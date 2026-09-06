# Phase 14 — Shared-heap concurrency and external processes

Status: **implemented (Python reference + Go + Rust).** This plan supersedes
the Phase 13 message-passing design (`Peer.worker`, `Channel<T>`, mailbox
protocol); the Phase 13 API was removed without migration shims. Built-in
type names follow `NAMING_CONSISTENCY_PLAN.md`.

Recorded validation (all three backends):

- `python3 tools/parity.py --reference-only` — pass.
- `./build.sh` (fresh Go/Rust binaries) — pass; full differential parity:
  366 PASS lines, "parity: all selected checks passed".
- Bounded repeated concurrency runs: `thread_self_join` 20× Rust / 15× Go,
  8-thread × 300-increment mutex stress 15× Python / 10× Rust / 8× Go — zero
  failures; unjoined-worker shutdown terminates cleanly on all backends.
- Go race-detector sweep (`go build -race`) over the concurrency fixtures —
  no data races.
- `./benchmark.sh --runs 2` — no shared-storage regression (Rust ~21× Python,
  Go ~0.54× Python).
- Removed-API rejection: `Channel<T>` is C110 statically; `channel()`,
  `Peer.worker`, and `process.run/capture/args` are undefined names at
  runtime (consistent with any unknown name).
- Docs migrated: `LANGUAGE.md`, `SEMANTICS.md` (concurrency section + E074-
  E079 diagnostics), `PARITY.md`, `README.md`, `PHASE_STATUS.md`, Sublime
  grammar; `PHASE_13_PLAN.md` marked historical/superseded; legacy Go
  `internal/native` + `internal/checker` process registrations removed.

## 1. Goal: a clean API for threads and processes

Make the common operations direct: start a function, join it, protect shared
state, or launch an application and use its streams. Threads and processes
share completion-method names, but do not need an artificial common mailbox
or worker framework. Keep configuration records small and defer optional
features until a concrete use case needs them.

Use shared lexical state and explicit synchronization for in-process work;
use argv and standard streams for external applications (including other
Solvik programs).

- `Thread` runs a function concurrently on a shared heap. Python and Rust use
  OS threads; Go uses goroutines scheduled onto OS threads. A dedicated OS
  thread per Go worker is not part of the language contract.
- `Mutex` synchronizes access to shared state. There is no `Channel<T>`, `Peer`,
  `Concurrent`, or lowercase `process` namespace in the new API.
- Starting work does not wait for its completion. Resource allocation and
  process launch can take time; this is not a wait-free or real-time guarantee.
  `join()`, `Mutex.lock()`, stream reads, and stream writes can block.
- Uncaught language errors in a thread body become exit code 1. Child exit
  codes are preserved; launch and I/O errors are separate cases (§2).
- Race freedom does not guarantee deterministic output: mutex acquisition
  order can vary. Parity fixtures must also make results independent of that
  order, or explicitly order their work.

### 1.1 Shared storage and existing value semantics

The sharing boundary is the closure's captured lexical storage, including
reachable globals and bound receivers. `Thread.start` must not clone the
closure environment or rebuild globals in a separate interpreter. Assignments
through a captured `mut` binding are visible to other closures and the creator
when synchronized. Captured environments outlive their originating calls.

This does **not** change ordinary struct/collection assignment, argument, or
return copy semantics. For example, `mut b: Counter = a` still creates a value
copy; capturing `a` directly shares its binding. A read that currently produces
a copy continues to do so. Mutating a captured aggregate must write back to
that same shared storage. Immutable bindings and field mutability rules remain
in force; starting a thread does not grant additional mutation permissions.

`Thread`, `Mutex`, `Process`, `InStream`, and `OutStream` are opaque identity
handles. Copies, including copies inside structs/collections, preserve handle
identity. Equality compares identity; display must use stable type labels,
never host addresses. Constructors for handles are limited to the API below;
`ThreadDef` and `ProcessDef` are built-in records with public fields.

### 1.2 Memory ordering and races

- Actions before a successful `Thread.start` happen before its body begins.
- Body completion happens before any successful `join()` return, and before
  a `status()` call returning an exit code or `is_done()` returning `true`.
- Unlocking a mutex publishes prior writes to a later successful acquisition
  of that same mutex. A null status or false completion poll publishes nothing.
- Concurrent accesses to the same mutable storage, with at least one write,
  require synchronization. Read/write races count, not just writer/writer races.
  Treat a whole collection binding as shared storage: distinct index writes
  are not promised independent because existing updates can copy/write back
  the collection. Lock those updates.
- Racy program results are unspecified, but runtimes must remain memory-safe:
  no Rust undefined behavior, Go concurrent-map crash, or corrupt interpreter
  state. Internal synchronization protects runtime representation; it does not
  make a user-level read/modify/write sequence atomic.
