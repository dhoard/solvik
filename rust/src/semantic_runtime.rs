//! Native tree-walking runtime for the semantic AST.

use crate::semantic_ast::{Block, Decl, Expr, Function, Stmt};
use crate::semantic_types::TypeRef;
use std::cell::RefCell;
use std::collections::HashMap;
use std::fmt;
use std::panic::{self, AssertUnwindSafe};

use std::sync::{Arc, Condvar, Mutex, MutexGuard};
use std::thread;
use base64::Engine;
use md5::Digest as Md5Digest;
use sha1::Sha1;
use sha2::{Sha256, Sha512};
use chrono::{DateTime, SecondsFormat, Utc};
use serde_json::Value as JsonValue;

#[derive(Clone)]
enum Value {
    Null,
    Bool(bool),
    Byte(u8),
    Int(i64),
    Float(f64),
    Char(char),
    String(String),
    Regex(String),
    List(Arc<RefCell<Vec<Value>>>),
    Stack(Arc<RefCell<Vec<Value>>>),
    Map(Arc<RefCell<Vec<(Value, Value)>>>),
    Struct(Arc<RefCell<StructValue>>),
    StructType(Arc<StructDef>),
    Enum(Arc<EnumValue>),
    EnumType(Arc<EnumDef>),
    Namespace(Arc<NamespaceValue>),
    Module(Arc<ModuleValue>),
    Exception(RuntimeError),
    Function(Arc<Callable>),
    Builtin(Arc<dyn Fn(Vec<Value>) -> Result<Value, RuntimeError>>),
    Thread(Arc<ThreadState>),
    Mutex(Arc<MutexState>),
    Semaphore(Arc<SemaphoreState>),
    Process(Arc<ProcessState>),
    InStream(Arc<StreamBuffer>),
    OutStream(Arc<ProcessState>),
    ThreadDef(Box<Value>),
    ProcessDef(String, Vec<String>),
}

struct Callable { function: Function, closure: EnvRef, receiver: Option<Value>, bytecode: Option<Arc<BcCode>> }
#[derive(Clone)]
struct StructDef {
    name: String,
    owner_package: String,
    package_env: EnvRef,
    methods: HashMap<String, Function>,
    method_public: HashMap<String, bool>,
    field_public: HashMap<String, bool>,
    type_param_count: usize,
}
struct StructValue { definition: Arc<StructDef>, fields: HashMap<String, Value>, unresolved: bool }
#[derive(Clone)]
struct EnumDef { name: String, members: HashMap<String, EnumMemberDef>, type_param_count: usize }
#[derive(Clone)]
struct EnumMemberDef { payload_count: usize, value: Option<i64> }
#[derive(Clone)]
struct EnumValue { definition: Arc<EnumDef>, member: String, payload: Vec<Value>, unresolved: bool }
struct NamespaceValue { env: EnvRef }
struct ModuleValue { env: EnvRef, callable: Option<Arc<dyn Fn(Vec<Value>) -> Result<Value, RuntimeError>>> }

#[derive(Clone, Debug)]
pub struct RuntimeError { pub code: String, pub message: String }

impl RuntimeError {
    fn new(message: impl Into<String>) -> Self { Self { code: String::new(), message: message.into() } }
    fn coded(code: &str, message: impl Into<String>) -> Self { Self { code: code.into(), message: message.into() } }
}

impl fmt::Display for Value {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Value::Null => write!(f, "null"), Value::Bool(v) => write!(f, "{}", v), Value::Byte(v) => write!(f, "{}", v), Value::Int(v) => write!(f, "{}", v),
            Value::Float(v) => write!(f, "{}", v), Value::Char(v) => write!(f, "{}", v), Value::String(v) => write!(f, "{}", v),
            Value::Regex(v) => write!(f, "regex({})", v),
            Value::List(v) => { write!(f, "[")?; for (i, item) in v.borrow().iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}", item)?; } write!(f, "]") },
            Value::Map(v) => { write!(f, "{{")?; for (i, (key, value)) in v.borrow().iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}: {}", key, value)?; } write!(f, "}}") },
            Value::Stack(v) => write!(f, "stack({})", v.borrow().len()),
            Value::Struct(value) => write!(f, "{} {{...}}", value.borrow().definition.name),
            Value::StructType(value) => write!(f, "<{}>", value.name),
            Value::Enum(value) => { write!(f, "{}", value.member)?; if !value.payload.is_empty() { write!(f, "(")?; for (i, item) in value.payload.iter().enumerate() { if i > 0 { write!(f, ", ")?; } write!(f, "{}", item)?; } write!(f, ")")?; } Ok(()) },
            Value::EnumType(value) => write!(f, "<{}>", value.name),
            Value::Namespace(_) => write!(f, "<namespace>"),
            Value::Module(_) => write!(f, "<module>"),
            Value::Exception(value) => write!(f, "{}", value.message),
            Value::Function(_) | Value::Builtin(_) => write!(f, "<function>"),
            Value::Thread(_) => write!(f, "<thread>"),
            Value::Mutex(_) => write!(f, "<mutex>"),
            Value::Semaphore(_) => write!(f, "<semaphore>"),
            Value::Process(_) => write!(f, "<process>"),
            Value::InStream(_) => write!(f, "<instream>"),
            Value::OutStream(_) => write!(f, "<outstream>"),
            Value::ThreadDef(_) => write!(f, "<threaddef>"),
            Value::ProcessDef(program, _) => write!(f, "<processdef {}>", program),
        }
    }
}

impl PartialEq for Value {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Value::Null, Value::Null) => true, (Value::Bool(a), Value::Bool(b)) => a == b,
            (Value::Byte(a), Value::Byte(b)) => a == b, (Value::Byte(a), Value::Int(b)) | (Value::Int(b), Value::Byte(a)) => (*a as i64) == *b,
            (Value::Int(a), Value::Int(b)) => a == b, (Value::Float(a), Value::Float(b)) => a == b,
            (Value::Int(a), Value::Float(b)) | (Value::Float(b), Value::Int(a)) => (*a as f64) == *b,
            (Value::Char(a), Value::Char(b)) => a == b, (Value::String(a), Value::String(b)) => a == b,
            (Value::Regex(a), Value::Regex(b)) => a == b,
            (Value::List(a), Value::List(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Map(a), Value::Map(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Stack(a), Value::Stack(b)) => a.borrow().as_slice() == b.borrow().as_slice(),
            (Value::Struct(a), Value::Struct(b)) => {
                let a = a.borrow(); let b = b.borrow();
                a.definition.name == b.definition.name && a.fields == b.fields
            }
            (Value::Enum(a), Value::Enum(b)) => a.definition.name == b.definition.name && a.member == b.member && a.payload == b.payload,
            (Value::Exception(a), Value::Exception(b)) => a.code == b.code && a.message == b.message,
            (Value::Function(a), Value::Function(b)) => Arc::ptr_eq(a, b),
            (Value::Thread(a), Value::Thread(b)) => Arc::ptr_eq(a, b),
            (Value::Mutex(a), Value::Mutex(b)) => Arc::ptr_eq(a, b),
            (Value::Semaphore(a), Value::Semaphore(b)) => Arc::ptr_eq(a, b),
            (Value::Process(a), Value::Process(b)) => Arc::ptr_eq(a, b),
            (Value::InStream(a), Value::InStream(b)) => Arc::ptr_eq(a, b),
            (Value::OutStream(a), Value::OutStream(b)) => Arc::ptr_eq(a, b),
            (Value::ThreadDef(a), Value::ThreadDef(b)) => a == b,
            (Value::ProcessDef(a, x), Value::ProcessDef(b, y)) => a == b && x == y,
            _ => false,
        }
    }
}

type EnvRef = Arc<RefCell<Env>>;
struct Env { parent: Option<EnvRef>, values: HashMap<String, Value>, package: String }

impl Env {
    fn new(parent: Option<EnvRef>) -> EnvRef {
        let package = parent.as_ref().map(|parent| parent.borrow().package.clone()).unwrap_or_default();
        Arc::new(RefCell::new(Self { parent, values: HashMap::new(), package }))
    }
    fn package(parent: Option<EnvRef>, package: impl Into<String>) -> EnvRef {
        Arc::new(RefCell::new(Self { parent, values: HashMap::new(), package: package.into() }))
    }
    fn define(env: &EnvRef, name: String, value: Value) { env.borrow_mut().values.insert(name, value); }
    fn get(env: &EnvRef, name: &str) -> Option<Value> {
        if let Some(value) = env.borrow().values.get(name) { return Some(value.clone()); }
        env.borrow().parent.as_ref().and_then(|parent| Self::get(parent, name))
    }
    fn set(env: &EnvRef, name: &str, value: Value) -> bool {
        if env.borrow().values.contains_key(name) { env.borrow_mut().values.insert(name.into(), value); true }
        else if let Some(parent) = env.borrow().parent.clone() { Self::set(&parent, name, value) } else { false }
    }
}

// ===========================================================================
// Phase 14: shared-heap threads, mutexes, and external processes.
//
// A worker thread runs its body on an OS thread and shares the interpreter's
// heap with the creator: closures capture lexical bindings by reference, so
// synchronization of shared state is the program's job, via Mutex values.
// The interpreter serializes user-code execution on a single heap lock
// (HEAP_LOCK); blocking natives (join, mutex lock/unlock, stream I/O, process
// control) run with the lock released so other workers can make progress.
// Starting a thread or process never waits for completion.
//
// SAFETY of the unsafe Send/Sync impls below: every Value/Env is only ever
// touched while holding HEAP_LOCK (user code and the natives that manipulate
// values), or by pump threads that touch only StreamBuffer byte state. The
// RefCell interior mutability therefore never observes concurrent access.
// ===========================================================================

use std::sync::atomic::{AtomicBool, Ordering};
use std::thread::ThreadId;

static HEAP_LOCK: Mutex<()> = Mutex::new(());

thread_local! {
    static HEAP_DEPTH: std::cell::Cell<usize> = std::cell::Cell::new(0);
    static HEAP_GUARD: std::cell::RefCell<Option<MutexGuard<'static, ()>>> = std::cell::RefCell::new(None);
}

unsafe impl Send for Value {}
unsafe impl Sync for Value {}
unsafe impl Send for Env {}
unsafe impl Sync for Env {}

fn heap_guard() -> MutexGuard<'static, ()> {
    HEAP_LOCK.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Acquires the heap lock for the duration of a top-level thread (main or a
/// worker body). The guard is parked thread-locally so native calls can
/// temporarily release it.
fn heap_thread_start() {
    let guard = heap_guard();
    HEAP_DEPTH.with(|depth| depth.set(depth.get() + 1));
    HEAP_GUARD.with(|slot| *slot.borrow_mut() = Some(guard));
}

/// Releases the parked heap guard (thread exit).
fn heap_thread_end() {
    HEAP_DEPTH.with(|depth| depth.set(0));
    HEAP_GUARD.with(|slot| *slot.borrow_mut() = None);
}

/// Temporarily releases the heap lock around a native call; re-acquires it on
/// drop. Natives are leaf work (no user code), so releasing lets blocking
/// natives (join, readLine, sleep, process control) yield to other workers.
struct NativeCallGuard { reacquire: bool }

impl Drop for NativeCallGuard {
    fn drop(&mut self) {
        if self.reacquire {
            let guard = heap_guard();
            HEAP_DEPTH.with(|depth| depth.set(depth.get() + 1));
            HEAP_GUARD.with(|slot| *slot.borrow_mut() = Some(guard));
        }
    }
}

fn begin_native_call() -> NativeCallGuard {
    let held = HEAP_DEPTH.with(|depth| depth.get()) > 0;
    if held {
        HEAP_DEPTH.with(|depth| depth.set(depth.get() - 1));
        HEAP_GUARD.with(|slot| {
            // Taking the guard out of the slot drops it, releasing the lock.
            let _guard = slot.borrow_mut().take();
        });
    }
    NativeCallGuard { reacquire: held }
}

/// Re-acquires the heap lock when a builtin invokes user code (closures in
/// list.map, sort comparators, ...): user code always runs under the lock.
struct UserCallGuard { acquired: bool }

impl Drop for UserCallGuard {
    fn drop(&mut self) {
        if self.acquired {
            HEAP_DEPTH.with(|depth| depth.set(0));
            HEAP_GUARD.with(|slot| *slot.borrow_mut() = None);
        }
    }
}

fn begin_user_call() -> UserCallGuard {
    if HEAP_DEPTH.with(|depth| depth.get()) == 0 {
        let guard = heap_guard();
        HEAP_DEPTH.with(|depth| depth.set(1));
        HEAP_GUARD.with(|slot| *slot.borrow_mut() = Some(guard));
        return UserCallGuard { acquired: true };
    }
    UserCallGuard { acquired: false }
}

// Base-lock guards for user-level Mutex values are parked thread-locally
// between lock() and unlock() (both run on the owning thread, with the heap
// lock released). Keyed by the MutexState address, which is stable because
// the state lives in an Arc.
// ---- thread handle -----------------------------------------------------------

#[derive(Default)]
struct ThreadInner {
    tid: Option<ThreadId>,
    finished: bool,
    exit_code: i64,
    started: bool,
}

struct ThreadState {
    inner: Mutex<ThreadInner>,
    cond: Condvar,
    body: Value,
    registry: Arc<ConcurrencyRegistry>,
}

impl ThreadState {
    fn new(body: Value, registry: Arc<ConcurrencyRegistry>) -> Arc<ThreadState> {
        Arc::new(ThreadState { inner: Mutex::new(ThreadInner::default()), cond: Condvar::new(), body, registry })
    }

    fn require_started(&self) -> Result<(), RuntimeError> {
        if self.inner.lock().unwrap().started { Ok(()) } else { Err(RuntimeError::coded("E081", "operation on unstarted thread")) }
    }

    fn join(&self) -> Result<i64, RuntimeError> {
        self.require_started()?;
        let current = thread::current().id();
        {
            let inner = self.inner.lock().unwrap();
            if inner.tid.as_ref() == Some(&current) {
                return Err(RuntimeError::coded("E074", "thread cannot join itself"));
            }
        }
        let mut inner = self.inner.lock().unwrap();
        while !inner.finished {
            inner = self.cond.wait(inner).unwrap();
        }
        Ok(inner.exit_code)
    }

    fn status(&self) -> Option<i64> {
        let inner = self.inner.lock().unwrap();
        if inner.finished { Some(inner.exit_code) } else { None }
    }

    fn is_done(&self) -> bool {
        self.inner.lock().unwrap().finished
    }
}

// Launches the worker exactly once. Called by the Solvik-level .start() method;
// Thread.new only constructs an unstarted handle.
fn thread_start(state: &Arc<ThreadState>) -> Result<(), RuntimeError> {
    {
        let mut inner = state.inner.lock().unwrap();
        if inner.started {
            return Err(RuntimeError::coded("E081", "thread already started"));
        }
        inner.started = true;
    }
    state.registry.register_thread(state.clone());
    let body = state.body.clone();
    let worker_state = state.clone();
    thread::spawn(move || {
        heap_thread_start();
        worker_state.inner.lock().unwrap().tid = Some(thread::current().id());
        let outcome = panic::catch_unwind(AssertUnwindSafe(|| call(body, Vec::new())));
        let exit = match outcome {
            Ok(Ok(Value::Int(n))) => n,
            Ok(Ok(_)) => 0,
            Ok(Err(_)) => 1,
            Err(_) => 1,
        };
        // An uncaught language error becomes exit code 1; nothing propagates
        // to the joining caller and nothing is printed by the runtime.
        let mut inner = worker_state.inner.lock().unwrap();
        inner.finished = true;
        inner.exit_code = exit;
        drop(inner);
        worker_state.cond.notify_all();
        heap_thread_end();
    });
    Ok(())
}

fn new_thread(body: Value, registry: &Arc<ConcurrencyRegistry>) -> Value {
    Value::Thread(ThreadState::new(body, registry.clone()))
}

// ---- mutex ---------------------------------------------------------------------

// Base lock for user-level Mutex values. A tiny spinlock: contention is low
// because user code is serialized by the heap lock, and only blocking natives
// (which run with the heap lock released) ever contend here. A spinlock also
// avoids parking a std MutexGuard across lock()/unlock() calls, which the
// borrow checker forbids (the guard would outlive its &self).
struct BaseLock { flag: AtomicBool }

impl BaseLock {
    fn new() -> Self { BaseLock { flag: AtomicBool::new(false) } }

    fn acquire(&self) {
        while self.flag.compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst).is_err() {
            std::hint::spin_loop();
        }
    }

    fn release(&self) {
        self.flag.store(false, Ordering::SeqCst);
    }
}

impl Default for BaseLock {
    fn default() -> Self { Self::new() }
}

struct MutexMeta { owner: Option<ThreadId> }

struct MutexState {
    base: BaseLock,
    meta: Mutex<MutexMeta>,
}

impl MutexState {
    fn new() -> Arc<MutexState> {
        Arc::new(MutexState { base: BaseLock::new(), meta: Mutex::new(MutexMeta { owner: None }) })
    }

    fn lock(&self) -> Result<(), RuntimeError> {
        let current = thread::current().id();
        {
            let meta = self.meta.lock().unwrap();
            if meta.owner.as_ref() == Some(&current) {
                return Err(RuntimeError::coded("E075", "recursive lock of mutex"));
            }
        }
        // Never block on base while holding meta: the current owner must be
        // able to run unlock() (which takes meta) while we wait.
        self.base.acquire();
        self.meta.lock().unwrap().owner = Some(current);
        Ok(())
    }

