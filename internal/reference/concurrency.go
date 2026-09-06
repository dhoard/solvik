package reference

// Shared-heap threads, mutexes, and external processes (Phase 14).
//
// A worker thread runs its body on a goroutine and shares the interpreter's
// heap with the creator: closures capture lexical bindings by reference, so
// synchronization of shared state is the program's job, via Mutex values.
// The interpreter serializes user-code execution on a single heap lock
// (Interpreter.heapMu); blocking natives (join, mutex lock/unlock, stream
// I/O, process control) run with the lock released so other workers can make
// progress. Starting a thread or process never waits for completion.
//
// Uncaught errors in a thread body become that thread's exit code (1) and are
// observed through join()/status(); they never propagate to the caller.

import (
	"bytes"
	"io"
	"os/exec"
	"runtime"
	"sync"
	"syscall"
)

// ---- goroutine-local inference state ----------------------------------------

// goroutineID returns a process-unique id for the calling goroutine, used to
// key per-goroutine state. It mirrors Python's threading.get_ident().
func goroutineID() uint64 {
	var buf [64]byte
	n := runtime.Stack(buf[:], false)
	b := buf[:n]
	// runtime.Stack's first line is "goroutine 123 [running]:...". The id is
	// the digit run after the space that follows "goroutine". Slice on the
	// first space and re-trim is wrong (the first space is adjacent to
	// "goroutine", yielding an empty token); scan digits directly.
	i := 0
	for i < len(b) && b[i] != ' ' {
		i++
	}
	for i < len(b) && (b[i] == ' ' || b[i] == '\t') {
		i++
	}
	start := i
	var id uint64
	for i < len(b) && b[i] >= '0' && b[i] <= '9' {
		id = id*10 + uint64(b[i]-'0')
		i++
	}
	if i == start {
		return 0
	}
	return id
}

func (in *Interpreter) glBindings() *[]map[string]TypeRef {
	id := goroutineID()
	in.glMu.Lock()
	defer in.glMu.Unlock()
	if in.glBind == nil {
		in.glBind = map[uint64]*[]map[string]TypeRef{}
	}
	if s := in.glBind[id]; s == nil {
		empty := make([]map[string]TypeRef, 0, 1)
		in.glBind[id] = &empty
	}
	return in.glBind[id]
}

func (in *Interpreter) glReturn() *[]TypeRef {
	id := goroutineID()
	in.glMu.Lock()
	defer in.glMu.Unlock()
	if in.glReturns == nil {
		in.glReturns = map[uint64]*[]TypeRef{}
	}
	if s := in.glReturns[id]; s == nil {
		empty := make([]TypeRef, 0, 1)
		in.glReturns[id] = &empty
	}
	return in.glReturns[id]
}

// ---- thread handle -----------------------------------------------------------

// threadValue is an opaque identity handle for a worker running on the shared
// heap. Copies preserve identity; equality is identity.
type threadValue struct {
	body     any
	in       *Interpreter
	mu       sync.Mutex
	id       uint64 // goroutine id of the worker (0 until it starts running)
	done     chan struct{}
	finished bool
	exitCode int
}

func (t *threadValue) start() {
	t.done = make(chan struct{})
	go func() {
		defer close(t.done)
		t.mu.Lock()
		t.id = goroutineID()
		t.mu.Unlock()
		code := 0
		vm := &bcVM{in: t.in, funcs: t.in.compiled, holdingHeap: true}
		t.in.heapMu.Lock()
		func() {
			defer t.in.heapMu.Unlock()
			defer func() {
				if recover() != nil {
					// An uncaught language error becomes exit code 1; nothing
					// propagates to the joining caller and nothing is printed.
					code = 1
				}
			}()
			result, err := vm.call(t.body, []any{}, false, nil, nil)
			if err != nil {
				code = 1
			} else if n, ok := result.(int64); ok {
				code = int(n)
			} else if n, ok := result.(float64); ok {
				code = int(n)
			}
		}()
		t.mu.Lock()
		t.exitCode = code
		t.finished = true
		t.mu.Unlock()
	}()
}