- Reading results after joining all writers is safe only if those writers did
  not race with each other. No fairness or deadlock freedom is promised.

## 2. Types and API surface

Signatures below use descriptive API notation; function-valued fields use
Solvik's existing `Func<...>` type syntax. `Void` means no returned value.

### 2.1 Thread

```text
Thread.start(def: ThreadDef) -> Thread
ThreadDef { body: Func<Int> }       // no parameters; returns int

t.join()    -> Int                 // waits for completion; never null
t.status()  -> Int?                // null until finished
t.is_done() -> Bool
```

The body may be an ordinary closure, compatible named function, or bound
method. Its returned integer is preserved as the status (no OS exit-code
truncation). An uncaught `throw` or language runtime error becomes 1 without
runtime-generated stdout/stderr or propagation to the joining caller. Explicit
worker printing is allowed but unordered. Host runtime bugs and fatal resource
exhaustion are not language errors that can reliably be contained.

Multiple callers may join, and repeated joins return the same cached result.
Self-join raises a catchable runtime error. Cyclic joins can deadlock. Failure
to create a worker raises a catchable error in `start`; no handle is returned.
A worker may finish before `start` returns, so initial status need not be null.

### 2.2 Mutex

```text
mutex()    -> Mutex
m.lock()   -> Void
m.unlock() -> Void
```

A mutex is non-reentrant and owned by a logical Solvik thread, including the
main execution context. Recursive locking, unlocking an unlocked mutex, and
unlocking from a different thread raise catchable errors consistently across
backends. Contention blocks the caller. Use `try/finally` to release locks on
return or error; body failure does not automatically release user-held locks.
`wait()`/`notify()` and lock poisoning are outside this phase.

### 2.3 Process

```text
Process.start(def: ProcessDef) -> Process
ProcessDef { program: String, args: List<String> }

p.stdin     -> OutStream           // read-only handle property
p.stdout    -> InStream            // read-only handle property
p.stderr    -> InStream            // read-only handle property
p.join()    -> Int                 // waits for child exit; never null
p.status()  -> Int?                // null until exit is observed
p.is_done() -> Bool
p.terminate() -> Void              // requests force-kill; use join to wait
```

- Both definition fields are required (`args: []` for no arguments). Snapshot
  them before launch. Execute argv directly, with no shell interpolation;
  shell syntax requires explicitly launching a shell. A bare program name
  uses PATH; paths are executed directly. Inherit the parent's cwd/environment.
- `start` returns after successful launch and pipe setup, without waiting for
  exit. Missing/non-executable programs and setup failures raise a catchable
  error in the caller. Clean up any partially launched child and pipes.
- Preserve normal child exit codes, including nonzero codes. On POSIX, signal
  termination maps to `128 + signal number`; use the native exit code on other
  platforms. A crash does not universally map to 1.
- Concurrent/repeated joins return the cached exit code and reap the child
  exactly once. Status polling does not consume the result.
- `terminate()` targets the direct child only. It is a no-op once exit has
  been observed; an exit racing with termination is also successful. Other
  termination failures raise catchable errors. It does not close buffered
  output or imply the child has already exited.

### 2.4 Process streams

```text
InStream.readLine() -> String?     // next line, or null at EOF
OutStream.write(text: String) -> Void
OutStream.close() -> Void          // child receives stdin EOF
```

- Encode writes as UTF-8 and write all bytes verbatim, with no added newline or
  user-visible flush requirement. Writes can block on pipe backpressure.
  Serialize concurrent writes as whole calls; their relative order is unspecified.
- `close()` is idempotent and serialized with writes. Writing after close or
  after the child closes its input raises a catchable I/O error.
- Separate background readers continuously drain stdout and stderr into
  independent, **unbounded** buffers. Ignoring stderr cannot fill its OS pipe
  and stall the child. This trades deadlock avoidance for potentially unbounded
  memory use; bounded buffering/spooling is deferred, not claimed here.
- Decode UTF-8 incrementally, replacing invalid sequences with U+FFFD. Strip LF
  and a CR immediately before LF; preserve other characters. At EOF, return
  any final unterminated line once, then null forever. Blank lines return `""`.
- Serialize concurrent reads from one stream; each line goes to one reader,
  with no guarantee about which reader receives it. There is no ordering
  guarantee between stdout and stderr.
- A pump error is stored and raised on reads after already buffered output is
  consumed; it is not silently converted to EOF. Stable error categories and
  messages must match across backends, without raw OS exception text.
- `join()` waits for the direct child's exit, **not** for output EOF or reader
  completion. Buffered output remains readable after joining. Descendants may
  inherit pipe writers and delay EOF even after the direct child exits.
  `join()` does not close stdin: callers must send EOF when the child needs it.