    fn unlock(&self) -> Result<(), RuntimeError> {
        let current = thread::current().id();
        {
            let mut meta = self.meta.lock().unwrap();
            if meta.owner.is_none() {
                return Err(RuntimeError::coded("E075", "unlock of unlocked mutex"));
            }
            if meta.owner.as_ref() != Some(&current) {
                return Err(RuntimeError::coded("E075", "mutex unlock from a different thread"));
            }
            meta.owner = None;
        }
        self.base.release();
        Ok(())
    }
}


// ---- semaphore -----------------------------------------------------------------

// POSIX-style counting semaphore (Phase 15). acquire() blocks until the
// counter is positive, then decrements it; release() increments it without
// bound from any thread. No ownership tracking. Negative initial count is E080.
struct SemaphoreState {
    mu: Mutex<i64>,
    cond: Condvar,
}

impl SemaphoreState {
    fn new(count: i64) -> Result<Arc<SemaphoreState>, RuntimeError> {
        if count < 0 {
            return Err(RuntimeError::coded("E080", "semaphore count must be non-negative"));
        }
        Ok(Arc::new(SemaphoreState { mu: Mutex::new(count), cond: Condvar::new() }))
    }

    fn acquire(&self) -> Result<(), RuntimeError> {
        let mut count = self.cond.wait_while(self.mu.lock().unwrap(), |c| *c == 0).unwrap();
        *count -= 1;
        Ok(())
    }

    fn release(&self) -> Result<(), RuntimeError> {
        let mut count = self.mu.lock().unwrap();
        *count += 1;
        self.cond.notify_one();
        Ok(())
    }
}

// ---- process streams -----------------------------------------------------------

/// Unbounded byte buffer fed by a process output pump thread. read_line
/// blocks until a line is available or EOF is reached.
struct StreamBuffer {
    mu: Mutex<Vec<u8>>,
    cond: Condvar,
    eof: AtomicBool,
    error: Mutex<Option<String>>,
}

impl StreamBuffer {
    fn new() -> Arc<StreamBuffer> {
        Arc::new(StreamBuffer { mu: Mutex::new(Vec::new()), cond: Condvar::new(), eof: AtomicBool::new(false), error: Mutex::new(None) })
    }

    fn append(&self, data: &[u8]) {
        let mut buf = self.mu.lock().unwrap();
        buf.extend_from_slice(data);
        self.cond.notify_all();
    }

    fn finish(&self, error: Option<String>) {
        if let Some(message) = error {
            *self.error.lock().unwrap() = Some(message);
        }
        self.eof.store(true, Ordering::SeqCst);
        self.cond.notify_all();
    }

    /// Next line, or None at EOF. Strips LF and a CR immediately before it;
    /// a final unterminated line is emitted once at EOF.
    fn read_line(&self) -> Result<Option<String>, RuntimeError> {
        let mut buf = self.mu.lock().unwrap();
        loop {
            if let Some(pos) = buf.iter().position(|byte| *byte == b'\n') {
                let line = buf.drain(..=pos).collect::<Vec<u8>>();
                let mut line = &line[..line.len() - 1];
                if line.last() == Some(&b'\r') {
                    line = &line[..line.len() - 1];
                }
                self.cond.notify_all();
                return Ok(Some(String::from_utf8_lossy(line).into_owned()));
            }
            if self.eof.load(Ordering::SeqCst) {
                if !buf.is_empty() {
                    let line = std::mem::take(&mut *buf);
                    return Ok(Some(String::from_utf8_lossy(&line).into_owned()));
                }
                if self.error.lock().unwrap().is_some() {
                    return Err(RuntimeError::coded("E078", "process output read failed"));
                }
                return Ok(None);
            }
            buf = self.cond.wait(buf).unwrap();
        }
    }
}

// ---- process handle ----------------------------------------------------------------

struct ProcessState {
    program: String,
    args: Vec<String>,
    registry: Arc<ConcurrencyRegistry>,
    started: AtomicBool,
    child: Mutex<Option<std::process::Child>>,
    stdin_pipe: Mutex<Option<std::process::ChildStdin>>,
    stdin_closed: AtomicBool,
    stdout: Arc<StreamBuffer>,
    stderr: Arc<StreamBuffer>,
    done: Mutex<bool>,
    exit_code: Mutex<i64>,
    cond: Condvar,
}

fn new_process(program: String, args: Vec<String>, registry: &Arc<ConcurrencyRegistry>) -> Value {
    Value::Process(Arc::new(ProcessState {
        program,
        args,
        registry: registry.clone(),
        started: AtomicBool::new(false),
        child: Mutex::new(None),
        stdin_pipe: Mutex::new(None),
        stdin_closed: AtomicBool::new(false),
        stdout: StreamBuffer::new(),
        stderr: StreamBuffer::new(),
        done: Mutex::new(false),
        exit_code: Mutex::new(0),
        cond: Condvar::new(),
    }))
}
// Launches the child exactly once. Called by the Solvik-level .start() method;
// Process.new only constructs an unstarted handle.
fn process_start(state: &Arc<ProcessState>) -> Result<(), RuntimeError> {
    use std::io::Read;
    if state.started.swap(true, Ordering::SeqCst) {
        return Err(RuntimeError::coded("E081", "process already started"));
    }
    let program = state.program.clone();
    let mut command = std::process::Command::new(&program);
    command.args(&state.args).stdin(std::process::Stdio::piped()).stdout(std::process::Stdio::piped()).stderr(std::process::Stdio::piped());
    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(_) => return Err(RuntimeError::coded("E076", format!("cannot launch process '{}'", program))),
    };
    let stdin_pipe = child.stdin.take().ok_or_else(|| RuntimeError::coded("E076", format!("cannot launch process '{}'", program)))?;
    let stdout_pipe = child.stdout.take().ok_or_else(|| RuntimeError::coded("E076", format!("cannot launch process '{}'", program)))?;
    let stderr_pipe = child.stderr.take().ok_or_else(|| RuntimeError::coded("E076", format!("cannot launch process '{}'", program)))?;
    *state.child.lock().unwrap() = Some(child);
    *state.stdin_pipe.lock().unwrap() = Some(stdin_pipe);
    state.registry.register_process(state.clone());
    // Pump both pipes concurrently (a child may fill one pipe before ever
    // writing the other); reap the child only after both reached EOF, since
    // wait() would close the pipes and lose unread output otherwise.
    let out_state = state.clone();
    thread::spawn(move || {
        let mut pipe = stdout_pipe;
        let mut tmp = [0u8; 32 * 1024];
        loop {
            match pipe.read(&mut tmp) {
                Ok(0) => break,
                Ok(n) => out_state.stdout.append(&tmp[..n]),
                Err(_) => break,
            }
        }
        out_state.stdout.finish(None);
    });
    let err_state = state.clone();
    thread::spawn(move || {
        let mut pipe = stderr_pipe;
        let mut tmp = [0u8; 32 * 1024];
        loop {
            match pipe.read(&mut tmp) {
                Ok(0) => break,
                Ok(n) => err_state.stderr.append(&tmp[..n]),
                Err(_) => break,
            }
        }
        err_state.stderr.finish(None);
    });
    let wait_state = state.clone();
    thread::spawn(move || {
        // Wait for both pumps to finish before reaping.
        while !wait_state.stdout.eof.load(Ordering::SeqCst) || !wait_state.stderr.eof.load(Ordering::SeqCst) {
            thread::sleep(std::time::Duration::from_millis(1));
        }
        // Take the child out so reaping does not hold the lock (terminate()
        // needs it); a child that already closed its pipes is about to exit.
        let code = match wait_state.child.lock().unwrap().take() {
            Some(mut child) => wait_for_child(&mut child).unwrap_or(1),
            None => 1,
        };
        *wait_state.exit_code.lock().unwrap() = code;
        {
            let mut done = wait_state.done.lock().unwrap();
            *done = true;
        }
        wait_state.cond.notify_all();
    });
    Ok(())
}

impl ProcessState {
    fn require_started(&self) -> Result<(), RuntimeError> {
        if self.started.load(Ordering::SeqCst) { Ok(()) } else { Err(RuntimeError::coded("E081", "operation on unstarted process")) }
    }
}

/// Reaps the child and maps its exit to the Solvik convention: the exit code
/// on normal termination, or 128 + signum when killed by a signal (POSIX).
fn wait_for_child(child: &mut std::process::Child) -> Option<i64> {
    #[cfg(unix)]
    {
        let pid = child.id() as libc::pid_t;
        let mut status: libc::c_int = 0;
        // waitpid directly so the signal number survives (std's ExitStatus
        // does not expose it in this toolchain).
        loop {
            let result = unsafe { libc::waitpid(pid, &mut status, 0) };
            if result >= 0 || std::io::Error::last_os_error().kind() != std::io::ErrorKind::Interrupted {
                break;
            }
        }
        if libc::WIFSIGNALED(status) {
            return Some(128 + libc::WTERMSIG(status) as i64);
        }
        return Some(if libc::WIFEXITED(status) { libc::WEXITSTATUS(status) as i64 } else { 1 });
    }
    #[cfg(not(unix))]
    {
        match child.wait() {
            Ok(status) => Some(status.code().unwrap_or(1) as i64),
            Err(_) => None,
        }
    }
}

impl ProcessState {
    fn join(&self) -> i64 {
        let mut done = self.done.lock().unwrap();
        while !*done {
            done = self.cond.wait(done).unwrap();
        }
        *self.exit_code.lock().unwrap()
    }

    fn status(&self) -> Option<i64> {
        if *self.done.lock().unwrap() { Some(*self.exit_code.lock().unwrap()) } else { None }
    }

    fn is_done(&self) -> bool {
        *self.done.lock().unwrap()
    }

    /// Force-kills the direct child (SIGKILL on POSIX). No-op once exit has
    /// been observed.
    fn terminate(&self) -> Result<(), RuntimeError> {
        if self.is_done() {
            return Ok(());
        }
        let mut child = self.child.lock().unwrap();
        match child.as_mut() {
            Some(child) => child.kill().map_err(|_| RuntimeError::coded("E079", "process termination failed")),
            None => Ok(()),
        }
    }

    fn write_stdin(&self, text: &str) -> Result<(), RuntimeError> {
        self.require_started()?;
        if self.stdin_closed.load(Ordering::SeqCst) {
            return Err(RuntimeError::coded("E077", "write to closed process stdin"));
        }
        let mut pipe = self.stdin_pipe.lock().unwrap();
        let Some(pipe) = pipe.as_mut() else {
            return Err(RuntimeError::coded("E077", "write to closed process stdin"));
        };
        use std::io::Write;
        pipe.write_all(text.as_bytes()).and_then(|()| pipe.flush()).map_err(|_| RuntimeError::coded("E077", "process stdin write failed"))?;
        Ok(())
    }

    fn close_stdin(&self) {
        if self.stdin_closed.swap(true, Ordering::SeqCst) {
            return;
        }
        self.stdin_pipe.lock().unwrap().take();
    }
}

// ---- record values ---------------------------------------------------------------

// threadDefValue / processDefValue are represented directly as Value variants
// (ThreadDef(body) and ProcessDef(program, args)).

// ---- concurrency registry (shutdown policy) ---------------------------------------

/// Owns live threads and child processes for one run and implements the
/// shutdown policy: after main returns (or fails), wait for outstanding
/// threads (including workers they start), then close child stdin,
/// terminate/reap remaining direct children, and release process readers.
pub struct ConcurrencyRegistry {
    threads: Mutex<Vec<Arc<ThreadState>>>,
    processes: Mutex<Vec<Arc<ProcessState>>>,
}

impl ConcurrencyRegistry {
    fn new() -> Arc<ConcurrencyRegistry> {
        Arc::new(ConcurrencyRegistry { threads: Mutex::new(Vec::new()), processes: Mutex::new(Vec::new()) })
    }

    fn register_thread(&self, state: Arc<ThreadState>) {
        self.threads.lock().unwrap().push(state);
    }

    fn register_process(&self, state: Arc<ProcessState>) {
        self.processes.lock().unwrap().push(state);
    }

    fn shutdown(&self) {
        loop {
            let live: Vec<Arc<ThreadState>> = self.threads.lock().unwrap().iter().filter(|state| !state.is_done()).cloned().collect();
            if live.is_empty() {
                break;
            }
            for state in live {
                let _ = state.join();
            }
        }
        let processes = self.processes.lock().unwrap().clone();
        for state in processes {
            state.close_stdin();
            if !state.is_done() {
                let _ = state.terminate();
            }
            let _ = state.join();
        }
    }
}

// ---- builtins ------------------------------------------------------------------------

fn install_concurrency(env: &EnvRef, program_args: &[String]) -> Arc<ConcurrencyRegistry> {
    let registry = ConcurrencyRegistry::new();
    let args_list = program_args.iter().cloned().map(Value::String).collect::<Vec<_>>();
    Env::define(env, "args".into(), Value::Builtin(Arc::new(move |values| {
        if !values.is_empty() { return Err(RuntimeError::new("args expects no arguments")); }
        Ok(Value::List(Arc::new(RefCell::new(args_list.clone()))))
    })));
    let thread_ns = Env::new(Some(env.clone()));
    let thread_registry = registry.clone();
    Env::define(&thread_ns, "new".into(), Value::Builtin(Arc::new(move |args| {
        let [Value::ThreadDef(body)] = args.as_slice() else { return Err(RuntimeError::new("Thread.new expects a ThreadDef")); };
        Ok(new_thread((**body).clone(), &thread_registry))
    })));
    Env::define(env, "Thread".into(), Value::Namespace(Arc::new(NamespaceValue { env: thread_ns })));
    let process_ns = Env::new(Some(env.clone()));
    let process_registry = registry.clone();
    Env::define(&process_ns, "new".into(), Value::Builtin(Arc::new(move |args| {
        let [Value::ProcessDef(program, arguments)] = args.as_slice() else { return Err(RuntimeError::new("Process.new expects a ProcessDef")); };
        Ok(new_process(program.clone(), arguments.clone(), &process_registry))
    })));
    Env::define(env, "Process".into(), Value::Namespace(Arc::new(NamespaceValue { env: process_ns })));

    // Constructor namespaces for the remaining built-in value types.
    let mutex_ns = Env::new(Some(env.clone()));
    Env::define(&mutex_ns, "new".into(), Value::Builtin(Arc::new(|args| { if !args.is_empty() { return Err(RuntimeError::new("Mutex.new expects no arguments")); } Ok(Value::Mutex(MutexState::new())) })));
    Env::define(env, "Mutex".into(), Value::Namespace(Arc::new(NamespaceValue { env: mutex_ns })));
    let semaphore_ns = Env::new(Some(env.clone()));
    Env::define(&semaphore_ns, "new".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("Semaphore.new expects an Int count")); } let Value::Int(count) = &args[0] else { return Err(RuntimeError::new("Semaphore.new expects an Int count")); }; Ok(Value::Semaphore(SemaphoreState::new(*count)?)) })));
    Env::define(env, "Semaphore".into(), Value::Namespace(Arc::new(NamespaceValue { env: semaphore_ns })));
    let stack_ns = Env::new(Some(env.clone()));
    Env::define(&stack_ns, "new".into(), Value::Builtin(Arc::new(|args| { if !args.is_empty() { return Err(RuntimeError::new("Stack.new expects no arguments")); } Ok(Value::Stack(Arc::new(RefCell::new(Vec::new())))) })));
    Env::define(env, "Stack".into(), Value::Namespace(Arc::new(NamespaceValue { env: stack_ns })));
    let regex_ns = Env::new(Some(env.clone()));
    Env::define(&regex_ns, "new".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("Regex.new expects a String pattern")); } let Value::String(pattern) = &args[0] else { return Err(RuntimeError::new("Regex.new expects a String pattern")); }; Ok(Value::Regex(pattern.clone())) })));
    Env::define(env, "Regex".into(), Value::Namespace(Arc::new(NamespaceValue { env: regex_ns })));
    let list_ns = Env::new(Some(env.clone()));
    Env::define(&list_ns, "new".into(), Value::Builtin(Arc::new(|args| { if !args.is_empty() { return Err(RuntimeError::new("List.new expects no arguments")); } Ok(Value::List(Arc::new(RefCell::new(Vec::new())))) })));
    Env::define(env, "List".into(), Value::Namespace(Arc::new(NamespaceValue { env: list_ns })));
    let map_ns = Env::new(Some(env.clone()));
    Env::define(&map_ns, "new".into(), Value::Builtin(Arc::new(|args| { if !args.is_empty() { return Err(RuntimeError::new("Map.new expects no arguments")); } Ok(Value::Map(Arc::new(RefCell::new(Vec::new())))) })));
    Env::define(env, "Map".into(), Value::Namespace(Arc::new(NamespaceValue { env: map_ns })));
    let exception_ns = Env::new(Some(env.clone()));
    Env::define(&exception_ns, "new".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("Exception.new expects a String message")); } let Value::String(message) = &args[0] else { return Err(RuntimeError::new("Exception.new expects a String message")); }; Ok(Value::Exception(RuntimeError::new(message.clone()))) })));
    Env::define(env, "Exception".into(), Value::Namespace(Arc::new(NamespaceValue { env: exception_ns })));
    registry
}