func (t *threadValue) join() any {
	t.mu.Lock()
	id := t.id
	t.mu.Unlock()
	if id != 0 && id == goroutineID() {
		panic(runtimeErrCode("E074", "thread cannot join itself"))
	}
	<-t.done
	t.mu.Lock()
	defer t.mu.Unlock()
	return int64(t.exitCode)
}

func (t *threadValue) status() any {
	t.mu.Lock()
	defer t.mu.Unlock()
	if !t.finished {
		return nil
	}
	return int64(t.exitCode)
}

func (t *threadValue) isDone() bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.finished
}

// ---- mutex -------------------------------------------------------------------

// mutexValue is a non-reentrant mutex owned by a logical Solvik thread.
// Ownership is tracked by goroutine id; all misuse is a catchable E075.
type mutexValue struct {
	base  sync.Mutex
	meta  sync.Mutex
	owner uint64
}

func newMutexValue() *mutexValue { return &mutexValue{} }

func (m *mutexValue) lock() {
	tid := goroutineID()
	m.meta.Lock()
	if m.owner == tid {
		m.meta.Unlock()
		panic(runtimeErrCode("E075", "recursive lock of mutex"))
	}
	m.meta.Unlock()
	// Never block on base while holding meta: the current owner must be able
	// to run unlock() (which takes meta) while we wait.
	m.base.Lock()
	m.meta.Lock()
	m.owner = tid
	m.meta.Unlock()
}

func (m *mutexValue) unlock() {
	tid := goroutineID()
	m.meta.Lock()
	if m.owner == 0 {
		m.meta.Unlock()
		panic(runtimeErrCode("E075", "unlock of unlocked mutex"))
	}
	if m.owner != tid {
		m.meta.Unlock()
		panic(runtimeErrCode("E075", "mutex unlock from a different thread"))
	}
	m.owner = 0
	m.meta.Unlock()
	m.base.Unlock()
}

// ---- semaphore -----------------------------------------------------------------

// semaphoreValue is a POSIX-style counting semaphore (Phase 15). acquire()
// blocks until the counter is positive, then decrements it; release()
// increments it without bound from any goroutine. There is no ownership
// tracking. A negative initial count is a catchable E080.
type semaphoreValue struct {
	mu    sync.Mutex
	cond  *sync.Cond
	count int
}

func newSemaphoreValue(count int) *semaphoreValue {
	if count < 0 {
		panic(runtimeErrCode("E080", "semaphore count must be non-negative"))
	}
	s := &semaphoreValue{count: count}
	s.cond = sync.NewCond(&s.mu)
	return s
}

func (s *semaphoreValue) acquire() {
	s.mu.Lock()
	for s.count == 0 {
		s.cond.Wait()
	}
	s.count--
	s.mu.Unlock()
}

func (s *semaphoreValue) release() {
	s.mu.Lock()
	s.count++
	s.cond.Signal()
	s.mu.Unlock()
}

// ---- process streams ----------------------------------------------------------

// streamBuffer is an unbounded byte buffer fed by a process output pump
// goroutine. readLine blocks until a line is available or EOF is reached.
type streamBuffer struct {
	mu     sync.Mutex
	cond   *sync.Cond
	buf    []byte
	eof    bool
	errMsg string
}

func newStreamBuffer() *streamBuffer {
	b := &streamBuffer{}
	b.cond = sync.NewCond(&b.mu)
	return b
}

func (b *streamBuffer) append(data []byte) {
	b.mu.Lock()
	b.buf = append(b.buf, data...)
	b.cond.Broadcast()
	b.mu.Unlock()
}