- Streams provide ordering for their own data, not a general memory barrier
  for unrelated shared variables. Binary APIs and public input-stream closing
  are deferred.

### 2.5 Program arguments

```text
args() -> List<String>
```

Return a fresh value copy of CLI arguments after the source file (excluding
interpreter flags, executable, and source path), matching existing
`process.args()` behavior. Workers see the same program arguments. A Solvik
child reads its own argv; it does not implicitly inherit the parent's argument
list. During migration, rename locals such as `args` in `bootstrap/main.sol`
if they shadow the new builtin.

### 2.6 Lifetime

Dropping a handle neither cancels nor joins work. Programs should explicitly
join threads and close child stdin when finished writing. Runtime shutdown
must have one documented policy across backends, rather than inheriting
Python daemon behavior, Go main-exit behavior, or Rust handle-drop behavior.

Proposed policy: after main returns, wait for outstanding threads, including
any workers they start; then close child stdin, terminate/reap remaining direct
children, and release process readers. Preserve main's result. An uncaught
main error uses the same cleanup. Deadlocked workers can prevent shutdown;
thread cancellation and timed joins are deferred.

## 3. Usage

These examples target the proposed API, which is not yet runnable. They use
existing Solvik syntax; no list repetition or list `push`/`pop` is introduced.

### 3.1 Fork-join with a shared result

```solvik
package main

struct Counter {
    pub mut total: Int
}

func main() -> Int {
    mut counter: Counter = Counter { total: 0 }
    lock: Mutex = mutex()
    add: Func<Int> = func() -> Int {
        mut i: Int = 0
        while i < 500 {
            lock.lock()
            try {
                counter.total = counter.total + 1
            } finally {
                lock.unlock()
            }
            i = i + 1
        }
        return 0
    }
    a: Thread = Thread.start(ThreadDef { body: add })
    b: Thread = Thread.start(ThreadDef { body: add })
    ac: Int = a.join()
    bc: Int = b.join()
    if ac != 0 || bc != 0 {
        return 1
    }
    println("total=" .. counter.total) // 1000
    return 0
}
```

### 3.2 User-built worker pool (drain-and-exit)

Use the existing stack collection for a prefilled LIFO worklist. No work is
added after workers start. The sum is independent of work assignment/order.

```solvik
package main

func main() -> Int {
    mut jobs: Stack<Int> = stack()
    jobs.push(1)
    jobs.push(2)
    jobs.push(3)
    mut total: Int = 0
    lock: Mutex = mutex()
    worker: Func<Int> = func() -> Int {
        while true {
            mut job: Int? = null
            lock.lock()
            try {
                if jobs.len() > 0 {
                    job = jobs.pop()
                }
            } finally {
                lock.unlock()
            }
            if job != null {
                result: Int = job * job
                lock.lock()
                try {
                    total = total + result
                } finally {
                    lock.unlock()
                }
            } else {
                return 0
            }
        }
        return 0
    }
    a: Thread = Thread.start(ThreadDef { body: worker })
    b: Thread = Thread.start(ThreadDef { body: worker })
    ac: Int = a.join()
    bc: Int = b.join()
    if ac != 0 || bc != 0 {
        return 1
    }
    println(total) // 14
    return 0
}
```

Spawn-per-job is also possible: keep handles in a `Stack<Thread>`, pop/join
all handles, and protect shared result updates with a mutex. Long-lived pools
that wait for future submissions require additional synchronization and are
deferred.

### 3.3 External process with stdio

```solvik
package main

func main() -> Int {
    p: Process = Process.start(ProcessDef {
        program: "/bin/sh",
        args: ["-c", "printf 'first\\nsecond\\n'"],
    })
    p.stdin.close()
    while true {
        line: String? = p.stdout.readLine()
        if line != null {
            println(line)
        } else {
            break
        }
    }
    return p.join()
}
```

The positive null check keeps the string use inside Solvik's supported
narrowing scope. This command is a POSIX fixture, not a portable command
availability promise.

## 4. Implementation sequence

1. Register the new records, handle types, namespace calls, methods, stream
   properties, and `args()` in all frontends and runtimes. Reuse existing type
   diagnostics; reserve consistent runtime errors for launch, self-join, mutex
   misuse, and I/O failures. Check misuse through `Any` as well.
2. Make captured storage safe to share while preserving ordinary value-copy
   boundaries. Keep VM stacks and type-inference state per worker. Implement
   thread completion, mutex ownership, failure containment, and shutdown.
3. Add process launch, independent stream pumps, cached exit status, and
   cleanup. Do not hold internal heap locks across user code or blocking calls.
4. Migrate/remove the old APIs and artifacts (§5), then pass the gates in §6.

Backend constraints:

- **Python:** remains the tree-walking semantic reference. Use
  `threading.Thread`, an ownership-checked lock wrapper, and `subprocess.Popen`
  with reader threads. The GIL is not the language's synchronization contract.