enum Flow { Normal, Return(Value), Break, Continue }

#[cfg(test)]
pub fn run(program: &crate::semantic_ast::Program) -> Result<i32, RuntimeError> {
    let global = Env::package(None, program.package.clone());
    install_builtins(&global);
    install_type_declarations(&global, program);
    install_bytecode_declarations(&global, program);
    let registry = install_concurrency(&global, &[]);
    heap_thread_start();
    let result = execute_main_bytecode(&global);
    // Release the heap lock before shutdown so outstanding workers can still
    // run; shutdown itself touches only thread/process state, no values.
    heap_thread_end();
    registry.shutdown();
    result
}

fn install_type_declarations(global: &EnvRef, program: &crate::semantic_ast::Program) {
    for declaration in &program.declarations {
        if let Decl::Enum(enum_decl) = declaration {
            let mut next_value = 0;
            let members = enum_decl.members.iter().map(|member| {
                let value = if member.payload.is_empty() { let value = member.value.unwrap_or(next_value); next_value = value + 1; Some(value) } else { None };
                (member.name.clone(), EnumMemberDef { payload_count: member.payload.len(), value })
            }).collect();
            Env::define(&global, enum_decl.name.clone(), Value::EnumType(Arc::new(EnumDef { name: enum_decl.name.clone(), members, type_param_count: enum_decl.type_params.len() })));
        }
        if let Decl::Struct(struct_decl) = declaration {
            let methods = struct_decl.methods.iter().map(|method| (method.name.clone(), method.clone())).collect();
            let method_public = struct_decl.methods.iter().map(|method| (method.name.clone(), method.public)).collect();
            let field_public = struct_decl.fields.iter().map(|field| (field.name.clone(), field.public)).collect();
            let package = global.borrow().package.clone();
            Env::define(&global, struct_decl.name.clone(), Value::StructType(Arc::new(StructDef {
                name: struct_decl.name.clone(), owner_package: package, package_env: global.clone(), methods, method_public,
                field_public, type_param_count: struct_decl.type_params.len(),
            })));
        }
    }
}


fn validate_entry(program: &crate::semantic_ast::Program) -> Result<(), RuntimeError> {
    if let Some(Decl::Function(function)) = program.declarations.iter().find(|declaration| matches!(declaration, Decl::Function(function) if function.name == "main")) {
        if !function.params.is_empty() {
            return Err(RuntimeError::coded("C123", "entry function 'main' must take no parameters"));
        }
        if function.return_type.name != "int" && function.return_type.name != "void" {
            return Err(RuntimeError::coded("C124", format!("entry function 'main' must return int or nothing, not {}", function.return_type)));
        }
    }
    Ok(())
}

fn load_dependency_programs(path: &str, program: &crate::semantic_ast::Program) -> Result<Vec<crate::semantic_ast::Program>, RuntimeError> {
    let base = std::path::Path::new(path).parent().unwrap_or_else(|| std::path::Path::new("."));
    let builtins = ["core", "string", "math", "random", "path", "base64", "hash", "secrets", "file", "env", "json", "time", "http", "test", "stack"];
    let mut dependencies = Vec::new();
    for dependency in &program.uses {
        if dependency.scheme != "file" { return Err(RuntimeError::new(format!("unsupported dependency scheme {}", dependency.scheme))); }
        let requested = std::path::Path::new(&dependency.value);
        let candidate = if requested.is_absolute() { requested.to_path_buf() } else { base.join(requested) };
        let dotted = if !dependency.value.contains('/') && !dependency.value.contains('\\') { Some(base.join(dependency.value.replace('.', "/") + ".sol")) } else { None };
        let dependency_path = if candidate.exists() { candidate } else if candidate.extension().is_none() { candidate.with_extension("sol") } else if dotted.as_ref().is_some_and(|path| path.exists()) { dotted.unwrap() } else { candidate };
        let dependency_source = std::fs::read_to_string(&dependency_path).map_err(|error| RuntimeError::new(format!("cannot read dependency {}: {}", dependency_path.display(), error)))?;
        let dependency_program = crate::semantic_parser::parse(&dependency_source).map_err(|error| parse_error(&dependency_path.display().to_string(), error))?;
        if builtins.contains(&dependency_program.package.as_str()) { return Err(RuntimeError::coded("C121", format!("package name '{}' conflicts with a built-in namespace", dependency_program.package))); }
        dependencies.push(dependency_program);
    }
    Ok(dependencies)
}

pub fn check_file(path: &str) -> Result<(), RuntimeError> {
    let source = std::fs::read_to_string(path).map_err(|error| RuntimeError::new(format!("cannot read source file: {}", error)))?;
    let program = crate::semantic_parser::parse(&source).map_err(|error| parse_error(path, error))?;
    validate_entry(&program)?;
    let dependencies = load_dependency_programs(path, &program)?;
    for dependency in &dependencies {
        crate::semantic_validator::validate(dependency).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    }
    crate::semantic_validator::validate_with_dependencies(&program, &dependencies).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    Ok(())
}

pub fn run_file(path: &str, program_args: &[String]) -> Result<i32, RuntimeError> {
    let source = std::fs::read_to_string(path).map_err(|error| RuntimeError::new(format!("cannot read source file: {}", error)))?;
    let program = crate::semantic_parser::parse(&source).map_err(|error| parse_error(path, error))?;
    validate_entry(&program)?;
    let dependencies = load_dependency_programs(path, &program)?;
    for dependency in &dependencies {
        crate::semantic_validator::validate(dependency).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    }
    crate::semantic_validator::validate_with_dependencies(&program, &dependencies).map_err(|error| RuntimeError::coded(&error.code, error.message))?;
    let global = Env::package(None, program.package.clone()); install_builtins(&global); install_type_declarations(&global, &program); install_bytecode_declarations(&global, &program);
    let registry = install_concurrency(&global, program_args);
    for dependency_program in &dependencies {
        let dependency_env = Env::package(Some(global.clone()), dependency_program.package.clone()); install_type_declarations(&dependency_env, &dependency_program); install_bytecode_declarations(&dependency_env, dependency_program);
        Env::define(&global, dependency_program.package.clone(), Value::Namespace(Arc::new(NamespaceValue { env: dependency_env })));
    }
    // Main runs under the heap lock; the shutdown policy (wait for outstanding
    // threads, then close child stdin and terminate/reap remaining children)
    // runs even when main fails.
    heap_thread_start();
    let result = execute_main_bytecode(&global);
    // Release the heap lock before shutdown so outstanding workers can still
    // run; shutdown itself touches only thread/process state, no values.
    heap_thread_end();
    registry.shutdown();
    result
}

fn parse_error(path: &str, error: crate::semantic_parser::ParseError) -> RuntimeError {
    let code = if error.message.starts_with("P123:") { "P123" } else if error.message.starts_with("P078:") { "P078" } else if error.message.starts_with("P075:") { "P075" } else if error.message.starts_with("L016:") || error.message.starts_with("unknown escape sequence") { "L016" } else if error.message.starts_with("L017:") || error.message.starts_with("invalid hexadecimal escape") { "L017" } else if error.message.starts_with("C125:") { "C125" } else { "P000" };
    RuntimeError::coded(code, format!("{}:{}:{}: parse error: {}", path, error.position.line, error.position.column, error.message))
}

fn install_builtins(env: &EnvRef) {
    let print = |newline: bool| Value::Builtin(Arc::new(move |args| {
        let text = args.iter().map(|a| a.to_string()).collect::<Vec<_>>().join(" ");
        if newline { println!("{}", text); } else { print!("{}", text); }
        Ok(Value::Null)
    }));
    Env::define(env, "print".into(), print(false)); Env::define(env, "println".into(), print(true));
    install_string(env);
    Env::define(env, "int".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("int expects 1 argument")); } match &args[0] { Value::Enum(value) if value.payload.is_empty() => value.definition.members.get(&value.member).and_then(|member| member.value).map(Value::Int).ok_or_else(|| RuntimeError::coded("E066", "payload enum values have no integer conversion")), Value::Enum(_) => Err(RuntimeError::coded("E066", "payload enum values have no integer conversion")), Value::Byte(v) => Ok(Value::Int(*v as i64)), Value::Int(v) => Ok(Value::Int(*v)), Value::Float(v) => Ok(Value::Int(*v as i64)), Value::Bool(v) => Ok(Value::Int(*v as i64)), Value::Char(v) => Ok(Value::Int(*v as i64)), Value::String(v) => v.parse().map(Value::Int).map_err(|_| RuntimeError::coded("E073", format!("cannot convert '{}' to int", v))), _ => Err(RuntimeError::new("cannot convert value to int")) } })));
    Env::define(env, "byte".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("byte expects 1 argument")); } let value = match &args[0] { Value::Byte(v) => *v as i64, Value::Int(v) => *v, Value::Float(v) => *v as i64, _ => return Err(RuntimeError::coded("E073", "cannot convert value to byte")) }; u8::try_from(value).map(Value::Byte).map_err(|_| RuntimeError::coded("E073", "byte conversion out of range")) })));
    Env::define(env, "float".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("float expects 1 argument")); } match &args[0] { Value::Int(v) => Ok(Value::Float(*v as f64)), Value::Float(v) => Ok(Value::Float(*v)), Value::String(v) => v.parse().map(Value::Float).map_err(|_| RuntimeError::coded("E073", format!("cannot convert '{}' to float", v))), _ => Err(RuntimeError::new("cannot convert value to float")) } })));
    Env::define(env, "bool".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("bool expects 1 argument")); } Ok(Value::Bool(truthy(&args[0]))) })));
    Env::define(env, "typeOf".into(), Value::Builtin(Arc::new(|args| { if args.len() != 1 { return Err(RuntimeError::new("typeOf expects 1 argument")); } Ok(Value::String(type_name(&args[0]))) })));
    Env::define(env, "isType".into(), Value::Builtin(Arc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("isType expects 2 arguments")); } let Value::String(expected) = &args[1] else { return Err(RuntimeError::new("isType expects a string as second argument")); }; Ok(Value::Bool(type_name(&args[0]) == *expected)) })));
    let environment = Env::new(Some(env.clone()));
    Env::define(&environment, "get".into(), Value::Builtin(Arc::new(|values| {
        let [Value::String(name)] = values.as_slice() else { return Err(RuntimeError::new("env.get expects one string")); };
        Ok(std::env::var(name).map(Value::String).unwrap_or(Value::Null))
    })));
    Env::define(&environment, "set".into(), Value::Builtin(Arc::new(|values| {
        let [Value::String(name), Value::String(value)] = values.as_slice() else { return Err(RuntimeError::new("env.set expects two strings")); };
        std::env::set_var(name, value);
        Ok(Value::Null)
    })));
    Env::define(&environment, "keys".into(), Value::Builtin(Arc::new(|values| {
        if !values.is_empty() { return Err(RuntimeError::new("env.keys expects no arguments")); }
        Ok(Value::List(Arc::new(RefCell::new(std::env::vars().map(|(name, _)| Value::String(name)).collect()))))
    })));
    Env::define(env, "env".into(), Value::Namespace(Arc::new(NamespaceValue { env: environment })));
    install_random(env);
    install_path(env);
    install_math(env);
    install_base64(env);
    install_hash(env);
    install_secrets(env);
    install_file(env);
    install_json(env);
    install_time(env);
    install_test(env);
    install_http(env);
}

fn install_random(env: &EnvRef) {
    let module = Env::new(Some(env.clone()));
    let state = Arc::new(RefCell::new(0x9e37_79b9_u64));
    let next = |state: Arc<RefCell<u64>>| move || { let mut value = state.borrow_mut(); *value = value.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407); *value };
    let float_state = state.clone();
    Env::define(&module, "float".into(), Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("random.float expects no arguments")); } Ok(Value::Float(next(float_state.clone())() as f64 / (u64::MAX as f64 + 1.0))) })));
    let int_state = state.clone();
    Env::define(&module, "int".into(), Value::Builtin(Arc::new(move |args| { let [Value::Int(low), Value::Int(high)] = args.as_slice() else { return Err(RuntimeError::new("random.int expects two integers")); }; if low > high { return Err(RuntimeError::coded("E072", "random.int lower bound exceeds upper bound")); } let width = (*high as i128 - *low as i128 + 1) as u128; let value = (next(int_state.clone())() as u128 % width) as i128 + *low as i128; Ok(Value::Int(value as i64)) })));
    let range_state = state.clone();
    Env::define(&module, "range".into(), Value::Builtin(Arc::new(move |args| { let (low, high) = match args.as_slice() { [Value::Int(n)] => { if *n <= 0 { return Err(RuntimeError::coded("E072", "random.range requires a positive count")); } (0i64, *n) } [Value::Int(low), Value::Int(high)] => { if low >= high { return Err(RuntimeError::coded("E072", "random.range requires lower bound below upper bound")); } (*low, *high) } _ => return Err(RuntimeError::new("random.range expects one or two integers")) }; Ok(Value::Int((next(range_state.clone())() % ((high - low) as u64)) as i64 + low)) })));
    let uniform_state = state.clone();
    Env::define(&module, "uniform".into(), Value::Builtin(Arc::new(move |args| { let [Value::Float(low), Value::Float(high)] = args.as_slice() else { return Err(RuntimeError::new("random.uniform expects two floats")); }; Ok(Value::Float(low + (high - low) * (next(uniform_state.clone())() as f64 / (u64::MAX as f64 + 1.0)))) })));
    let seed_state = state.clone();
    Env::define(&module, "seed".into(), Value::Builtin(Arc::new(move |args| { let [Value::Int(seed)] = args.as_slice() else { return Err(RuntimeError::new("random.seed expects one integer")); }; *seed_state.borrow_mut() = *seed as u64; Ok(Value::Null) })));
    let choice_state = state.clone();
    Env::define(&module, "choice".into(), Value::Builtin(Arc::new(move |args| { let [Value::List(items)] = args.as_slice() else { return Err(RuntimeError::new("random.choice expects a list")); }; let items = items.borrow(); if items.is_empty() { return Ok(Value::Null); } Ok(items[(next(choice_state.clone())() % items.len() as u64) as usize].clone()) })));
    let shuffle_state = state.clone();
    Env::define(&module, "shuffle".into(), Value::Builtin(Arc::new(move |args| { let [Value::List(items)] = args.as_slice() else { return Err(RuntimeError::new("random.shuffle expects a list")); }; let mut result = items.borrow().clone(); for i in (1..result.len()).rev() { let j = (next(shuffle_state.clone())() % (i as u64 + 1)) as usize; result.swap(i, j); } Ok(Value::List(Arc::new(RefCell::new(result)))) })));
    let sample_state = state;
    Env::define(&module, "sample".into(), Value::Builtin(Arc::new(move |args| { let [Value::List(items), Value::Int(requested)] = args.as_slice() else { return Err(RuntimeError::new("random.sample expects a list and count")); }; let mut result = items.borrow().clone(); for i in (1..result.len()).rev() { let j = (next(sample_state.clone())() % (i as u64 + 1)) as usize; result.swap(i, j); } result.truncate((*requested).max(0) as usize); Ok(Value::List(Arc::new(RefCell::new(result)))) })));
    Env::define(env, "random".into(), Value::Namespace(Arc::new(NamespaceValue { env: module })));
}

fn module(env: &EnvRef) -> EnvRef { Env::new(Some(env.clone())) }
fn install_path(env: &EnvRef) {
    let path = module(env);
    Env::define(&path, "join".into(), Value::Builtin(Arc::new(|args| { if args.is_empty() { return Err(RuntimeError::new("path.join expects at least one argument")); } let mut result = std::path::PathBuf::new(); for value in args { let Value::String(value) = value else { return Err(RuntimeError::new("path.join expects strings")); }; result.push(value); } Ok(Value::String(result.to_string_lossy().replace('\\', "/"))) })));
    Env::define(&path, "basename".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.basename expects one string")); }; Ok(Value::String(std::path::Path::new(value).file_name().and_then(|part| part.to_str()).unwrap_or("").into())) })));
    Env::define(&path, "dirname".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.dirname expects one string")); }; Ok(Value::String(std::path::Path::new(value).parent().and_then(|part| part.to_str()).unwrap_or(".").replace('\\', "/"))) })));
    Env::define(&path, "ext".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.ext expects one string")); }; Ok(Value::String(std::path::Path::new(value).extension().and_then(|part| part.to_str()).map(|part| format!(".{}", part)).unwrap_or_default())) })));
    Env::define(&path, "abs".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.abs expects one string")); }; let path = std::path::Path::new(value); let path = if path.is_absolute() { path.to_path_buf() } else { std::env::current_dir().unwrap_or_default().join(path) }; Ok(Value::String(path.to_string_lossy().replace('\\', "/"))) })));
    Env::define(&path, "exists".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("path.exists expects one string")); }; Ok(Value::Bool(std::path::Path::new(value).exists())) })));
    Env::define(env, "path".into(), Value::Namespace(Arc::new(NamespaceValue { env: path })));
}