func (b *streamBuffer) finish(err error) {
	b.mu.Lock()
	b.eof = true
	if err != nil && err != io.EOF {
		b.errMsg = err.Error()
	}
	b.cond.Broadcast()
	b.mu.Unlock()
}

// readLine returns the next line, or nil at EOF. It strips LF and a CR
// immediately before it; a final unterminated line is emitted once at EOF.
func (b *streamBuffer) readLine() any {
	b.mu.Lock()
	defer b.mu.Unlock()
	for {
		pos := bytes.IndexByte(b.buf, '\n')
		if pos >= 0 {
			line := b.buf[:pos]
			b.buf = append([]byte{}, b.buf[pos+1:]...)
			if n := len(line); n > 0 && line[n-1] == '\r' {
				line = line[:n-1]
			}
			return string(line)
		}
		if len(b.buf) > 0 && b.eof {
			line := b.buf
			b.buf = nil
			return string(line)
		}
		if len(b.buf) == 0 && b.errMsg != "" {
			panic(runtimeErrCode("E078", "process output read failed"))
		}
		if b.eof {
			return nil
		}
		b.cond.Wait()
	}
}

// inStreamValue is a read-only view of one child output stream.
type inStreamValue struct {
	buf *streamBuffer
}

func (s *inStreamValue) readLine() any { return s.buf.readLine() }

// outStreamValue is the writable child stdin handle.
type outStreamValue struct {
	p      *processValue
	mu     sync.Mutex
	closed bool
}

func (o *outStreamValue) write(text string) {
	o.mu.Lock()
	if o.closed {
		o.mu.Unlock()
		panic(runtimeErrCode("E077", "write to closed process stdin"))
	}
	o.mu.Unlock()
	if _, err := o.p.stdin.Write([]byte(text)); err != nil {
		panic(runtimeErrCode("E077", "process stdin write failed"))
	}
}

func (o *outStreamValue) close() {
	o.mu.Lock()
	if o.closed {
		o.mu.Unlock()
		return
	}
	o.closed = true
	o.mu.Unlock()
	o.p.stdin.Close()
}

// ---- process handle ------------------------------------------------------------

// processValue is an opaque identity handle for a direct child process.
// stdout/stderr are pumped into buffers by background goroutines, so ignoring
// one stream never stalls the child; join() waits for exit only, and buffered
// output stays readable afterwards.
type processValue struct {
	program  string
	args     []string
	cmd      *exec.Cmd
	stdin    io.WriteCloser
	stdout   *inStreamValue
	stderr   *inStreamValue
	stdinOut *outStreamValue
	mu       sync.Mutex
	done     chan struct{}
	finished bool
	exitCode int
}

func startProcess(program string, args []string, rt *concurrencyRuntime) *processValue {
	cmd := exec.Command(program, args...)
	stdin, err := cmd.StdinPipe()
	if err != nil {
		panic(runtimeErrCode("E076", "cannot launch process '%s'", program))
	}
	stdoutPipe, err := cmd.StdoutPipe()
	if err != nil {
		panic(runtimeErrCode("E076", "cannot launch process '%s'", program))
	}
	stderrPipe, err := cmd.StderrPipe()
	if err != nil {
		panic(runtimeErrCode("E076", "cannot launch process '%s'", program))
	}
	p := &processValue{
		program: program,
		args:    args,
		cmd:     cmd,
		stdin:   stdin,
		stdout:  &inStreamValue{buf: newStreamBuffer()},
		stderr:  &inStreamValue{buf: newStreamBuffer()},
	}
	p.stdinOut = &outStreamValue{p: p}
	p.done = make(chan struct{})
	if err := cmd.Start(); err != nil {
		panic(runtimeErrCode("E076", "cannot launch process '%s'", program))
	}
	go func() {
		defer close(p.done)
		// Pump both pipes concurrently (a child may fill one pipe before
		// ever writing the other), then reap the child once both reached
		// EOF -- Wait would close the pipes and lose unread output otherwise.
		var wg sync.WaitGroup
		wg.Add(2)
		go func() { defer wg.Done(); p.pump(stdoutPipe, p.stdout.buf) }()
		go func() { defer wg.Done(); p.pump(stderrPipe, p.stderr.buf) }()
		wg.Wait()
		err := cmd.Wait()
		code := 0
		if err != nil {
			if ee, ok := err.(*exec.ExitError); ok {
				code = ee.ExitCode()
				if code < 0 {
					// Killed by a signal: POSIX maps this to 128 + signum.
					if ws, ok2 := ee.Sys().(syscall.WaitStatus); ok2 && ws.Signaled() {
						code = 128 + int(ws.Signal())
					}
				}
			} else {
				code = 1
			}
		}
		p.mu.Lock()
		p.exitCode = code
		p.finished = true
		p.mu.Unlock()
	}()
	rt.registerProcess(p)
	return p
}