- **Go:** use goroutines, synchronized shared storage, an ownership-checked
  `sync.Mutex`, and `os/exec` with independent pumps. Keep each worker on its
  own bytecode VM state. Audit shared bindings, maps, caches, and random state
  with the race detector.
- **Rust:** the `Rc<RefCell<_>>` graph includes environments, callables,
  receivers, collections, builtins, and code references. All worker-reachable
  state needs suitable `Send`/`Sync` representation, not just captured objects.
  Use `Arc` and short-lived internal locks while preserving value copies.
  Remove the Phase 13 snapshot/rebuilt-environment approach; never disguise
  the Rc graph with unsafe `Send`/`Sync`. Workers run the production bytecode VM.

No global execution lock or native-backend tree-walking fallback.

## 5. Removal and migration inventory

No compatibility shims for `Channel<T>`, `channel()`, mailbox methods,
`Peer.worker`, or `process.run/capture/args`.

- Replace `test/reference/concurrency_peer_peer.sol`, `concurrency_fanout.sol`,
  `concurrency_workers.sol`, `valid/concurrency_channel_shape.sol`, and
  `runtime_errors/concurrency_send_closed.sol` with `thread_*`, `mutex_*`, and
  `process_*` coverage. Add rejection fixtures for removed APIs.
- Rewrite `example.sol` sections 11/17/18/19 **and their calls in main**. Keep
  the example deterministic and in the shared parity corpus.
- Migrate `test/native_args.sol`, `bootstrap/main.sol`,
  `test/reference/stdlib_data.sol`, and `test/reference/stdlib_files.sol`.
  Replace capture assertions with stream reads plus joins, preserving coverage
  of stdout, stderr, and nonzero status.
- Remove channel machinery in `solvik.py`, Go `internal/reference/channels.go`
  and related type/VM paths, and Rust's semantic channel subsystem.
- Audit legacy Go `internal/native/native.go`, its tests, and
  `internal/checker/checker.go`, which still register/test `process.*`, as well
  as production `internal/reference` and Rust registrations. Do not leave old
  exported names enabled in a retained pipeline.
- Update `LANGUAGE.md`, `SEMANTICS.md`, `PARITY.md`, `README.md`,
  `internal/reference/README.md`, and `PHASE_STATUS.md` when implemented.
  Mark `PHASE_13_PLAN.md` as historical/superseded, retaining its historical
  description. Audit editor grammar/completion files and diagnostics tables.
  Search the whole non-vendored repository for old names; distinguish history
  and rejection tests from active registrations and examples.

## 6. Validation and completion criteria

Compare exit code, stdout, and stderr across Python, Go, and Rust. Only main
prints in concurrency fixtures. Assert worker return codes; do not depend on
sleeps, initial pending status, lock acquisition order, or timing thresholds.

Required coverage:

- Shared scalar/aggregate captures, globals, bound methods, nested workers,
  closure lifetime, preserved assignment copies, and handle identity.
- Locked increments and the drain-and-exit pool; repeated/concurrent joins;
  completion polling; nonzero returns and contained errors; self-join and
  mutex misuse; release through `finally`.
- Direct argv handling; launch failure; stdin EOF; line framing, UTF-8,
  empty/final lines; output exceeding pipe capacity, unread stderr, and
  join-before-read; repeated close/EOF/join; broken pipes; nonzero exits and
  termination. Use backend tests for rare pump failures and signal details.
- Shutdown and dropped handles, including descendants retaining pipe writers.
  Supervise intentional hangs in host-side tests with timeouts and cleanup.
- Static signature/mutability errors, runtime checks through `Any`, removed-API
  rejection fixtures, and the migrated CLI argv behavior.

Declare and preflight POSIX command dependencies or use controlled helpers;
absolute paths and git history are not portable guarantees. Add per-case
execution timeouts and process-tree cleanup to parity supervision:
`tools/parity.py` currently runs subprocesses without a timeout. Include bounded
repeated concurrency runs and Go race-detector coverage of the new paths.

Completion gates:

- Python compilation and `python3 tools/parity.py --reference-only`.
- `./build.sh` with freshly built Go/Rust binaries and full differential parity.
- The lifecycle/stress checks above and `./benchmark.sh --runs 2` to assess
  shared-storage overhead.
- All old API registrations removed, examples/docs migrated, and actual check
  results recorded before marking Phase 14 implemented.

## 7. Deferred

- Mutex condition waiting/notification and long-lived blocking worker pools.
- Process cwd/env overrides, redirection, public input-stream closing, binary
  streams, bounded buffers/spooling, and process-group termination APIs.
- Thread cancellation, timed joins/locks, remote peers, and named discovery.
- Mailbox/channel primitives are deliberately absent from this design.