fn install_math(env: &EnvRef) {
    let math = module(env);
    Env::define(&math, "PI".into(), Value::Float(std::f64::consts::PI)); Env::define(&math, "E".into(), Value::Float(std::f64::consts::E));
    Env::define(&math, "abs".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.abs expects one argument")); }; match value { Value::Int(value) => Ok(Value::Int(value.abs())), Value::Float(value) => Ok(Value::Float(value.abs())), _ => Err(RuntimeError::new("math.abs expects a numeric argument")) } })));
    Env::define(&math, "min".into(), Value::Builtin(Arc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.min expects 2 arguments")); } if matches!((&args[0], &args[1]), (Value::Float(_), _) | (_, Value::Float(_))) { Ok(Value::Float(number(&args[0])?.min(number(&args[1])?))) } else { Ok(Value::Int(integer_value(&args[0])?.min(integer_value(&args[1])?))) } })));
    Env::define(&math, "max".into(), Value::Builtin(Arc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.max expects 2 arguments")); } if matches!((&args[0], &args[1]), (Value::Float(_), _) | (_, Value::Float(_))) { Ok(Value::Float(number(&args[0])?.max(number(&args[1])?))) } else { Ok(Value::Int(integer_value(&args[0])?.max(integer_value(&args[1])?))) } })));
    Env::define(&math, "floor".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.floor expects one argument")); }; Ok(Value::Int(number(value)?.floor() as i64)) })));
    Env::define(&math, "ceil".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.ceil expects one argument")); }; Ok(Value::Int(number(value)?.ceil() as i64)) })));
    Env::define(&math, "round".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.round expects one argument")); }; Ok(Value::Int(number(value)?.round() as i64)) })));
    Env::define(&math, "sqrt".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.sqrt expects one argument")); }; Ok(Value::Float(number(value)?.sqrt())) })));
    Env::define(&math, "pow".into(), Value::Builtin(Arc::new(|args| { if args.len() != 2 { return Err(RuntimeError::new("math.pow expects 2 arguments")); }; Ok(Value::Float(number(&args[0])?.powf(number(&args[1])?))) })));
    Env::define(&math, "sin".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.sin expects one argument")); }; Ok(Value::Float(number(value)?.sin())) })));
    Env::define(&math, "cos".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.cos expects one argument")); }; Ok(Value::Float(number(value)?.cos())) })));
    Env::define(&math, "tan".into(), Value::Builtin(Arc::new(|args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("math.tan expects one argument")); }; Ok(Value::Float(number(value)?.tan())) })));
    Env::define(env, "math".into(), Value::Namespace(Arc::new(NamespaceValue { env: math })));
}

fn install_base64(env: &EnvRef) {
    let base64 = module(env);
    Env::define(&base64, "encode".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("base64.encode expects one string")); }; Ok(Value::String(base64::engine::general_purpose::STANDARD.encode(value.as_bytes()))) })));
    Env::define(&base64, "decode".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("base64.decode expects one string")); }; let bytes = base64::engine::general_purpose::STANDARD.decode(value).map_err(|_| RuntimeError::coded("E072", "base64 decode failed"))?; String::from_utf8(bytes).map(Value::String).map_err(|_| RuntimeError::coded("E072", "base64 decoded data is not UTF-8")) })));
    Env::define(env, "base64".into(), Value::Namespace(Arc::new(NamespaceValue { env: base64 })));
}

fn digest_hex<D: Md5Digest>(bytes: &[u8]) -> String { D::digest(bytes).iter().map(|byte| format!("{:02x}", byte)).collect() }
fn install_hash(env: &EnvRef) {
    let hash = module(env);
    Env::define(&hash, "md5".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.md5 expects one string")); }; Ok(Value::String(digest_hex::<md5::Md5>(value.as_bytes()))) })));
    Env::define(&hash, "sha1".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha1 expects one string")); }; Ok(Value::String(digest_hex::<Sha1>(value.as_bytes()))) })));
    Env::define(&hash, "sha256".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha256 expects one string")); }; Ok(Value::String(digest_hex::<Sha256>(value.as_bytes()))) })));
    Env::define(&hash, "sha512".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("hash.sha512 expects one string")); }; Ok(Value::String(digest_hex::<Sha512>(value.as_bytes()))) })));
    Env::define(env, "hash".into(), Value::Namespace(Arc::new(NamespaceValue { env: hash })));
}

fn random_bytes(count: usize) -> Vec<u8> { let mut bytes = vec![0; count]; if getrandom::getrandom(&mut bytes).is_err() { let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos() as u64; for (index, byte) in bytes.iter_mut().enumerate() { *byte = stamp.rotate_left(index as u32) as u8; } } bytes }
fn install_secrets(env: &EnvRef) {
    let secrets = module(env);
    Env::define(&secrets, "token".into(), Value::Builtin(Arc::new(|args| { let [Value::Int(count)] = args.as_slice() else { return Err(RuntimeError::new("secrets.token expects one integer")); }; if *count <= 0 { return Err(RuntimeError::coded("E072", "secrets.token n must be > 0")); } Ok(Value::String(base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(random_bytes(*count as usize)))) })));
    Env::define(&secrets, "hex".into(), Value::Builtin(Arc::new(|args| { let [Value::Int(count)] = args.as_slice() else { return Err(RuntimeError::new("secrets.hex expects one integer")); }; if *count <= 0 { return Err(RuntimeError::coded("E072", "secrets.hex n must be > 0")); } Ok(Value::String(random_bytes(*count as usize).iter().map(|byte| format!("{:02x}", byte)).collect())) })));
    Env::define(env, "secrets".into(), Value::Namespace(Arc::new(NamespaceValue { env: secrets })));
}

fn install_json(env: &EnvRef) {
    let json = module(env);
    Env::define(&json, "parse".into(), Value::Builtin(Arc::new(|args| {
        let [Value::String(source)] = args.as_slice() else {
            return Err(RuntimeError::new("json.parse expects one string"));
        };
        let parsed: JsonValue = serde_json::from_str(source)
            .map_err(|error| RuntimeError::coded("E072", format!("json parse error: {}", error)))?;
        json_to_value(parsed)
    })));
    Env::define(&json, "stringify".into(), Value::Builtin(Arc::new(|args| {
        let [value] = args.as_slice() else {
            return Err(RuntimeError::new("json.stringify expects one argument"));
        };
        let mut out = String::new();
        write_json_value(&mut out, value)?;
        Ok(Value::String(out))
    })));
    Env::define(env, "json".into(), Value::Namespace(Arc::new(NamespaceValue { env: json })));
}

fn json_to_value(value: JsonValue) -> Result<Value, RuntimeError> {
    match value {
        JsonValue::Null => Ok(Value::Null),
        JsonValue::Bool(value) => Ok(Value::Bool(value)),
        JsonValue::Number(value) => value.as_i64().map(Value::Int)
            .or_else(|| value.as_f64().map(Value::Float))
            .ok_or_else(|| RuntimeError::coded("E072", "json number is outside Solvik numeric range")),
        JsonValue::String(value) => Ok(Value::String(value)),
        JsonValue::Array(values) => Ok(Value::List(Arc::new(RefCell::new(
            values.into_iter().map(json_to_value).collect::<Result<Vec<_>, _>>()?,
        )))),
        // serde_json::Map is a BTreeMap by default, so object keys arrive in
        // code-point order — already consistent with the canonical sorted Map
        // key order.  Do not enable the "preserve_order" feature without
        // re-sorting here.
        JsonValue::Object(values) => Ok(Value::Map(Arc::new(RefCell::new(
            values.into_iter().map(|(key, value)| Ok((Value::String(key), json_to_value(value)?)))
                .collect::<Result<Vec<_>, RuntimeError>>()?,
        )))),
    }
}

// json.stringify mirrors the Python reference's json.dumps defaults:
// ", " and ": " separators, sorted map key order (TreeMap semantics),
// ensure_ascii string escaping, and Python float repr formatting. Byte and
// Stack values are not representable (E072), matching the reference.
fn write_json_value(sb: &mut String, value: &Value) -> Result<(), RuntimeError> {
    match value {
        Value::Null => sb.push_str("null"),
        Value::Bool(value) => sb.push_str(if *value { "true" } else { "false" }),
        Value::Byte(_) => {
            return Err(RuntimeError::coded("E072", "value of type Byte is not representable as JSON"))
        }
        Value::Int(value) => sb.push_str(&value.to_string()),
        Value::Float(value) => sb.push_str(&python_float_string(*value)?),
        Value::Char(value) => write_json_string(sb, &value.to_string()),
        Value::String(value) => write_json_string(sb, value),
        Value::Stack(_) => {
            return Err(RuntimeError::coded("E072", "value of type Stack is not representable as JSON"))
        }
        Value::List(items) => {
            sb.push('[');
            for (i, item) in items.borrow().iter().enumerate() {
                if i > 0 { sb.push_str(", "); }
                write_json_value(sb, item)?;
            }
            sb.push(']');
        }
        Value::Map(entries) => {
            sb.push('{');
            for (i, (key, val)) in entries.borrow().iter().enumerate() {
                if i > 0 { sb.push_str(", "); }
                let text = match key {
                    Value::String(key) => key.clone(),
                    Value::Int(key) => key.to_string(),
                    Value::Float(key) => python_float_string(*key)?,
                    Value::Bool(key) => if *key { "true".into() } else { "false".into() },
                    Value::Null => "null".into(),
                    other => return Err(RuntimeError::coded("E072", format!("json stringify error: keys must be str, int, float, bool or None, not {}", type_name(other)))),
                };
                write_json_string(sb, &text);
                sb.push_str(": ");
                write_json_value(sb, val)?;
            }
            sb.push('}');
        }
        // Enum/exception render like the reference string(); struct rendering
        // follows the runtime representation (see known display gaps).
        Value::Struct(_) | Value::Enum(_) | Value::Exception(_) => write_json_string(sb, &value.to_string()),
        _ => return Err(RuntimeError::coded("E072", format!("value of type {} is not representable as JSON", type_name(value)))),
    }
    Ok(())
}

// write_json_string escapes with ensure_ascii semantics: short escapes for
// the common controls, \u00XX for other controls, and \uXXXX (surrogate
// pairs for astral planes) for everything above 0x7E.
fn write_json_string(sb: &mut String, s: &str) {
    sb.push('"');
    for r in s.chars() {
        match r {
            '"' => sb.push_str("\\\""),
            '\\' => sb.push_str("\\\\"),
            '\u{08}' => sb.push_str("\\b"),
            '\u{0c}' => sb.push_str("\\f"),
            '\n' => sb.push_str("\\n"),
            '\r' => sb.push_str("\\r"),
            '\t' => sb.push_str("\\t"),
            _ => {
                if (r as u32) < 0x20 {
                    sb.push_str(&format!("\\u{:04x}", r as u32));
                } else if (r as u32) > 0x7e {
                    let c = r as u32;
                    if c <= 0xffff {
                        sb.push_str(&format!("\\u{:04x}", c));
                    } else {
                        let d = c - 0x10000;
                        sb.push_str(&format!("\\u{:04x}\\u{:04x}", 0xd800 + d >> 10, 0xdc00 + d & 0x3ff));
                    }
                } else {
                    sb.push(r);
                }
            }
        }
    }
    sb.push('"');
}

// python_float_string formats like Python's float repr: shortest round trip,
// fixed notation while -4 <= exp <= 15 (with a trailing ".0" for integral
// values), exponential d[.ddd]e±XX otherwise.
fn python_float_string(f: f64) -> Result<String, RuntimeError> {
    if f.is_nan() || f.is_infinite() {
        return Err(RuntimeError::coded("E072", "json stringify error: Out of range float values are not JSON compliant"));
    }
    let neg = f.signum() < 0.0;
    let e = format!("{:e}", f.abs());
    let at = e.find('e').unwrap();
    let (mantissa, exp_part) = e.split_at(at);
    let exp: i32 = exp_part[1..].parse().unwrap();
    let digits: String = mantissa.chars().filter(|c| *c != '.').collect();
    let sign = if neg { "-" } else { "" };
    if exp < -4 || exp >= 16 {
        let frac = digits[1..].trim_end_matches('0');
        let mut mant = digits.chars().next().unwrap().to_string();
        if !frac.is_empty() {
            mant.push('.');
            mant.push_str(frac);
        }
        return Ok(format!("{}{}e{}", sign, mant, format_python_exponent(exp)));
    }
    let point = exp + 1;
    if point <= 0 {
        Ok(format!("{}0.{}{}", sign, "0".repeat(-point as usize), digits))
    } else if point >= digits.len() as i32 {
        Ok(format!("{}{}{}.0", sign, digits, "0".repeat((point - digits.len() as i32) as usize)))
    } else {
        Ok(format!("{}{}.{}", sign, &digits[..point as usize], &digits[point as usize..]))
    }
}

fn format_python_exponent(exp: i32) -> String {
    let abs = exp.abs();
    let mut text = abs.to_string();
    if text.len() < 2 {
        text.insert(0, '0');
    }
    if exp < 0 {
        format!("-{}", text)
    } else {
        format!("+{}", text)
    }
}

fn install_time(env: &EnvRef) {
    let time = module(env);
    Env::define(&time, "now".into(), Value::Builtin(Arc::new(|args| {
        if !args.is_empty() { return Err(RuntimeError::new("time.now expects no arguments")); }
        let millis = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)
            .map_err(|error| RuntimeError::coded("E072", error.to_string()))?
            .as_millis();
        Ok(Value::Int(millis.min(i64::MAX as u128) as i64))
    })));
    Env::define(&time, "sleep".into(), Value::Builtin(Arc::new(|args| {
        let [milliseconds] = args.as_slice() else { return Err(RuntimeError::new("time.sleep expects one integer")); };
        let milliseconds = integer_value(milliseconds)?;
        if milliseconds > 0 { std::thread::sleep(std::time::Duration::from_millis(milliseconds as u64)); }
        Ok(Value::Null)
    })));
    Env::define(&time, "iso".into(), Value::Builtin(Arc::new(|args| {
        let [milliseconds] = args.as_slice() else { return Err(RuntimeError::new("time.iso expects one integer")); };
        let milliseconds = integer_value(milliseconds)?;
        let value = DateTime::<Utc>::from_timestamp_millis(milliseconds)
            .ok_or_else(|| RuntimeError::coded("E072", "time.iso timestamp is out of range"))?;
        Ok(Value::String(value.to_rfc3339_opts(SecondsFormat::AutoSi, true)))
    })));
    Env::define(&time, "parse".into(), Value::Builtin(Arc::new(|args| {
        let [Value::String(source)] = args.as_slice() else { return Err(RuntimeError::new("time.parse expects one string")); };
        let source = source.strip_suffix('Z').unwrap_or(source);
        let parsed = DateTime::parse_from_rfc3339(&format!("{}Z", source))
            .or_else(|_| DateTime::parse_from_rfc3339(source))
            .map_err(|error| RuntimeError::coded("E072", format!("time parse error: {}", error)))?;
        Ok(Value::Int(parsed.timestamp_millis()))
    })));
    Env::define(env, "time".into(), Value::Namespace(Arc::new(NamespaceValue { env: time })));
}