// pump copies pipe into buf until EOF, then finishes the buffer.
func (p *processValue) pump(r interface{ Read([]byte) (int, error) }, buf *streamBuffer) {
	tmp := make([]byte, 32*1024)
	for {
		n, err := r.Read(tmp)
		if n > 0 {
			buf.append(tmp[:n])
		}
		if err != nil {
			buf.finish(err)
			return
		}
	}
}

func (p *processValue) join() any {
	<-p.done
	p.mu.Lock()
	defer p.mu.Unlock()
	return int64(p.exitCode)
}

func (p *processValue) status() any {
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.finished {
		return nil
	}
	return int64(p.exitCode)
}

func (p *processValue) isDone() bool {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.finished
}

// terminate force-kills the direct child (SIGKILL on POSIX). It is a no-op
// once exit has been observed.
func (p *processValue) terminate() {
	p.mu.Lock()
	wasFinished := p.finished
	p.mu.Unlock()
	if wasFinished {
		return
	}
	if err := p.cmd.Process.Kill(); err != nil {
		panic(runtimeErrCode("E079", "process termination failed"))
	}
}

// ---- record values -------------------------------------------------------------

// threadDefValue is the built-in record `ThreadDef { body: Func<Int> }`.
type threadDefValue struct {
	body any
}

// processDefValue is the built-in record
// `ProcessDef { program: String, args: List<String> }`.
type processDefValue struct {
	program string
	args    []string
}

// ---- concurrency runtime --------------------------------------------------------

// concurrencyRuntime owns live threads and child processes and implements the
// shutdown policy: after main returns (or fails), wait for outstanding threads
// (including workers they start), then close child stdin, terminate/reap
// remaining direct children, and release process readers.
type concurrencyRuntime struct {
	mu        sync.Mutex
	threads   []*threadValue
	processes []*processValue
}

func newConcurrencyRuntime() *concurrencyRuntime { return &concurrencyRuntime{} }

func (rt *concurrencyRuntime) registerThread(t *threadValue) {
	rt.mu.Lock()
	rt.threads = append(rt.threads, t)
	rt.mu.Unlock()
}

func (rt *concurrencyRuntime) registerProcess(p *processValue) {
	rt.mu.Lock()
	rt.processes = append(rt.processes, p)
	rt.mu.Unlock()
}

func (rt *concurrencyRuntime) shutdown() {
	for {
		rt.mu.Lock()
		var live []*threadValue
		for _, t := range rt.threads {
			if !t.isDone() {
				live = append(live, t)
			}
		}
		rt.mu.Unlock()
		if len(live) == 0 {
			break
		}
		for _, t := range live {
			t.join()
		}
	}
	rt.mu.Lock()
	procs := append([]*processValue(nil), rt.processes...)
	rt.mu.Unlock()
	for _, p := range procs {
		p.stdinOut.close()
		if !p.isDone() {
			func() {
				defer func() { recover() }()
				p.terminate()
			}()
		}
		p.join()
	}
}