fn install_test(env: &EnvRef) {
    let test = module(env);
    let assertion = |check: fn(&[Value]) -> Result<(), String>| {
        Value::Builtin(Arc::new(move |args| check(&args).map(|_| Value::Null).map_err(|message| RuntimeError::coded("E071", message))))
    };
    Env::define(&test, "assert".into(), assertion(|args| {
        if args.is_empty() || args.len() > 2 { return Err("expected condition and optional message".into()); }
        if truthy(&args[0]) { Ok(()) } else { Err(format!("assertion failed: {}", args.get(1).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(&test, "assertTrue".into(), assertion(|args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if truthy(&args[0]) { Ok(()) } else { Err(format!("assertion failed: {}", args.get(1).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(&test, "assertFalse".into(), assertion(|args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if !truthy(&args[0]) { Ok(()) } else { Err(format!("assertion failed: {}", args.get(1).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(&test, "assertEq".into(), assertion(|args| {
        if args.len() < 2 || args.len() > 3 { return Err("expected two values and optional message".into()); }
        if args[0] == args[1] { Ok(()) } else { Err(format!("assertion failed: expected {} equal to {} {}", args[0], args[1], args.get(2).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(&test, "assertNe".into(), assertion(|args| {
        if args.len() < 2 || args.len() > 3 { return Err("expected two values and optional message".into()); }
        if args[0] != args[1] { Ok(()) } else { Err(format!("assertion failed: expected {} not equal to {} {}", args[0], args[1], args.get(2).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(&test, "assertNull".into(), assertion(|args| {
        if args.is_empty() || args.len() > 2 { return Err("expected value and optional message".into()); }
        if matches!(args[0], Value::Null) { Ok(()) } else { Err(format!("assertion failed: expected null but got {} {}", type_name(&args[0]), args.get(1).map(ToString::to_string).unwrap_or_default())) }
    }));
    Env::define(env, "test".into(), Value::Namespace(Arc::new(NamespaceValue { env: test })));
}

fn install_http(env: &EnvRef) {
    let http = module(env);
    let request = Arc::new(|args: Vec<Value>| -> Result<Value, RuntimeError> {
        if args.len() != 4 { return Err(RuntimeError::new("http.request expects method, url, body, and headers")); }
        let Value::String(method) = &args[0] else { return Err(RuntimeError::new("http method must be a string")); };
        let Value::String(url) = &args[1] else { return Err(RuntimeError::new("http url must be a string")); };
        let body = match &args[2] { Value::Null => None, Value::String(value) => Some(value.as_bytes().to_vec()), _ => return Err(RuntimeError::new("http body must be a string or null")) };
        let Value::Map(headers) = &args[3] else { return Err(RuntimeError::new("http headers must be a map")); };
        http_request(method, url, body, headers)
    });
    let get_request = request.clone();
    Env::define(&http, "get".into(), Value::Builtin(Arc::new(move |args| {
        let [Value::String(url)] = args.as_slice() else { return Err(RuntimeError::new("http.get expects one URL")); };
        get_request(vec![Value::String("GET".into()), Value::String(url.clone()), Value::Null, Value::Map(Arc::new(RefCell::new(Vec::new())))])
    })));
    let post_request = request.clone();
    Env::define(&http, "post".into(), Value::Builtin(Arc::new(move |args| {
        let [Value::String(url), Value::String(body)] = args.as_slice() else { return Err(RuntimeError::new("http.post expects URL and body")); };
        post_request(vec![Value::String("POST".into()), Value::String(url.clone()), Value::String(body.clone()), Value::Map(Arc::new(RefCell::new(Vec::new())))])
    })));
    Env::define(&http, "request".into(), Value::Builtin(request));
    Env::define(env, "http".into(), Value::Namespace(Arc::new(NamespaceValue { env: http })));
}

fn http_request(method: &str, url: &str, body: Option<Vec<u8>>, headers: &Arc<RefCell<Vec<(Value, Value)>>>) -> Result<Value, RuntimeError> {
    let remainder = url.strip_prefix("http://").ok_or_else(|| RuntimeError::coded("E072", "http client currently requires an http:// URL"))?;
    let (authority, path) = remainder.split_once('/').map(|(host, path)| (host, format!("/{}", path))).unwrap_or((remainder, "/".into()));
    let (host, port) = authority.rsplit_once(':').filter(|(_, port)| port.parse::<u16>().is_ok())
        .map(|(host, port)| (host, port.parse::<u16>().unwrap()))
        .unwrap_or((authority, 80));
    let mut stream = std::net::TcpStream::connect((host, port)).map_err(|error| RuntimeError::coded("E072", format!("http request failed: {}", error)))?;
    use std::io::{Read, Write};
    let body = body.unwrap_or_default();
    let mut request = format!("{} {} HTTP/1.1\r\nHost: {}\r\nConnection: close\r\n", method, path, host);
    for (key, value) in headers.borrow().iter() {
        let (Value::String(key), Value::String(value)) = (key, value) else { return Err(RuntimeError::new("http headers must be string pairs")); };
        request.push_str(&format!("{}: {}\r\n", key, value));
    }
    if !body.is_empty() { request.push_str(&format!("Content-Length: {}\r\n", body.len())); }
    request.push_str("\r\n");
    stream.write_all(request.as_bytes()).and_then(|_| stream.write_all(&body))
        .map_err(|error| RuntimeError::coded("E072", format!("http request failed: {}", error)))?;
    let mut response = Vec::new();
    stream.read_to_end(&mut response).map_err(|error| RuntimeError::coded("E072", format!("http response failed: {}", error)))?;
    let response = String::from_utf8_lossy(&response);
    let (head, body) = response.split_once("\r\n\r\n").unwrap_or((&response, ""));
    let status = head.lines().next().and_then(|line| line.split_whitespace().nth(1)).and_then(|value| value.parse::<i64>().ok()).unwrap_or(-1);
    let mut response_headers = Vec::new();
    for line in head.lines().skip(1) {
        if let Some((key, value)) = line.split_once(':') { response_headers.push((Value::String(key.trim().into()), Value::String(value.trim().into()))); }
    }
    use std::cmp::Ordering;
    response_headers.sort_by(|a, b| { if map_key_less(&a.0, &b.0) { Ordering::Less } else if map_key_less(&b.0, &a.0) { Ordering::Greater } else { Ordering::Equal } });
    // Keys are inserted through map_insert_sorted so the result map keeps its
    // canonical sorted-key invariant (body, headers, status).
    let mut result = Vec::new();
    map_insert_sorted(&mut result, Value::String("status".into()), Value::Int(status));
    map_insert_sorted(&mut result, Value::String("body".into()), Value::String(body.into()));
    map_insert_sorted(&mut result, Value::String("headers".into()), Value::Map(Arc::new(RefCell::new(response_headers))));
    Ok(Value::Map(Arc::new(RefCell::new(result))))
}

fn install_file(env: &EnvRef) {
    let file = module(env);
    Env::define(&file, "read".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.read expects one string")); }; std::fs::read_to_string(path).map(Value::String).map_err(|error| RuntimeError::coded("E072", error.to_string())) })));
    Env::define(&file, "write".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path), Value::String(contents)] = args.as_slice() else { return Err(RuntimeError::new("file.write expects path and contents")); }; std::fs::write(path, contents).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "append".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path), Value::String(contents)] = args.as_slice() else { return Err(RuntimeError::new("file.append expects path and contents")); }; use std::io::Write; let mut handle = std::fs::OpenOptions::new().append(true).create(true).open(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; handle.write_all(contents.as_bytes()).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "delete".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.delete expects one string")); }; if std::fs::remove_file(path).is_err() { std::fs::remove_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; } Ok(Value::Null) })));
    Env::define(&file, "exists".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.exists expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).exists())) })));
    Env::define(&file, "list".into(), Value::Builtin(Arc::new(|args| {
        let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.list expects one string")); };
        let mut entries = std::fs::read_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?
            .map(|entry| entry.map(|entry| Value::String(entry.file_name().to_string_lossy().into_owned())))
            .collect::<Result<Vec<_>, _>>().map_err(|error| RuntimeError::coded("E072", error.to_string()))?;
        entries.sort_by_key(|entry| entry.to_string());
        Ok(Value::List(Arc::new(RefCell::new(entries))))
    })));
    Env::define(&file, "isFile".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.isFile expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).is_file())) })));
    Env::define(&file, "isDir".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.isDir expects one string")); }; Ok(Value::Bool(std::path::Path::new(path).is_dir())) })));
    Env::define(&file, "mkdir".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.mkdir expects one string")); }; std::fs::create_dir_all(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "size".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.size expects one string")); }; Ok(Value::Int(std::fs::metadata(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?.len() as i64)) })));
    Env::define(&file, "rename".into(), Value::Builtin(Arc::new(|args| { let [Value::String(from), Value::String(to)] = args.as_slice() else { return Err(RuntimeError::new("file.rename expects two strings")); }; std::fs::rename(from, to).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::Null) })));
    Env::define(&file, "remove".into(), Value::Builtin(Arc::new(|args| { let [Value::String(path)] = args.as_slice() else { return Err(RuntimeError::new("file.remove expects one string")); }; if std::fs::remove_file(path).is_err() { std::fs::remove_dir(path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; } Ok(Value::Null) })));
    Env::define(&file, "temp".into(), Value::Builtin(Arc::new(|args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("file.temp expects one string")); }; let path = unique_temp_path(prefix); std::fs::File::create(&path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::String(path.to_string_lossy().into())) })));
    Env::define(&file, "tempDir".into(), Value::Builtin(Arc::new(|args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("file.tempDir expects one string")); }; let path = unique_temp_path(prefix); std::fs::create_dir(&path).map_err(|error| RuntimeError::coded("E072", error.to_string()))?; Ok(Value::String(path.to_string_lossy().into())) })));
    Env::define(env, "file".into(), Value::Namespace(Arc::new(NamespaceValue { env: file })));
}

fn install_string(env: &EnvRef) {
    let string = module(env);
    Env::define(&string, "len".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.len expects one string")); }; Ok(Value::Int(value.chars().count() as i64)) })));
    Env::define(&string, "byteLength".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.byteLength expects one string")); }; Ok(Value::Int(value.len() as i64)) })));
    Env::define(&string, "charAt".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), index] = args.as_slice() else { return Err(RuntimeError::new("string.charAt expects string and index")); }; let index = integer_value(index)?; if index < 0 { return Ok(Value::Null); } Ok(value.chars().nth(index as usize).map(Value::Char).unwrap_or(Value::Null)) })));
    Env::define(&string, "substring".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), start, end] = args.as_slice() else { return Err(RuntimeError::new("string.substring expects string and two indexes")); }; let start = integer_value(start)?.max(0) as usize; let end = integer_value(end)?.max(start as i64) as usize; Ok(Value::String(value.chars().skip(start).take(end.saturating_sub(start)).collect())) })));
    Env::define(&string, "contains".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("string.contains expects two strings")); }; Ok(Value::Bool(value.contains(needle))) })));
    Env::define(&string, "startsWith".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("string.startsWith expects two strings")); }; Ok(Value::Bool(value.starts_with(prefix))) })));
    Env::define(&string, "endsWith".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), Value::String(suffix)] = args.as_slice() else { return Err(RuntimeError::new("string.endsWith expects two strings")); }; Ok(Value::Bool(value.ends_with(suffix))) })));
    Env::define(&string, "indexOf".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("string.indexOf expects two strings")); }; Ok(Value::Int(value.find(needle).map(|index| value[..index].chars().count() as i64).unwrap_or(-1))) })));
    Env::define(&string, "toUpper".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.toUpper expects one string")); }; Ok(Value::String(value.to_uppercase())) })));
    Env::define(&string, "toLower".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.toLower expects one string")); }; Ok(Value::String(value.to_lowercase())) })));
    Env::define(&string, "trim".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value)] = args.as_slice() else { return Err(RuntimeError::new("string.trim expects one string")); }; Ok(Value::String(value.trim().into())) })));
    Env::define(&string, "split".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("string.split expects two strings")); }; Ok(Value::List(Arc::new(RefCell::new(value.split(separator).map(|part| Value::String(part.into())).collect())))) })));
    Env::define(&string, "join".into(), Value::Builtin(Arc::new(|args| { let [Value::List(values), Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("string.join expects list and separator")); }; Ok(Value::String(values.borrow().iter().map(ToString::to_string).collect::<Vec<_>>().join(separator))) })));
    Env::define(&string, "repeat".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), count] = args.as_slice() else { return Err(RuntimeError::new("string.repeat expects string and count")); }; Ok(Value::String(value.repeat(integer_value(count)?.max(0) as usize))) })));
    Env::define(&string, "padStart".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), width, Value::String(fill)] = args.as_slice() else { return Err(RuntimeError::new("string.padStart expects string, width, fill")); }; Ok(Value::String(pad_string(value, integer_value(width)?, fill, true))) })));
    Env::define(&string, "padEnd".into(), Value::Builtin(Arc::new(|args| { let [Value::String(value), width, Value::String(fill)] = args.as_slice() else { return Err(RuntimeError::new("string.padEnd expects string, width, fill")); }; Ok(Value::String(pad_string(value, integer_value(width)?, fill, false))) })));
    let converter = Arc::new(|args: Vec<Value>| { if args.len() != 1 { return Err(RuntimeError::new("string expects 1 argument")); } Ok(Value::String(args[0].to_string())) });
    Env::define(env, "string".into(), Value::Module(Arc::new(ModuleValue { env: string, callable: Some(converter) })));
}

fn pad_string(value: &str, width: i64, fill: &str, start: bool) -> String {
    let width = width.max(0) as usize; let length = value.chars().count(); if length >= width || fill.is_empty() { return value.into(); }
    let needed = width - length; let padding: String = fill.chars().cycle().take(needed).collect(); if start { format!("{}{}", padding, value) } else { format!("{}{}", value, padding) }
}

fn unique_temp_path(prefix: &str) -> std::path::PathBuf {
    let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
    std::env::temp_dir().join(format!("{}{}-{}", prefix, std::process::id(), stamp))
}

fn type_name(value: &Value) -> String { match value { Value::Null => "null".into(), Value::Bool(_) => "Bool".into(), Value::Byte(_) => "Byte".into(), Value::Int(_) => "Int".into(), Value::Float(_) => "Float".into(), Value::Char(_) => "Char".into(), Value::String(_) => "String".into(), Value::Regex(_) => "Regex".into(), Value::List(_) => "List".into(), Value::Stack(_) => "Stack".into(), Value::Map(_) => "Map".into(), Value::Struct(value) => value.borrow().definition.name.clone(), Value::StructType(value) => value.name.clone(), Value::Enum(value) => value.definition.name.clone(), Value::EnumType(value) => value.name.clone(), Value::Namespace(_) | Value::Module(_) => "namespace".into(), Value::Exception(_) => "Exception".into(), Value::Function(_) | Value::Builtin(_) => "Func".into(), Value::Thread(_) => "Thread".into(), Value::Mutex(_) => "Mutex".into(), Value::Semaphore(_) => "Semaphore".into(), Value::Process(_) => "Process".into(), Value::InStream(_) => "InStream".into(), Value::OutStream(_) => "OutStream".into(), Value::ThreadDef(_) => "ThreadDef".into(), Value::ProcessDef(_, _) => "ProcessDef".into() } }
fn truthy(value: &Value) -> bool { match value { Value::Null => false, Value::Bool(v) => *v, Value::Byte(v) => *v != 0, Value::Int(v) => *v != 0, Value::Float(v) => *v != 0.0, Value::String(v) => !v.is_empty(), Value::List(v) | Value::Stack(v) => !v.borrow().is_empty(), Value::Map(v) => !v.borrow().is_empty(), _ => true } }

fn lookup_path(env: &EnvRef, path: &str) -> Option<Value> {
    let mut parts = path.split('.'); let first = parts.next()?; let mut value = Env::get(env, first)?;
    for part in parts { value = member(value, part, env).ok()?; }
    Some(value)
}

fn call(callee: Value, args: Vec<Value>) -> Result<Value, RuntimeError> {
    match callee {
        Value::Builtin(function) => {
            // Natives are leaf work: run them with the heap lock released so
            // blocking natives (join, readLine, sleep, process control) yield
            // to other workers.
            let guard = begin_native_call();
            let result = function(args);
            drop(guard);
            result
        }
        Value::Module(module) if module.callable.is_some() => {
            let guard = begin_native_call();
            let result = (module.callable.as_ref().unwrap())(args);
            drop(guard);
            result
        }
        Value::Function(function) => {
            let code = function.bytecode.clone().ok_or_else(|| RuntimeError::new("internal error: function body was not compiled to bytecode"))?;
            // User code always runs under the heap lock; re-acquire it when a
            // builtin invokes a closure (list.map, sort comparators, ...).
            let guard = begin_user_call();
            let result = bc_call(function.clone(), code, args);
            drop(guard);
            result
        }
        Value::Null => Err(RuntimeError::coded("E031", "null reference")),
        _ => Err(RuntimeError::coded("E068", "value is not callable")),
    }
}

// builtin_compare mirrors the Python reference's _builtin_compare: chars by
// code point, strings lexicographically, numbers (Byte/Int/Float) numerically
// across kinds; anything else is a "cannot compare" error.
fn builtin_compare(a: &Value, b: &Value) -> Result<i64, RuntimeError> {
    let num = |v: &Value| -> Option<f64> { match v { Value::Byte(x) => Some(*x as f64), Value::Int(x) => Some(*x as f64), Value::Float(x) => Some(*x), _ => None } };
    match (a, b) {
        (Value::Char(x), Value::Char(y)) => {
            let (xa, ya) = (*x as u32, *y as u32);
            Ok(if xa < ya { -1 } else if xa > ya { 1 } else { 0 })
        }
        (Value::String(x), Value::String(y)) => Ok(if x < y { -1 } else if x > y { 1 } else { 0 }),
        _ => {
            let av = num(a).ok_or_else(|| RuntimeError::new(format!("cannot compare {} and {}", type_name(a), type_name(b))))?;
            let bv = num(b).ok_or_else(|| RuntimeError::new(format!("cannot compare {} and {}", type_name(a), type_name(b))))?;
            Ok(if av < bv { -1 } else if av > bv { 1 } else { 0 })
        }
    }
}

// stable_hash mirrors the Python reference's _stable_hash: SHA-256 of
// "TypeName:stringForm", first 8 bytes big-endian, top bit cleared.
fn stable_hash(v: &Value) -> i64 {
    let data = format!("{}:{}", type_name(v), v.to_string());
    let digest = Sha256::digest(data.as_bytes());
    let mut h: i64 = 0;
    for byte in &digest[..8] { h = (h << 8) | *byte as i64; }
    h & 0x7FFFFFFFFFFFFFFF
}

fn member(object: Value, name: &str, env: &EnvRef) -> Result<Value, RuntimeError> {
    let tname = type_name(&object);
    match (object, name) {
        (Value::Byte(x), "abs") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("abs expects no arguments")); } Ok(Value::Byte(x)) }))),
        (Value::Int(x), "abs") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("abs expects no arguments")); } Ok(Value::Int(x.abs())) }))),
        (Value::Float(x), "abs") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("abs expects no arguments")); } Ok(Value::Float(x.abs())) }))),
        (a @ (Value::Byte(_) | Value::Int(_) | Value::Float(_) | Value::Char(_) | Value::String(_)), "compare") => {
            let self_value = a;
            Ok(Value::Builtin(Arc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("compare expects one argument")); }; Ok(Value::Int(builtin_compare(&self_value, other)?)) })))
        }
        (a @ (Value::Byte(_) | Value::Int(_) | Value::Float(_) | Value::Char(_) | Value::Bool(_) | Value::String(_)), "equals") => {
            let self_value = a;
            Ok(Value::Builtin(Arc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("equals expects one argument")); }; Ok(Value::Bool(self_value == *other)) })))
        }
        (a @ (Value::Byte(_) | Value::Int(_) | Value::Float(_) | Value::Char(_) | Value::String(_)), "hash") => {
            let self_value = a;
            Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("hash expects no arguments")); } Ok(Value::Int(stable_hash(&self_value))) })))
        }
        (Value::Bool(x), "hash") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("hash expects no arguments")); } Ok(Value::Int(if x { 1 } else { 0 })) }))),
        (Value::String(text), name) => {
            let text = Arc::new(text);
            match name {
                "string" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String((*text).clone())) }))),
                "equals" => Ok(Value::Builtin(Arc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("equals expects one argument")); }; Ok(Value::Bool(Value::String((*text).clone()) == *other)) }))),
                "len" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(text.chars().count() as i64)) }))),
                "byteLength" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("byteLength expects no arguments")); } Ok(Value::Int(text.len() as i64)) }))),
                "charAt" => Ok(Value::Builtin(Arc::new(move |args| { let [index] = args.as_slice() else { return Err(RuntimeError::new("charAt expects one index")); }; let index = integer_value(index)?; Ok(if index < 0 { Value::Null } else { text.chars().nth(index as usize).map(Value::Char).unwrap_or(Value::Null) }) }))),
                "substring" => Ok(Value::Builtin(Arc::new(move |args| { let [start, end] = args.as_slice() else { return Err(RuntimeError::new("substring expects two indexes")); }; let start = integer_value(start)?.max(0) as usize; let end = integer_value(end)?.max(start as i64) as usize; Ok(Value::String(text.chars().skip(start).take(end.saturating_sub(start)).collect())) }))),
                "contains" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("contains expects one string")); }; Ok(Value::Bool(text.contains(needle))) }))),
                "startsWith" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(prefix)] = args.as_slice() else { return Err(RuntimeError::new("startsWith expects one string")); }; Ok(Value::Bool(text.starts_with(prefix))) }))),
                "endsWith" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(suffix)] = args.as_slice() else { return Err(RuntimeError::new("endsWith expects one string")); }; Ok(Value::Bool(text.ends_with(suffix))) }))),
                "indexOf" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(needle)] = args.as_slice() else { return Err(RuntimeError::new("indexOf expects one string")); }; Ok(Value::Int(text.find(needle).map(|index| text[..index].chars().count() as i64).unwrap_or(-1))) }))),
                "trim" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("trim expects no arguments")); }; Ok(Value::String(text.trim().into())) }))),
                "toUpper" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("toUpper expects no arguments")); }; Ok(Value::String(text.to_uppercase())) }))),
                "toLower" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("toLower expects no arguments")); }; Ok(Value::String(text.to_lowercase())) }))),
                "split" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(separator)] = args.as_slice() else { return Err(RuntimeError::new("split expects one string")); }; Ok(Value::List(Arc::new(RefCell::new(text.split(separator).map(|part| Value::String(part.into())).collect())))) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::List(items), name) => {
            match name {
                "iterator" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("iterator expects no arguments")); } Ok(Value::List(Arc::new(RefCell::new(items.borrow().clone())))) }))),
                "string" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(items.borrow().iter().map(ToString::to_string).collect::<Vec<_>>().join(", "))) }))),
                "len" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
                "contains" => Ok(Value::Builtin(Arc::new(move |args| { let [needle] = args.as_slice() else { return Err(RuntimeError::new("contains expects one argument")); }; Ok(Value::Bool(items.borrow().iter().any(|item| item == needle))) }))),
                "first" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("first expects no arguments")); } Ok(items.borrow().first().cloned().unwrap_or(Value::Null)) }))),
                "last" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("last expects no arguments")); } Ok(items.borrow().last().cloned().unwrap_or(Value::Null)) }))),
                "reverse" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("reverse expects no arguments")); } let mut result = items.borrow().clone(); result.reverse(); Ok(Value::List(Arc::new(RefCell::new(result)))) }))),
                "map" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("map expects one function")); }; let mut result = Vec::new(); for item in items.borrow().iter().map(copy_value) { result.push(call(function.clone(), vec![item])?); } Ok(Value::List(Arc::new(RefCell::new(result)))) }))),
                "filter" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("filter expects one function")); }; let mut result = Vec::new(); for item in items.borrow().iter().map(copy_value) { if truthy(&call(function.clone(), vec![item.clone()])?) { result.push(item); } } Ok(Value::List(Arc::new(RefCell::new(result)))) }))),
                "fold" => Ok(Value::Builtin(Arc::new(move |args| { let [initial, function] = args.as_slice() else { return Err(RuntimeError::new("fold expects an initial value and function")); }; let mut result = initial.clone(); for item in items.borrow().iter().map(copy_value) { result = call(function.clone(), vec![result, item])?; } Ok(result) }))),
                "reduce" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("reduce expects one function")); }; let values = items.borrow().iter().map(copy_value).collect::<Vec<_>>(); let mut values = values.into_iter(); let Some(mut result) = values.next() else { return Err(RuntimeError::coded("E072", "reduce of empty list")); }; for item in values { result = call(function.clone(), vec![result, item])?; } Ok(result) }))),
                "find" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("find expects one function")); }; for item in items.borrow().iter().map(copy_value) { if truthy(&call(function.clone(), vec![item.clone()])?) { return Ok(item); } } Ok(Value::Null) }))),
                "any" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("any expects one function")); }; for item in items.borrow().iter().map(copy_value) { if truthy(&call(function.clone(), vec![item])?) { return Ok(Value::Bool(true)); } } Ok(Value::Bool(false)) }))),
                "all" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("all expects one function")); }; for item in items.borrow().iter().map(copy_value) { if !truthy(&call(function.clone(), vec![item])?) { return Ok(Value::Bool(false)); } } Ok(Value::Bool(true)) }))),
                "sort" => Ok(Value::Builtin(Arc::new(move |args| { let [function] = args.as_slice() else { return Err(RuntimeError::new("sort expects one function")); }; let mut result = items.borrow().clone(); for i in 1..result.len() { let mut j = i; while j > 0 { let order = integer_value(&call(function.clone(), vec![copy_value(&result[j - 1]), copy_value(&result[j])])?)?; if order <= 0 { break; } result.swap(j - 1, j); j -= 1; } } Ok(Value::List(Arc::new(RefCell::new(result)))) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Stack(items), name) => {
            match name {
                "len" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
                "isEmpty" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("isEmpty expects no arguments")); } Ok(Value::Bool(items.borrow().is_empty())) }))),
                "push" => Ok(Value::Builtin(Arc::new(move |args| { let [value] = args.as_slice() else { return Err(RuntimeError::new("push expects one argument")); }; items.borrow_mut().push(value.clone()); Ok(Value::Null) }))),
                "pop" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("pop expects no arguments")); } items.borrow_mut().pop().ok_or_else(|| RuntimeError::new("pop from empty stack")) }))),
                "peek" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("peek expects no arguments")); } items.borrow().last().cloned().ok_or_else(|| RuntimeError::new("peek from empty stack")) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Thread(thread), name) => {
            match name {
                "start" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("start expects no arguments")); } thread_start(&thread)?; Ok(Value::Null) }))),
                "join" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("join expects no arguments")); } Ok(Value::Int(thread.join()?)) }))),
                "status" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("status expects no arguments")); } thread.require_started()?; Ok(match thread.status() { Some(code) => Value::Int(code), None => Value::Null }) }))),
                "is_done" | "isDone" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("is_done expects no arguments")); } thread.require_started()?; Ok(Value::Bool(thread.is_done())) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Mutex(mutex), name) => {
            match name {
                "lock" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("lock expects no arguments")); } mutex.lock()?; Ok(Value::Null) }))),
                "unlock" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("unlock expects no arguments")); } mutex.unlock()?; Ok(Value::Null) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Semaphore(sem), name) => {
            match name {
                "acquire" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("acquire expects no arguments")); } sem.acquire()?; Ok(Value::Null) }))),
                "release" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("release expects no arguments")); } sem.release()?; Ok(Value::Null) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Process(process), name) => {
            match name {
                "start" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("start expects no arguments")); } process_start(&process)?; Ok(Value::Null) }))),
                "join" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("join expects no arguments")); } process.require_started()?; Ok(Value::Int(process.join())) }))),
                "status" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("status expects no arguments")); } process.require_started()?; Ok(match process.status() { Some(code) => Value::Int(code), None => Value::Null }) }))),
                "is_done" | "isDone" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("is_done expects no arguments")); } process.require_started()?; Ok(Value::Bool(process.is_done())) }))),
                "terminate" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("terminate expects no arguments")); } process.require_started()?; process.terminate()?; Ok(Value::Null) }))),
                "stdin" => Ok(Value::OutStream(process.clone())),
                "stdout" => Ok(Value::InStream(process.stdout.clone())),
                "stderr" => Ok(Value::InStream(process.stderr.clone())),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::InStream(stream), name) => {
            match name {
                "readLine" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("readLine expects no arguments")); } Ok(match stream.read_line()? { Some(line) => Value::String(line), None => Value::Null }) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::OutStream(process), name) => {
            match name {
                "write" => Ok(Value::Builtin(Arc::new(move |args| { let [Value::String(text)] = args.as_slice() else { return Err(RuntimeError::new("write expects one string")); }; process.write_stdin(text)?; Ok(Value::Null) }))),
                "close" => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("close expects no arguments")); } process.close_stdin(); Ok(Value::Null) }))),
                _ => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
            }
        }
        (Value::Map(items), "len") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("len expects no arguments")); } Ok(Value::Int(items.borrow().len() as i64)) }))),
        (Value::Map(items), "contains") => Ok(Value::Builtin(Arc::new(move |args| { let [needle] = args.as_slice() else { return Err(RuntimeError::new("contains expects one argument")); }; Ok(Value::Bool(items.borrow().iter().any(|(key, _)| key == needle))) }))),
        (Value::Function(function), "string") => {
            let name = function.function.name.clone();
            Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(if name == "<closure>" { "<closure>".into() } else { format!("<function {}>", name) })) })))
        }
        (Value::Function(function), "equals") => {
            let expected = Value::Function(function);
            Ok(Value::Builtin(Arc::new(move |args| { let [other] = args.as_slice() else { return Err(RuntimeError::new("equals expects one argument")); }; Ok(Value::Bool(expected == *other)) })))
        }
        (Value::Builtin(function), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } let _ = &function; Ok(Value::String("<function>".into())) }))),
        (Value::Int(value), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Byte(value), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Float(value), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Bool(value), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Char(value), "string") => Ok(Value::Builtin(Arc::new(move |args| { if !args.is_empty() { return Err(RuntimeError::new("string expects no arguments")); } Ok(Value::String(value.to_string())) }))),
        (Value::Exception(error), "message") => Ok(Value::String(error.message)),
        (Value::Exception(error), "code") => Ok(Value::String(error.code)),
        (Value::Exception(_), "trace") => Ok(Value::String(String::new())),
        (Value::EnumType(definition), member_name) => {
            let member = definition.members.get(member_name).ok_or_else(|| RuntimeError::new(format!("unknown enum member {}", member_name)))?.clone();
            if member.payload_count == 0 {
                Ok(Value::Enum(Arc::new(EnumValue { unresolved: definition.type_param_count > 0, definition, member: member_name.into(), payload: Vec::new() })))
            } else {
                let definition = definition.clone(); let member_name = member_name.to_string();
                Ok(Value::Builtin(Arc::new(move |args| {
                    if args.len() != member.payload_count { return Err(RuntimeError::coded("E069", "enum payload arity mismatch")); }
                    Ok(Value::Enum(Arc::new(EnumValue { unresolved: definition.type_param_count > args.len(), definition: definition.clone(), member: member_name.clone(), payload: args })))
                })))
            }
        }
        (Value::Enum(value), "member") => Ok(Value::String(value.member.clone())),
        (Value::Namespace(namespace), name) => Env::get(&namespace.env, name).ok_or_else(|| RuntimeError::new(format!("unknown namespace member {}", name))),
        (Value::Module(module), name) => Env::get(&module.env, name).ok_or_else(|| RuntimeError::new(format!("unknown module member {}", name))),
        (Value::Struct(value), name) => {
            let definition = value.borrow().definition.clone();
            let internal_receiver = matches!(Env::get(env, "self"), Some(Value::Struct(receiver)) if Arc::ptr_eq(&receiver, &value));
            let same_package = env.borrow().package == definition.owner_package;
            if let Some(field) = value.borrow().fields.get(name).cloned() {
                if !definition.field_public.get(name).copied().unwrap_or(false) && !same_package && !internal_receiver {
                    return Err(RuntimeError::coded("E070", format!("field '{}' is private", name)));
                }
                return Ok(copy_value(&field));
            }
            let method = definition.methods.get(name).cloned().ok_or_else(|| RuntimeError::new(format!("type {} has no member {}", tname, name)))?;
            if !definition.method_public.get(name).copied().unwrap_or(false) && !same_package && !internal_receiver {
                return Err(RuntimeError::coded("E070", format!("method '{}' is private", name)));
            }
            Ok(Value::Function(Arc::new(Callable { function: method.clone(), closure: env.clone(), receiver: Some(Value::Struct(value)), bytecode: Some(bc_compile_block(method.body.as_ref())) })))
        }
        (Value::StructType(definition), name) => {
            let same_package = env.borrow().package == definition.owner_package;
            let method = definition.methods.get(name).cloned().filter(|method| method.static_).ok_or_else(|| RuntimeError::new(format!("type '{}' has no associated function '{}'", definition.name, name)))?;
            if !definition.method_public.get(name).copied().unwrap_or(false) && !same_package {
                return Err(RuntimeError::coded("E070", format!("associated function '{}' of '{}' is private", name, definition.name)));
            }
            let bytecode = bc_compile_block(method.body.as_ref());
            Ok(Value::Function(Arc::new(Callable { function: method, closure: definition.package_env.clone(), receiver: None, bytecode: Some(bytecode) })))
        }
        (_, _) => Err(RuntimeError::new(format!("type {} has no member {}", tname, name))),
    }
}

fn match_pattern(pattern: &Expr, value: &Value) -> Result<Option<HashMap<String, Value>>, RuntimeError> {
    let mut bindings = HashMap::new();
    if match_pattern_into(pattern, value, &mut bindings)? { Ok(Some(bindings)) } else { Ok(None) }
}

fn match_pattern_into(pattern: &Expr, value: &Value, bindings: &mut HashMap<String, Value>) -> Result<bool, RuntimeError> {
    match pattern {
        Expr::Name { name, .. } if name == "_" => Ok(true),
        Expr::Name { name, .. } => { bindings.insert(name.clone(), value.clone()); Ok(true) }
        Expr::Null => Ok(matches!(value, Value::Null)),
        // Numeric literal cases match any numerically equal numeric value
        // (the frozen contract's wider/narrower numeric rule).
        Expr::Int(expected) => Ok(numeric_literal_matches(value, &Value::Int(*expected))),
        Expr::Float(expected) => Ok(numeric_literal_matches(value, &Value::Float(*expected))),
        Expr::Bool(expected) => Ok(matches!(value, Value::Bool(actual) if actual == expected)),
        Expr::Char(expected) => Ok(matches!(value, Value::Char(actual) if actual == expected)),
        Expr::String(expected) => Ok(matches!(value, Value::String(actual) if actual == expected)),
        Expr::Call { callee, args, .. } if is_regex_new_call(callee) => {
            if args.len() != 1 { return Ok(false); }
            let Expr::String(pattern) = &args[0].expr else { return Ok(false); };
            let Value::String(actual) = value else { return Ok(false); };
            Ok(regex::Regex::new(pattern).map(|regex| regex.is_match(actual)).unwrap_or(false))
        }
        Expr::Member { object, name, .. } => match value {
            Value::Enum(actual) => Ok(enum_pattern_name(object, name) && actual.member == *name && actual.payload.is_empty()),
            _ => Ok(false),
        },
        Expr::Call { callee, args, .. } => {
            let (enum_expr, case_name) = match callee.as_ref() {
                Expr::Member { object, name, .. } => (Some(object.as_ref()), name.as_str()),
                Expr::Name { name, .. } => (None, name.as_str()),
                _ => return Ok(false),
            };
            let Value::Enum(actual) = value else { return Ok(false) };
            if actual.member != case_name || !enum_expr.map(|expr| enum_pattern_type(expr, &actual.definition.name)).unwrap_or(true) || actual.payload.len() != args.len() { return Ok(false); }
            for (arg, actual_value) in args.iter().map(|arg| &arg.expr).zip(actual.payload.iter()) {
                if !match_pattern_into(arg, actual_value, bindings)? { return Ok(false); }
            }
            Ok(true)
        }
        _ => Ok(false),
    }
}

fn is_regex_new_call(callee: &Expr) -> bool {
    match callee {
        Expr::Member { object, name, .. } => name == "new" && matches!(object.as_ref(), Expr::Name { name, .. } if name == "Regex"),
        _ => false,
    }
}

fn enum_pattern_name(object: &Expr, case_name: &str) -> bool {
    match object { Expr::Name { name, .. } => !name.is_empty() && case_name != name, Expr::Member { .. } => true, _ => false }
}

fn numeric_literal_matches(value: &Value, expected: &Value) -> bool {
    match (value, expected) {
        (Value::Byte(a), Value::Byte(b)) => a == b,
        (Value::Byte(a), Value::Int(b)) | (Value::Int(b), Value::Byte(a)) => i64::from(*a) == *b,
        (Value::Int(a), Value::Int(b)) => a == b,
        (Value::Byte(a), Value::Float(b)) | (Value::Float(b), Value::Byte(a)) => i64::from(*a) as f64 == *b,
        (Value::Int(a), Value::Float(b)) | (Value::Float(b), Value::Int(a)) => *a as f64 == *b,
        (Value::Float(a), Value::Float(b)) => a == b,
        _ => false,
    }
}

fn enum_pattern_type(object: &Expr, enum_name: &str) -> bool {
    match object { Expr::Name { name, .. } => name.rsplit('.').next() == Some(enum_name.rsplit('.').next().unwrap_or(enum_name)), _ => true }
}

fn binary(left: &Value, op: &str, right: &Value) -> Result<Value, RuntimeError> {
    match op {
        "==" => Ok(Value::Bool(left == right)), "!=" => Ok(Value::Bool(left != right)), ".." => Ok(Value::String(format!("{}{}", left, right))),
        "+" | "-" | "*" | "/" | "%" => numeric(left, op, right),
        "<" | "<=" | ">" | ">=" => compare(left, op, right),
        "|" | "&" | "^" | "<<" | ">>" => integer(left, op, right), "??" => if matches!(left, Value::Null) { Ok(right.clone()) } else { Ok(left.clone()) },
        _ => Err(RuntimeError::new(format!("unknown binary operator {}", op))),
    }
}
fn numeric(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> {
    let float = matches!((a,b), (Value::Float(_), _) | (_, Value::Float(_)));
    if float {
        let (a,b) = (number(a)?,number(b)?); if matches!(op, "/" | "%") && b == 0.0 { return Err(RuntimeError::coded("E031", "division by zero")); }
        Ok(Value::Float(match op { "+"=>a+b,"-"=>a-b,"*"=>a*b,"/"=>a/b,"%"=>a%b,_=>0.0 }))
    } else {
        let (a,b) = (integer_value(a)?,integer_value(b)?); if matches!(op, "/" | "%") && b == 0 { return Err(RuntimeError::coded("E031", "division by zero")); }
        Ok(Value::Int(match op { "+"=>a+b,"-"=>a-b,"*"=>a*b,"/"=>a/b,"%"=>a%b,_=>0 }))
    }
}
fn compare(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> { let (a,b) = (number(a)?,number(b)?); Ok(Value::Bool(match op { "<"=>a<b,"<="=>a<=b,">"=>a>b,">="=>a>=b,_=>false })) }
fn integer(a: &Value, op: &str, b: &Value) -> Result<Value, RuntimeError> { let (a,b) = (integer_value(a)?,integer_value(b)?); Ok(Value::Int(match op { "|"=>a|b,"&"=>a&b,"^"=>a^b,"<<"=>a<<b,">>"=>a>>b,_=>0 })) }
fn integer_value(value: &Value) -> Result<i64, RuntimeError> { match value { Value::Byte(v) => Ok(*v as i64), Value::Int(v) => Ok(*v), _ => Err(RuntimeError::new("integer operand required")) } }
fn number(value: &Value) -> Result<f64, RuntimeError> { match value { Value::Byte(v) => Ok(*v as f64), Value::Int(v) => Ok(*v as f64), Value::Float(v) => Ok(*v), Value::Char(v) => Ok(*v as u32 as f64), _ => Err(RuntimeError::new("numeric operand required")) } }

// Semantic AST bytecode. The dispatcher below owns expression evaluation and
// control flow; it does not invoke the tree-walking statement evaluator.
#[derive(Clone)]
enum BcInstr {
    Push(Value), Load(String, Vec<TypeRef>), Store(String, Option<TypeRef>), Pop,
    Unary(String), Binary(String), Short(String, Arc<BcCode>),
    Call(Vec<bool>, Vec<TypeRef>, bool), Member(String, Vec<TypeRef>), Index,
    List(usize), Map(usize), Struct(String, Vec<String>), Closure(Function),
    AssignName(String), AssignMember(Arc<BcCode>, String), AssignIndex(Arc<BcCode>, Arc<BcCode>), Block(Arc<BcCode>),
    If(Arc<BcCode>, Option<Arc<BcCode>>), While(Arc<BcCode>, Arc<BcCode>),
    For(Vec<String>, Arc<BcCode>, Arc<BcCode>),
    Switch(Arc<BcCode>, Vec<(Option<Expr>, Arc<BcCode>)>),
    Try(Arc<BcCode>, Option<String>, Option<Arc<BcCode>>, Option<Arc<BcCode>>),
    Throw, Return, Break, Continue,
}

#[derive(Clone, Default)]
struct BcCode { instructions: Vec<BcInstr> }

fn bc_compile_block(block: Option<&Block>) -> Arc<BcCode> {
    let mut code = BcCode::default();
    if let Some(block) = block { for statement in &block.statements { bc_compile_stmt(&mut code, statement); } }
    Arc::new(code)
}

fn bc_compile_stmt(code: &mut BcCode, statement: &Stmt) {
    match statement {
        Stmt::Var { name, value, typ, .. } => { if let Some(value) = value { bc_compile_expr(code, value); } else { code.instructions.push(BcInstr::Push(Value::Null)); } code.instructions.push(BcInstr::Store(name.clone(), typ.clone())); }
        Stmt::Expr(expr) => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Pop); }
        Stmt::Return(expr) => { if let Some(expr) = expr { bc_compile_expr(code, expr); } else { code.instructions.push(BcInstr::Push(Value::Null)); } code.instructions.push(BcInstr::Return); }
        Stmt::Block(block) => code.instructions.push(BcInstr::Block(bc_compile_block(Some(block)))),
        Stmt::If { condition, then_block, else_branch } => {
            bc_compile_expr(code, condition);
            let else_code = else_branch.as_deref().map(|branch| { let mut child = BcCode::default(); bc_compile_stmt(&mut child, branch); Arc::new(child) });
            code.instructions.push(BcInstr::If(bc_compile_block(Some(then_block)), else_code));
        }
        Stmt::While { condition, body } => code.instructions.push(BcInstr::While(bc_compile_expr_code(condition), bc_compile_block(Some(body)))),
        Stmt::For { names, iterable, body } => code.instructions.push(BcInstr::For(names.clone(), bc_compile_expr_code(iterable), bc_compile_block(Some(body)))),
        Stmt::Switch { value, cases } => code.instructions.push(BcInstr::Switch(bc_compile_expr_code(value), cases.iter().map(|(pattern, body)| (pattern.clone(), bc_compile_block(Some(body)))).collect())),
        Stmt::Try { body, catch_name, catch, finally } => code.instructions.push(BcInstr::Try(bc_compile_block(Some(body)), catch_name.clone(), catch.as_ref().map(|body| bc_compile_block(Some(body))), finally.as_ref().map(|body| bc_compile_block(Some(body))))),
        Stmt::Throw(expr) => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Throw); }
        Stmt::Break => code.instructions.push(BcInstr::Break), Stmt::Continue => code.instructions.push(BcInstr::Continue),
    }
}

fn bc_compile_expr_code(expr: &Expr) -> Arc<BcCode> { let mut code = BcCode::default(); bc_compile_expr(&mut code, expr); Arc::new(code) }

fn bc_compile_expr(code: &mut BcCode, expr: &Expr) {
    match expr {
        Expr::Int(v) => code.instructions.push(BcInstr::Push(Value::Int(*v))), Expr::Float(v) => code.instructions.push(BcInstr::Push(Value::Float(*v))), Expr::Bool(v) => code.instructions.push(BcInstr::Push(Value::Bool(*v))), Expr::Char(v) => code.instructions.push(BcInstr::Push(Value::Char(*v))), Expr::String(v) => code.instructions.push(BcInstr::Push(Value::String(v.clone()))), Expr::Null => code.instructions.push(BcInstr::Push(Value::Null)),
        Expr::Name { name, type_args } => code.instructions.push(BcInstr::Load(name.clone(), type_args.clone())),
        Expr::Function { params, return_type, body } => code.instructions.push(BcInstr::Closure(Function { name: "<closure>".into(), public: false, mutating: false, static_: false, params: params.clone(), return_type: return_type.clone(), type_params: Vec::new(), body: Some(body.clone()) })),
        Expr::Unary { op, expr } => { bc_compile_expr(code, expr); code.instructions.push(BcInstr::Unary(op.clone())); }
        Expr::Binary { left, op, right } if op == "=" => {
            bc_compile_expr(code, right);
            match left.as_ref() {
                Expr::Name { name, .. } => code.instructions.push(BcInstr::AssignName(name.clone())),
                Expr::Member { object, name, .. } => code.instructions.push(BcInstr::AssignMember(bc_compile_expr_code(object), name.clone())),
                Expr::Index { object, index } => code.instructions.push(BcInstr::AssignIndex(bc_compile_expr_code(object), bc_compile_expr_code(index))),
                _ => code.instructions.push(BcInstr::Pop),
            }
        }
        Expr::Binary { left, op, right } if matches!(op.as_str(), "&&" | "||" | "??") => { bc_compile_expr(code, left); code.instructions.push(BcInstr::Short(op.clone(), bc_compile_expr_code(right))); }
        Expr::Binary { left, op, right } => { bc_compile_expr(code, left); bc_compile_expr(code, right); code.instructions.push(BcInstr::Binary(op.clone())); }
        Expr::Call { callee, args, type_args } => { bc_compile_expr(code, callee); for arg in args { bc_compile_expr(code, &arg.expr); } let explicit = if !type_args.is_empty() { type_args.clone() } else { match callee.as_ref() { Expr::Name { type_args, .. } | Expr::Member { type_args, .. } => type_args.clone(), _ => Vec::new() } }; code.instructions.push(BcInstr::Call(args.iter().map(|arg| arg.spread || matches!(arg.expr, Expr::Spread(_))).collect(), explicit, args.iter().any(|arg| matches!(arg.expr, Expr::Null)))); }
        Expr::Member { object, name, type_args } => { bc_compile_expr(code, object); code.instructions.push(BcInstr::Member(name.clone(), type_args.clone())); }
        Expr::Index { object, index } => { bc_compile_expr(code, object); bc_compile_expr(code, index); code.instructions.push(BcInstr::Index); }
        Expr::List(items) => { for item in items { bc_compile_expr(code, item); } code.instructions.push(BcInstr::List(items.len())); }
        Expr::Map(items) => { for (key, value) in items { bc_compile_expr(code, key); bc_compile_expr(code, value); } code.instructions.push(BcInstr::Map(items.len())); }
        Expr::Struct { name, fields, .. } => { for (_, value) in fields { bc_compile_expr(code, value); } code.instructions.push(BcInstr::Struct(name.clone(), fields.iter().map(|(name, _)| name.clone()).collect())); }
        Expr::Spread(inner) => bc_compile_expr(code, inner),
    }
}

fn install_bytecode_declarations(env: &EnvRef, program: &crate::semantic_ast::Program) {
    for declaration in &program.declarations {
        if let Decl::Function(function) = declaration {
            Env::define(env, function.name.clone(), Value::Function(Arc::new(Callable { function: function.clone(), closure: env.clone(), receiver: None, bytecode: Some(bc_compile_block(function.body.as_ref())) })));
        }
    }
}

fn execute_main_bytecode(global: &EnvRef) -> Result<i32, RuntimeError> {
    let main = Env::get(global, "main").ok_or_else(|| RuntimeError::new("entry function main not found"))?;
    match call(main, Vec::new())? { Value::Int(code) => Ok(code as i32), _ => Ok(0) }
}

fn bc_eval(code: &BcCode, env: &EnvRef) -> Result<Value, RuntimeError> {
    let mut stack = Vec::new();
    match bc_run(code, env, &mut stack)? { Flow::Normal => Ok(stack.pop().unwrap_or(Value::Null)), _ => Err(RuntimeError::new("invalid control flow in expression")) }
}

fn bc_run(code: &BcCode, env: &EnvRef, stack: &mut Vec<Value>) -> Result<Flow, RuntimeError> {
    for instruction in &code.instructions {
        match instruction {
            BcInstr::Push(value) => stack.push(value.clone()),
            BcInstr::Load(name, type_args) => {
                let mut value = lookup_path(env, name).ok_or_else(|| RuntimeError::new(format!("undefined name {}", name)))?;
                if !type_args.is_empty() {
                    if let Value::EnumType(definition) = value {
                        if definition.type_param_count != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", definition.name, definition.type_param_count, type_args.len()))); }
                        let mut definition = (*definition).clone(); definition.type_param_count = 0; value = Value::EnumType(Arc::new(definition));
                    }
                }
                stack.push(value);
            }
            BcInstr::Store(name, typ) => {
                let mut value = stack.pop().unwrap_or(Value::Null);
                if typ.as_ref().is_some_and(|typ| typ.name == "exception") {
                    if let Value::String(message) = value { value = Value::Exception(RuntimeError::new(message)); }
                }
                if let Value::Enum(enum_value) = &value {
                    if enum_value.unresolved {
                        let resolves = typ.as_ref().is_some_and(|typ| typ.name.rsplit('.').next() == Some(enum_value.definition.name.rsplit('.').next().unwrap_or(&enum_value.definition.name)) && typ.args.len() == enum_value.definition.type_param_count);
                        if resolves { let mut resolved = (**enum_value).clone(); resolved.unresolved = false; value = Value::Enum(Arc::new(resolved)); }
                        else if typ.as_ref().is_some_and(|typ| typ.name == "any") { return Err(RuntimeError::coded("E067", "cannot infer type parameter for enum; use explicit type arguments or annotate the value's type")); }
                    }
                }
                if let Value::Struct(struct_value) = &value {
                    if struct_value.borrow().unresolved {
                        let resolves = typ.as_ref().is_some_and(|typ| typ.name.rsplit('.').next() == Some(struct_value.borrow().definition.name.rsplit('.').next().unwrap_or(&struct_value.borrow().definition.name)) && typ.args.len() == struct_value.borrow().definition.type_param_count);
                        if resolves { let mut resolved = struct_value.borrow_mut(); resolved.unresolved = false; }
                        else if typ.as_ref().is_some_and(|typ| typ.name == "any") { return Err(RuntimeError::coded("E067", "cannot infer type parameter for struct; annotate the declaration or use explicit type arguments")); }
                    }
                }
                Env::define(env, name.clone(), copy_value(&value));
            }
            BcInstr::Pop => { stack.pop(); }
            BcInstr::Unary(op) => { let value = stack.pop().unwrap_or(Value::Null); stack.push(match (op.as_str(), value) { ("!", value) => Value::Bool(!truthy(&value)), ("-", Value::Int(v)) => Value::Int(-v), ("-", Value::Float(v)) => Value::Float(-v), ("~", Value::Int(v)) => Value::Int(!v), _ => return Err(RuntimeError::new("invalid unary operation")) }); }
            BcInstr::Binary(op) => { let right = stack.pop().unwrap_or(Value::Null); let left = stack.pop().unwrap_or(Value::Null); stack.push(binary(&left, op, &right)?); }
            BcInstr::Short(op, right) => {
                let left = stack.pop().unwrap_or(Value::Null);
                let value = match op.as_str() { "&&" => if truthy(&left) { Value::Bool(truthy(&bc_eval(right, env)?)) } else { Value::Bool(false) }, "||" => if truthy(&left) { Value::Bool(true) } else { Value::Bool(truthy(&bc_eval(right, env)?)) }, "??" => if matches!(left, Value::Null) { bc_eval(right, env)? } else { left }, _ => unreachable!() };
                stack.push(value);
            }
            BcInstr::Call(spreads, type_args, literal_null) => {
                let mut raw = Vec::with_capacity(spreads.len()); for _ in 0..spreads.len() { raw.push(stack.pop().unwrap_or(Value::Null)); } raw.reverse();
                let mut args = Vec::new(); for (value, spread) in raw.into_iter().zip(spreads) { if *spread { let Value::List(items) = value else { return Err(RuntimeError::new("spread value is not a list")); }; args.extend(items.borrow().iter().cloned()); } else { args.push(value); } }
                let callee = stack.pop().unwrap_or(Value::Null);
                if let Value::Function(function) = &callee {
                    if !type_args.is_empty() && type_args.len() != function.function.type_params.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", function.function.name, function.function.type_params.len(), type_args.len()))); }
                    if *literal_null && function.function.type_params.len() > type_args.len() { return Err(RuntimeError::coded("E067", "a null value cannot infer a generic type parameter")); }
                }
                stack.push(call(callee, args)?);
            }
            BcInstr::Member(name, type_args) => {
                let object = stack.pop().unwrap_or(Value::Null);
                let value = if !type_args.is_empty() {
                    if let Value::EnumType(definition) = object.clone() {
                        if definition.type_param_count != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", definition.name, definition.type_param_count, type_args.len()))); }
                        let mut definition = (*definition).clone(); definition.type_param_count = 0;
                        member(Value::EnumType(Arc::new(definition)), name, env)?
                    } else {
                        let value = member(object, name, env)?;
                        if let Value::Function(function) = &value {
                            if function.function.type_params.len() != type_args.len() { return Err(RuntimeError::coded("E067", format!("{} requires {} type argument(s), found {}", function.function.name, function.function.type_params.len(), type_args.len()))); }
                        }
                        value
                    }
                } else { member(object, name, env)? };
                stack.push(value);
            }
            BcInstr::Index => { let index = stack.pop().unwrap_or(Value::Null); let object = stack.pop().unwrap_or(Value::Null); stack.push(bc_index(object, index)?); }
            BcInstr::List(count) => { let mut values = Vec::with_capacity(*count); for _ in 0..*count { values.push(stack.pop().unwrap_or(Value::Null)); } values.reverse(); stack.push(Value::List(Arc::new(RefCell::new(values)))); }
            BcInstr::Map(count) => { let mut values = Vec::with_capacity(*count); for _ in 0..*count { let value = stack.pop().unwrap_or(Value::Null); let key = stack.pop().unwrap_or(Value::Null); values.push((key, value)); } values.reverse(); use std::cmp::Ordering; values.sort_by(|a, b| { if map_key_less(&a.0, &b.0) { Ordering::Less } else if map_key_less(&b.0, &a.0) { Ordering::Greater } else { Ordering::Equal } }); stack.push(Value::Map(Arc::new(RefCell::new(values)))); }
            BcInstr::Struct(name, fields) => {
                let mut values = HashMap::new(); for field in fields.iter().rev() { values.insert(field.clone(), stack.pop().unwrap_or(Value::Null)); }
                let local = name.rsplit('.').next().unwrap_or(name);
                if local == "ThreadDef" || local == "ProcessDef" {
                    // Built-in records (Phase 14): not user structs; fields are
                    // checked directly.
                    if local == "ThreadDef" {
                        if values.len() != 1 || !values.contains_key("body") { return Err(RuntimeError::new("struct literal for ThreadDef must initialize every field exactly once")); }
                        stack.push(Value::ThreadDef(Box::new(values.remove("body").unwrap())));
                    } else {
                        if values.len() != 2 || !values.contains_key("program") || !values.contains_key("args") { return Err(RuntimeError::new("struct literal for ProcessDef must initialize every field exactly once")); }
                        let program = match values.remove("program").unwrap() { Value::String(program) => program, _ => return Err(RuntimeError::coded("E066", "ProcessDef.program must be a String")) };
                        let arguments = match values.remove("args").unwrap() {
                            Value::List(items) => items.borrow().iter().cloned().map(|item| match item { Value::String(argument) => Ok(argument), _ => Err(RuntimeError::coded("E066", "ProcessDef.args must be a List<String>")) }).collect::<Result<Vec<_>, RuntimeError>>()?,
                            _ => return Err(RuntimeError::coded("E066", "ProcessDef.args must be a List<String>")),
                        };
                        stack.push(Value::ProcessDef(program, arguments));
                    }
                } else {
                    let definition = match lookup_path(env, name) { Some(Value::StructType(definition)) => definition, _ => return Err(RuntimeError::new(format!("unknown struct {}", name))) };
                    let unresolved = definition.type_param_count > 0 && values.values().any(|value| matches!(value, Value::Null));
                    stack.push(Value::Struct(Arc::new(RefCell::new(StructValue { definition, fields: values, unresolved }))));
                }
            }
            BcInstr::Closure(function) => stack.push(Value::Function(Arc::new(Callable { function: function.clone(), closure: env.clone(), receiver: None, bytecode: Some(bc_compile_block(function.body.as_ref())) }))),
            BcInstr::Block(block) => match bc_run(block, &Env::new(Some(env.clone())), stack)? { Flow::Normal => {}, flow => return Ok(flow) },
            BcInstr::AssignName(name) => { let value = stack.pop().unwrap_or(Value::Null); let stored = copy_value(&value); if !Env::set(env, name, stored) { return Err(RuntimeError::new(format!("undefined name {}", name))); } stack.push(value); }
            BcInstr::AssignMember(object_code, name) => { let value = stack.pop().unwrap_or(Value::Null); let object = bc_eval(object_code, env)?; match object { Value::Struct(target) => { target.borrow_mut().fields.insert(name.clone(), copy_value(&value)); let _ = Env::set(env, name, copy_value(&value)); stack.push(value); }, _ => return Err(RuntimeError::new("assignment target is not a struct field")) } }
            BcInstr::AssignIndex(object_code, index_code) => { let value = stack.pop().unwrap_or(Value::Null); let object = bc_eval(object_code, env)?; let index = bc_eval(index_code, env)?; match object { Value::List(items) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::coded("E031", "index out of range")); } let mut items = items.borrow_mut(); let slot = items.get_mut(index as usize).ok_or_else(|| RuntimeError::coded("E031", "index out of range"))?; *slot = copy_value(&value); stack.push(value); }, Value::Map(items) => { let mut items = items.borrow_mut(); map_insert_sorted(&mut items, index, copy_value(&value)); stack.push(value); }, _ => return Err(RuntimeError::new("assignment target is not indexable")) } }
            BcInstr::If(then_code, else_code) => { let condition = stack.pop().unwrap_or(Value::Null); let flow = if truthy(&condition) { bc_run(then_code, &Env::new(Some(env.clone())), stack)? } else if let Some(else_code) = else_code { bc_run(else_code, &Env::new(Some(env.clone())), stack)? } else { Flow::Normal }; if !matches!(flow, Flow::Normal) { return Ok(flow); } }
            BcInstr::While(condition, body) => loop { if !truthy(&bc_eval(condition, env)?) { break; } match bc_run(body, &Env::new(Some(env.clone())), stack)? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) } },
            BcInstr::For(names, iterable, body) => { let entries = bc_iterable(bc_eval(iterable, env)?)?; for entry in entries { if entry.len() < names.len() { return Err(RuntimeError::new("loop binding count mismatch")); } let child = Env::new(Some(env.clone())); for (name, value) in names.iter().zip(entry) { Env::define(&child, name.clone(), copy_value(&value)); } match bc_run(body, &child, stack)? { Flow::Break => break, Flow::Continue | Flow::Normal => {}, flow => return Ok(flow) } } }
            BcInstr::Switch(value_code, cases) => { let value = bc_eval(value_code, env)?; for (pattern, body) in cases { let bindings = match pattern { Some(pattern) => match_pattern(pattern, &value)?, None => Some(HashMap::new()) }; if let Some(bindings) = bindings { let child = Env::new(Some(env.clone())); for (name, bound) in bindings { Env::define(&child, name, bound); } match bc_run(body, &child, stack)? { Flow::Normal => {}, flow => return Ok(flow) } break; } } }
            BcInstr::Try(body, catch_name, catch, finally) => {
                let mut outcome = bc_run(body, &Env::new(Some(env.clone())), stack);
                if let Err(error) = outcome {
                    if let Some(catch) = catch { let child = Env::new(Some(env.clone())); if let Some(name) = catch_name { Env::define(&child, name.clone(), Value::Exception(error)); } outcome = bc_run(catch, &child, stack); }
                    else { outcome = Err(error); }
                }
                if let Some(finally) = finally { let final_outcome = bc_run(finally, &Env::new(Some(env.clone())), stack); if final_outcome.is_err() || matches!(final_outcome, Ok(Flow::Return(_))) { outcome = final_outcome; } }
                match outcome? { Flow::Normal => {}, flow => return Ok(flow) }
            }
            BcInstr::Throw => {
                let value = stack.pop().unwrap_or(Value::Null);
                let error = match value {
                    Value::String(message) => RuntimeError::new(message),
                    Value::Exception(error) => error,
                    _ => RuntimeError::new("throw requires string or exception"),
                };
                return Err(error);
            }
            BcInstr::Return => return Ok(Flow::Return(stack.pop().unwrap_or(Value::Null))),
            BcInstr::Break => return Ok(Flow::Break), BcInstr::Continue => return Ok(Flow::Continue),
        }
    }
    Ok(Flow::Normal)
}

/// Deep-copies a value the way the reference backends do at every load/store
/// boundary: containers and structs get fresh storage; handles and functions
/// are identity values (Arc clones); scalars pass through.
// Canonical Map key order (TreeMap-style total order): type rank first
// (null < bool < numbers < char < string < enum < function), then the value
// within the type.  Numeric kinds compare numerically across Byte/Int/Float
// with ties broken by kind; char/string compare by code point; enum by its
// string form; function keys by identity (implementation-defined).
fn map_key_rank(value: &Value) -> u8 {
    match value {
        Value::Null => 0,
        Value::Bool(_) => 1,
        Value::Byte(_) | Value::Int(_) | Value::Float(_) => 2,
        Value::Char(_) => 3,
        Value::String(_) => 4,
        Value::Enum(_) => 5,
        Value::Function(_) | Value::Builtin(_) => 6,
        _ => 7,
    }
}

fn map_key_sub_rank(value: &Value) -> u8 {
    match value {
        Value::Byte(_) => 0,
        Value::Int(_) => 1,
        Value::Float(_) => 2,
        _ => 0,
    }
}

fn map_key_less(a: &Value, b: &Value) -> bool {
    let (ra, rb) = (map_key_rank(a), map_key_rank(b));
    if ra != rb { return ra < rb; }
    match (a, b) {
        (Value::Bool(x), Value::Bool(y)) => x < y,
        (Value::Char(x), Value::Char(y)) => x < y,
        (Value::String(x), Value::String(y)) => x < y,
        (Value::Enum(x), Value::Enum(y)) => {
            let key = |e: &EnumValue| format!("{}.{}({})", e.definition.name, e.member, e.payload.iter().map(|v| v.to_string()).collect::<Vec<_>>().join(", "));
            key(x) < key(y)
        }
        (Value::Function(x), Value::Function(y)) => Arc::as_ptr(x) < Arc::as_ptr(y),
        (Value::Builtin(x), Value::Builtin(y)) => (Arc::as_ptr(x) as *const u8) < (Arc::as_ptr(y) as *const u8),
        (a @ (Value::Byte(_) | Value::Int(_) | Value::Float(_)), b @ (Value::Byte(_) | Value::Int(_) | Value::Float(_))) => {
            let av = match a { Value::Byte(v) => *v as f64, Value::Int(v) => *v as f64, Value::Float(v) => *v, _ => unreachable!() };
            let bv = match b { Value::Byte(v) => *v as f64, Value::Int(v) => *v as f64, Value::Float(v) => *v, _ => unreachable!() };
            if av != bv { return av < bv; }
            map_key_sub_rank(a) < map_key_sub_rank(b)
        }
        _ => false,
    }
}

// Insert (key, value) into a key-sorted entry vec, updating in place when the
// key already exists.  Callers must hold the mutable borrow.
fn map_insert_sorted(items: &mut Vec<(Value, Value)>, key: Value, value: Value) {
    if let Some((_, existing)) = items.iter_mut().find(|(k, _)| *k == key) {
        *existing = value;
        return;
    }
    let pos = items.partition_point(|(k, _)| map_key_less(k, &key));
    items.insert(pos, (key, value));
}

fn copy_value(value: &Value) -> Value {
    match value {
        Value::List(items) => Value::List(Arc::new(RefCell::new(items.borrow().iter().map(copy_value).collect::<Vec<_>>()))),
        Value::Stack(items) => Value::Stack(Arc::new(RefCell::new(items.borrow().iter().map(copy_value).collect::<Vec<_>>()))),
        Value::Map(items) => Value::Map(Arc::new(RefCell::new(items.borrow().iter().map(|(key, val)| (copy_value(key), copy_value(val))).collect::<Vec<_>>()))),
        Value::Struct(struct_value) => {
            let struct_value = struct_value.borrow();
            Value::Struct(Arc::new(RefCell::new(StructValue {
                definition: struct_value.definition.clone(),
                fields: struct_value.fields.iter().map(|(name, field)| (name.clone(), copy_value(field))).collect(),
                unresolved: struct_value.unresolved,
            })))
        }
        other => other.clone(),
    }
}

fn bc_index(object: Value, index: Value) -> Result<Value, RuntimeError> {
    match object {
        Value::List(items) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::coded("E031", "index out of range")); } items.borrow().get(index as usize).map(|value| copy_value(value)).ok_or_else(|| RuntimeError::coded("E031", "index out of range")) }
        Value::String(text) => { let index = integer_value(&index)?; if index < 0 { return Err(RuntimeError::coded("E031", "index out of range")); } text.chars().nth(index as usize).map(Value::Char).ok_or_else(|| RuntimeError::coded("E031", "index out of range")) }
        Value::Map(items) => items.borrow().iter().find(|(key, _)| *key == index).map(|(_, value)| copy_value(value)).ok_or_else(|| RuntimeError::new("key not found")),
        _ => Err(RuntimeError::new("value is not indexable")),
    }
}

fn bc_iterable(source: Value) -> Result<Vec<Vec<Value>>, RuntimeError> {
    match source {
        Value::List(values) => Ok(values.borrow().iter().cloned().map(|value| vec![value]).collect()),
        Value::Map(values) => Ok(values.borrow().iter().cloned().map(|(key, value)| vec![key, value]).collect()),
        Value::Stack(values) => Ok(values.borrow().iter().rev().cloned().map(|value| vec![value]).collect()),
        Value::String(value) => Ok(value.chars().map(|value| vec![Value::Char(value)]).collect()),
        Value::Struct(value) => { let iterator = member(Value::Struct(value), "iterator", &Env::package(None, String::new()))?; match call(iterator, Vec::new())? { Value::List(values) => Ok(values.borrow().iter().cloned().map(|value| vec![value]).collect()), _ => Err(RuntimeError::new("iterator must return a list")) } }
        _ => Err(RuntimeError::new("value is not iterable")),
    }
}

fn bc_call(function: Arc<Callable>, code: Arc<BcCode>, args: Vec<Value>) -> Result<Value, RuntimeError> {
    let params = &function.function.params;
    let fixed = params.iter().filter(|param| !param.variadic).count();
    let variadic = params.last().is_some_and(|param| param.variadic);
    if (!variadic && args.len() != params.len()) || (variadic && args.len() < fixed) { return Err(RuntimeError::coded("E068", format!("{} argument count mismatch", function.function.name))); }
    let mut generic_bindings = HashMap::new();
    for (param, argument) in params.iter().zip(args.iter()) {
        if param.typ.args.is_empty() && param.typ.name.chars().next().is_some_and(|ch| ch.is_ascii_uppercase()) { generic_bindings.insert(param.typ.name.clone(), argument.clone()); }
    }
    for type_param in &function.function.type_params {
        if let Some(argument) = generic_bindings.get(&type_param.name) {
            for constraint in &type_param.constraints {
                let method_name = match constraint.name.rsplit('.').next().unwrap_or(&constraint.name) { "Stringable" => "string", "Equatable" => "equals", "Countable" => "len", "Iterable" | "Collection" => "iterator", _ => continue };
                if member(argument.clone(), method_name, &function.closure).is_err() { return Err(RuntimeError::coded("E067", format!("type {} does not satisfy generic constraint {}", type_name(argument), constraint.name))); }
            }
        }
    }
    let local = Env::new(Some(function.closure.clone()));
    if let Some(receiver) = &function.receiver {
        Env::define(&local, "self".into(), receiver.clone());
        if let Value::Struct(value) = receiver {
            for (name, field) in value.borrow().fields.clone() { Env::define(&local, name, field); }
            for (name, method) in value.borrow().definition.methods.clone() {
                Env::define(&local, name, Value::Function(Arc::new(Callable { function: method.clone(), closure: local.clone(), receiver: Some(receiver.clone()), bytecode: Some(bc_compile_block(method.body.as_ref())) })));
            }
        }
    }
    let mut index = 0;
    for param in params { if param.variadic { Env::define(&local, param.name.clone(), Value::List(Arc::new(RefCell::new(args[index..].iter().map(copy_value).collect::<Vec<_>>())))); index = args.len(); } else { Env::define(&local, param.name.clone(), copy_value(&args[index])); index += 1; } }
    let mut stack = Vec::new();
    let flow = bc_run(&code, &local, &mut stack)?;
    if let Some(Value::Struct(value)) = &function.receiver { let fields = value.borrow().fields.keys().cloned().collect::<Vec<_>>(); let mut target = value.borrow_mut(); for field in fields { if let Some(value) = Env::get(&local, &field) { target.fields.insert(field, value); } } }
    match flow { Flow::Return(value) => Ok(value), Flow::Normal => Ok(Value::Null), Flow::Break | Flow::Continue => Err(RuntimeError::new("invalid control flow")) }
}

#[cfg(test)]
mod tests {
    use super::run;
    use crate::semantic_parser::parse;

    #[test]
    fn executes_native_functions_and_control_flow() {
        let source = "package demo\nfunc twice(x: Int) -> Int {\n return x * 2\n}\nfunc main() -> Int {\n mut total: Int = 0\n for x in [1, 2, 3] {\n  total = total + twice(x)\n }\n return total\n}\n";
        let program = parse(source).unwrap();
        assert_eq!(run(&program).unwrap(), 12);
    }

    #[test]
    fn executes_native_closure_capture() {
        let program = parse("package demo\nfunc main() -> Int {\n mut n: Int = 4\n f: Func<Int> = func() -> Int { return n }\n n = 7\n return f()\n}\n").unwrap();
        assert_eq!(run(&program).unwrap(), 7);
    }

    #[test]
    fn executes_native_struct_literal_and_bound_method() {
        let program = parse("package demo\nstruct Box { value: Int\n func get() -> Int { return self.value }\n}\nfunc main() -> Int {\n b: Box = Box { value: 41 }\n return b.get() + 1\n}\n").unwrap();
        assert_eq!(run(&program).unwrap(), 42);
    }
}
