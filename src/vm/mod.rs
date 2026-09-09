//! The Solvik bytecode virtual machine.

pub mod frames;
pub mod heap;
pub mod natives;
pub mod streams;
pub mod value;

use std::collections::HashMap;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};

use crate::bytecode::{CodeModule, ConstVal};
use frames::{CallFrame, TryRegion};
use heap::{GcRef, Heap, HeapObject};
use value::Value;

/// State shared between all Solvik threads of one program.
#[derive(Clone)]
pub struct SharedState {
    module: Arc<CodeModule>,
    heap: Arc<Mutex<Heap>>,
    /// Indexed by constant ID; None exactly for non-string constants.
    str_consts: Arc<[Option<GcRef>]>,
    pub streams: Arc<Mutex<streams::Streams>>,
    /// Number of currently active Solvik threads (for GC gating).
    pub active_threads: Arc<AtomicUsize>,
    /// Optional process-wide deterministic state selected by Random.seed.
    pub random_state: Arc<Mutex<Option<u64>>>,
}

impl SharedState {
    /// Materialize literals before publishing the paired module, heap, and cache.
    fn new(module: CodeModule) -> Self {
        let mut heap = Heap::new();
        // Heap::alloc does not collect. If that changes, construction needs
        // temporary roots before allocating the next literal.
        let str_consts = {
            let mut strings = HashMap::new();
            module
                .constants
                .iter()
                .map(|constant| match constant {
                    ConstVal::Str(text) => {
                        Some(*strings.entry(text.as_str()).or_insert_with(|| {
                            heap.alloc(HeapObject::String { text: text.clone() })
                        }))
                    }
                    _ => None,
                })
                .collect::<Arc<[Option<GcRef>]>>()
        };
        Self {
            module: Arc::new(module),
            heap: Arc::new(Mutex::new(heap)),
            str_consts,
            streams: Arc::new(Mutex::new(streams::Streams::default())),
            active_threads: Arc::new(AtomicUsize::new(0)),
            random_state: Arc::new(Mutex::new(None)),
        }
    }
}

/// A runtime error that aborts execution with a message.
#[derive(Debug)]
pub struct VmError {
    pub message: String,
    /// Source location hint (file, line), if known.
    pub location: Option<(String, u32)>,
}

impl VmError {
    fn new(message: impl Into<String>) -> Self {
        VmError {
            message: message.into(),
            location: None,
        }
    }

    /// Build an error with an explicit source location (used where the VM
    /// cannot call `err_at` because `self` is already borrowed).
    fn with_loc(message: impl Into<String>, location: Option<(String, u32)>) -> Self {
        VmError {
            message: message.into(),
            location,
        }
    }
}

/// The virtual machine: one per Solvik thread.
pub struct Vm {
    pub shared: SharedState,
    /// Contiguous value stack (frames' locals live here).
    stack: Vec<Value>,
    frames: Vec<CallFrame>,
    /// Global bindings: 0=stdin 1=stdout 2=stderr.
    globals: Vec<Value>,
    try_regions: Vec<TryRegion>,
    /// Set when a throw is in flight.
    pending_throw: Option<Value>,
    /// Exception to rethrow once the current finally block completes.
    rethrow: Option<Value>,
    /// Return deferred while enclosing finally blocks run.
    pending_return: Option<PendingReturn>,
    /// Ip to resume at after a FinallyDiverted finally completes
    /// (break/continue trampoline).
    finally_resume: Option<u32>,
}

/// A return intercepted by an enclosing finally block.
#[derive(Debug, Clone)]
enum PendingReturn {
    /// The function returns no value.
    Void,
    /// The function returns this value.
    Value(Value),
}

impl Vm {
    // ------------------------------------------------------------------
    // Construction / entry points
    // ------------------------------------------------------------------

    /// Create the main VM and run the module's entry point.
    pub fn run_main(module: CodeModule, args: Vec<String>) -> Result<i64, VmError> {
        let shared = SharedState::new(module);
        let mut vm = Vm::new(shared);
        vm.shared.active_threads.fetch_add(1, Ordering::SeqCst);
        let result = vm.run_entry(args);
        vm.shared.active_threads.fetch_sub(1, Ordering::SeqCst);
        result
    }

    fn new(shared: SharedState) -> Self {
        // Globals: stdin/stdout/stderr stream handles.
        let globals = {
            let mut heap = shared.heap.lock().unwrap_or_else(|e| e.into_inner());
            (0..3)
                .map(|k| Value::Object(heap.alloc(HeapObject::Stream { kind: k })))
                .collect()
        };
        Vm {
            shared,
            stack: Vec::new(),
            frames: Vec::new(),
            globals,
            try_regions: Vec::new(),
            pending_throw: None,
            rethrow: None,
            pending_return: None,
            finally_resume: None,
        }
    }

    fn run_entry(&mut self, args: Vec<String>) -> Result<i64, VmError> {
        let entry = self
            .shared
            .module
            .entry
            .ok_or_else(|| VmError::new("module has no entry point"))?;
        let f = &self.shared.module.functions[entry as usize];
        if f.params.len() != 1 {
            return Err(VmError::new(format!(
                "entry point '{}' expects 1 parameter (the args list)",
                f.name
            )));
        }
        // Build the args list object.
        let list_ref = {
            let mut heap = self.heap();
            heap.alloc(HeapObject::List { items: Vec::new() })
        };
        for a in args {
            let s = self.alloc_string(&a);
            self.list_add(list_ref, Value::Object(s))?;
        }
        self.call_function(entry, &[Value::Object(list_ref)], None)?;
        self.execute()?;
        match self.stack.pop() {
            Some(Value::Long(i)) => Ok(i),
            Some(v) => Ok(value_to_int(&v)),
            None => Ok(0),
        }
    }

    /// Spawn a new Solvik thread executing `runnable.run()`.
    pub fn spawn_thread(shared: SharedState, runnable: GcRef) -> std::thread::JoinHandle<()> {
        shared.active_threads.fetch_add(1, Ordering::SeqCst);
        std::thread::spawn(move || {
            let mut vm = Vm::new(shared.clone());
            if let Err(e) = vm.run_runnable(runnable) {
                eprintln!("thread error: {}", e.message);
            }
            shared.active_threads.fetch_sub(1, Ordering::SeqCst);
        })
    }

    fn run_runnable(&mut self, runnable: GcRef) -> Result<(), VmError> {
        // Find the Runnable interface id and the 'run' slot.
        let module = &self.shared.module;
        let iface = module
            .interfaces
            .iter()
            .position(|i| i.name == "Runnable")
            .ok_or_else(|| VmError::new("Runnable interface not found in module"))?
            as u32;
        let slot = module.interfaces[iface as usize]
            .slots
            .iter()
            .position(|s| s == "run")
            .ok_or_else(|| VmError::new("Runnable has no 'run' slot"))?;
        // Resolve the concrete class's dispatch for Runnable.run.
        let class = {
            let heap = self.heap();
            match heap.get(runnable) {
                Some(HeapObject::Instance { class, .. }) => *class as usize,
                _ => return Err(VmError::new("Runnable is not an object")),
            }
        };
        let entry = module
            .classes
            .get(class)
            .and_then(|c| c.interfaces.iter().find(|(iid, _)| *iid == iface))
            .ok_or_else(|| VmError::new("object does not implement Runnable"))?;
        let target = entry
            .1
            .get(slot)
            .copied()
            .ok_or_else(|| VmError::new("Runnable has no 'run' implementation"))?;
        self.call_function(target, &[Value::Object(runnable)], None)?;
        self.execute()?;
        self.stack.pop();
        Ok(())
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    fn heap(&self) -> std::sync::MutexGuard<'_, Heap> {
        self.shared.heap.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn heap_mut(&mut self) -> std::sync::MutexGuard<'_, Heap> {
        self.shared.heap.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn alloc_string(&mut self, s: &str) -> GcRef {
        self.heap_mut().alloc(HeapObject::String {
            text: s.to_string(),
        })
    }

    /// Content equality: primitives by value, strings by text, enum values
    /// by (variant id, index, content-equal payload), other objects by
    /// identity.
    fn values_equal(heap: &Heap, a: &Value, b: &Value) -> bool {
        match (a, b) {
            (Value::Null, Value::Null) => true,
            (Value::Bool(x), Value::Bool(y)) => x == y,
            (Value::Long(x), Value::Long(y)) => x == y,
            (Value::Double(x), Value::Double(y)) => x == y,
            (Value::Char(x), Value::Char(y)) => x == y,
            (Value::Object(ra), Value::Object(rb)) => {
                if ra == rb {
                    return true;
                }
                match (heap.get(*ra), heap.get(*rb)) {
                    (
                        Some(HeapObject::String { text: ta }),
                        Some(HeapObject::String { text: tb }),
                    ) => ta == tb,
                    (
                        Some(HeapObject::Enum {
                            enum_id: ea,
                            index: ia,
                            payload: pa,
                        }),
                        Some(HeapObject::Enum {
                            enum_id: eb,
                            index: ib,
                            payload: pb,
                        }),
                    ) => {
                        ea == eb
                            && ia == ib
                            && match (pa, pb) {
                                (None, None) => true,
                                (Some(x), Some(y)) => Self::values_equal(heap, x, y),
                                _ => false,
                            }
                    }
                    _ => false,
                }
            }
            _ => false,
        }
    }

    fn list_add(&mut self, list: GcRef, item: Value) -> Result<(), VmError> {
        let mut heap = self.heap_mut();
        match heap.get_mut(list) {
            Some(HeapObject::List { items }) => {
                items.push(item);
                Ok(())
            }
            _ => Err(VmError::new("not a list")),
        }
    }

    pub(crate) fn current_location(&self) -> Option<(String, u32)> {
        let frame = self.frames.last()?;
        let f = &self.shared.module.functions[frame.fid as usize];
        let line = f
            .line_map
            .iter()
            .rev()
            .find(|(off, _)| *off <= frame.ip)
            .map(|(_, l)| *l)
            .unwrap_or(0);
        let file = self
            .shared
            .module
            .sources
            .get(f.source_file as usize)
            .cloned()
            .unwrap_or_default();
        Some((file, line))
    }

    pub(crate) fn err_at(&self, message: impl Into<String>) -> VmError {
        let mut e = VmError::new(message);
        e.location = self.current_location();
        e
    }

    /// Run GC when due and we are the only active thread.
    fn maybe_gc(&self) {
        if self.shared.active_threads.load(Ordering::SeqCst) > 1 {
            return;
        }
        // Roots: the operand stack (call frames' locals live in it), the
        // global bindings, shared string constants, and any exception in flight.
        let mut roots: Vec<Value> = self.stack.to_vec();
        roots.extend_from_slice(&self.globals);
        roots.extend(
            self.shared
                .str_consts
                .iter()
                .flatten()
                .copied()
                .map(Value::Object),
        );
        if let Some(e) = &self.pending_throw {
            roots.push(*e);
        }
        if let Some(e) = &self.rethrow {
            roots.push(*e);
        }
        if let Some(PendingReturn::Value(v)) = &self.pending_return {
            roots.push(*v);
        }
        let mut heap = self.shared.heap.lock().unwrap_or_else(|e| e.into_inner());
        if heap.gc_due() {
            heap.collect(&roots);
        }
    }

    // ------------------------------------------------------------------
    // Call machinery
    // ------------------------------------------------------------------

    /// Call function `fid` with `args` already evaluated (not on the stack).
    fn call_function(
        &mut self,
        fid: u32,
        args: &[Value],
        construct_as: Option<u16>,
    ) -> Result<(), VmError> {
        let f = &self.shared.module.functions[fid as usize];
        if args.len() != f.params.len() {
            return Err(self.err_at(format!(
                "call to '{}' passes {} args, expected {}",
                f.name,
                args.len(),
                f.params.len()
            )));
        }
        let base = self.stack.len();
        // Locals: params first, then remaining slots as null.
        for a in args {
            self.stack.push(*a);
        }
        while self.stack.len() < base + f.local_count as usize {
            self.stack.push(Value::Null);
        }
        self.frames.push(CallFrame {
            fid,
            ip: 0,
            base,
            args_count: args.len() as u16,
            construct_as,
        });
        Ok(())
    }

    /// Pop the current frame. The return value (if any) must already have
    /// been popped by the caller. Returns true when the function returns a
    /// value (so the caller knows to push one back).
    fn return_from_frame(&mut self) -> Result<bool, VmError> {
        let frame = self
            .frames
            .pop()
            .ok_or_else(|| VmError::new("return with no active frame"))?;
        let f = &self.shared.module.functions[frame.fid as usize];
        // Drop this frame's local slots; the caller's stack is below `base`.
        let target = frame.base;
        if self.stack.len() > target {
            self.stack.truncate(target);
        }
        self.try_regions
            .retain(|r| r.frame_depth <= self.frames.len());
        Ok(f.returns_value)
    }

    /// Throw a runtime exception value, unwinding try regions. A new
    /// exception supersedes any deferred return or diverted break/continue.
    fn do_throw(&mut self, exc: Value) -> Result<(), VmError> {
        self.pending_throw = Some(exc);
        self.pending_return = None;
        self.finally_resume = None;
        self.unwind_to_catch()
    }

    /// If the innermost try region belongs to the current frame and has a
    /// finally handler, pop it and transfer control to the finally body.
    /// Used when a return/break/continue must run the finally first.
    fn divert_to_finally(&mut self) -> bool {
        let fin = match self.try_regions.last() {
            Some(r) if r.frame_depth == self.frames.len() => r.finally_ip,
            _ => None,
        };
        if let Some(fin) = fin {
            let region = self.try_regions.pop().unwrap();
            self.reset_stack_to(region.stack_base);
            self.frames.last_mut().unwrap().ip = fin;
            true
        } else {
            false
        }
    }

    /// Unwind frames/regions until a catch handler is found or we exit.
    fn unwind_to_catch(&mut self) -> Result<(), VmError> {
        while let Some(exc) = self.pending_throw.take() {
            // Pop frames until the innermost try region belongs to the
            // current frame (or there are none left).
            loop {
                let Some(region) = self.try_regions.last() else {
                    // No handlers left: uncaught exception.
                    let msg = {
                        let heap = self.heap();
                        match exc {
                            Value::Object(r) => heap.object_string(r),
                            other => other.to_display(&heap),
                        }
                    };
                    return Err(VmError {
                        message: format!("uncaught exception: {}", msg),
                        location: None,
                    });
                };
                if region.frame_depth == self.frames.len() {
                    break;
                }
                // The current frame has no handler: pop it.
                let frame = self.frames.pop().unwrap();
                let target = frame.base;
                if self.stack.len() > target {
                    self.stack.truncate(target);
                }
                self.try_regions
                    .retain(|r| r.frame_depth <= self.frames.len());
            }
            let region = self.try_regions.pop().unwrap();
            // No finally (None) means a plain catch; finally == catch
            // encodes a pure-finally region.
            let has_catch = region.finally_ip != Some(region.catch_ip);
            if has_catch {
                // Catch: reset stack to region base and push the exception.
                self.reset_stack_to(region.stack_base);
                self.stack.push(exc);
                self.frames.last_mut().unwrap().ip = region.catch_ip;
                return Ok(());
            }
            // No catch (or pure-finally region): run finally, then rethrow.
            if let Some(fin) = region.finally_ip {
                self.reset_stack_to(region.stack_base);
                self.frames.last_mut().unwrap().ip = fin;
                self.rethrow = Some(exc);
                return Ok(());
            }
            // No finally either: continue unwinding outward.
            self.pending_throw = Some(exc);
        }
        Ok(())
    }

    fn reset_stack_to(&mut self, base: usize) {
        self.stack.truncate(base);
    }
}

fn value_to_int(v: &Value) -> i64 {
    match v {
        Value::Long(i) => *i,
        Value::Double(f) => *f as i64,
        Value::Bool(b) => i64::from(*b),
        _ => 0,
    }
}

// ---------------------------------------------------------------------------
// Execution loop
// ---------------------------------------------------------------------------

impl Vm {
    fn pop(&mut self) -> Value {
        self.stack.pop().unwrap_or(Value::Null)
    }

    /// Pop n values in original stack order (bottom first).
    fn pop_n(&mut self, n: usize) -> Vec<Value> {
        let start = self.stack.len().saturating_sub(n);
        let mut out = Vec::with_capacity(n);
        while self.stack.len() > start {
            out.push(self.stack.pop().unwrap());
        }
        out.reverse();
        out
    }

    fn push(&mut self, v: Value) {
        self.stack.push(v);
    }

    fn ref_of(v: &Value) -> Option<GcRef> {
        match v {
            Value::Object(r) => Some(*r),
            _ => None,
        }
    }

    fn bool_of(&self, v: &Value) -> Result<bool, VmError> {
        match v {
            Value::Bool(b) => Ok(*b),
            _ => Err(self.err_at("expected Bool")),
        }
    }

    fn long_of(&self, v: &Value) -> Result<i64, VmError> {
        match v {
            Value::Long(i) => Ok(*i),
            _ => Err(self.err_at("expected Long")),
        }
    }

    fn double_of(&self, v: &Value) -> Result<f64, VmError> {
        match v {
            Value::Double(f) => Ok(*f),
            Value::Long(i) => Ok(*i as f64),
            _ => Err(self.err_at("expected Double")),
        }
    }

    fn char_of(&self, v: &Value) -> Result<char, VmError> {
        match v {
            Value::Char(c) => Ok(*c),
            _ => Err(self.err_at("expected Char")),
        }
    }

    /// Read two String operands as text.
    fn strs(&self, a: &Value, b: &Value) -> Result<(String, String), VmError> {
        let heap = self.heap();
        let get = |v: &Value| -> Result<String, VmError> {
            match Self::ref_of(v) {
                Some(r) => match heap.get(r) {
                    Some(HeapObject::String { text }) => Ok(text.clone()),
                    _ => Err(VmError::new("expected String")),
                },
                None => Err(VmError::new("expected String")),
            }
        };
        Ok((get(a)?, get(b)?))
    }

    /// Read one String operand as text.
    fn str_of(&self, v: &Value) -> Result<String, VmError> {
        let heap = self.heap();
        match Self::ref_of(v) {
            Some(r) => match heap.get(r) {
                Some(HeapObject::String { text }) => Ok(text.clone()),
                _ => Err(VmError::new("expected String")),
            },
            None => Err(VmError::new("expected String")),
        }
    }

    fn displays(&self, a: &Value, b: &Value) -> (String, String) {
        let heap = self.heap();
        (a.to_display(&heap), b.to_display(&heap))
    }

    fn make_string(&mut self, text: String) -> Value {
        Value::Object(self.alloc_string(&text))
    }

    fn receiver_class(&self, v: &Value) -> Result<u16, VmError> {
        match Self::ref_of(v) {
            Some(r) => {
                let heap = self.heap();
                match heap.get(r) {
                    Some(HeapObject::Instance { class, .. }) => Ok(*class),
                    _ => Err(VmError::new("expected object")),
                }
            }
            None => Err(VmError::new("expected object")),
        }
    }

    /// Pop `arity` arguments plus the receiver beneath them.
    fn pop_call(&mut self, arity: usize) -> (Value, Vec<Value>) {
        let args = self.pop_n(arity);
        let recv = self.pop();
        (recv, args)
    }

    /// Run until the frame stack is empty.
    fn execute(&mut self) -> Result<(), VmError> {
        let module = self.shared.module.clone();
        loop {
            if self.frames.is_empty() {
                return Ok(());
            }
            let fid = self.frames.last().unwrap().fid as usize;
            let code = &module.functions[fid].code;
            let ip = self.frames.last().unwrap().ip as usize;
            if ip >= code.len() {
                // Fell off the end: implicit void return.
                self.return_from_frame()?;
                continue;
            }
            let op = match crate::ir::IrOp::from_code(code[ip]) {
                Some(o) => o,
                None => return Err(self.err_at(format!("unknown opcode 0x{:02x}", code[ip]))),
            };
            let mut p = ip + 1;
            let mut a = [0u32; 3];
            for (i, slot) in a.iter_mut().enumerate().take(op.operand_count()) {
                let size = op.operand_size(i);
                let mut v: u32 = 0;
                for k in 0..size {
                    v |= (code[p + k] as u32) << (8 * k);
                }
                *slot = v;
                p += size;
            }
            self.frames.last_mut().unwrap().ip = p as u32;
            self.step(&module, op, &a)?;
        }
    }

    /// Execute one instruction.
    #[allow(clippy::too_many_lines)]
    fn step(&mut self, module: &CodeModule, op: crate::ir::IrOp, a: &[u32]) -> Result<(), VmError> {
        use crate::ir::IrOp::*;
        match op {
            // ---- constants / locals / globals --------------------------
            LoadConst => {
                let c = module
                    .constants
                    .get(a[0] as usize)
                    .ok_or_else(|| self.err_at("constant index out of range"))?;
                if self.shared.str_consts.len() != module.constants.len() {
                    return Err(
                        self.err_at("internal VM invariant: string constant cache length mismatch")
                    );
                }
                match c {
                    ConstVal::Str(_) => {
                        let r = self
                            .shared
                            .str_consts
                            .get(a[0] as usize)
                            .copied()
                            .flatten()
                            .ok_or_else(|| {
                                self.err_at("internal VM invariant: missing string constant")
                            })?;
                        self.push(Value::Object(r));
                    }
                    ConstVal::Null => self.push(Value::Null),
                    ConstVal::Bool(b) => self.push(Value::Bool(*b)),
                    ConstVal::Long(i) => self.push(Value::Long(*i)),
                    ConstVal::Double(f) => self.push(Value::Double(*f)),
                    ConstVal::Char(ch) => self.push(Value::Char(*ch)),
                }
            }
            LoadLocal => {
                let base = self.frames.last().map(|f| f.base).unwrap_or(0);
                let idx = base + a[0] as usize;
                let v = *self
                    .stack
                    .get(idx)
                    .ok_or_else(|| self.err_at("local index out of range"))?;
                self.push(v);
            }
            StoreLocal => {
                let base = self.frames.last().map(|f| f.base).unwrap_or(0);
                let idx = base + a[0] as usize;
                let v = self.pop();
                match self.stack.get_mut(idx) {
                    Some(slot) => *slot = v,
                    None => return Err(self.err_at("local index out of range")),
                }
            }
            LoadGlobal => {
                let v = *self
                    .globals
                    .get(a[0] as usize)
                    .ok_or_else(|| self.err_at("global index out of range"))?;
                self.push(v);
            }
            StoreGlobal => {
                let v = self.pop();
                match self.globals.get_mut(a[0] as usize) {
                    Some(slot) => *slot = v,
                    None => return Err(self.err_at("global index out of range")),
                }
            }
            // ---- long arithmetic ---------------------------------------
            AddLong => {
                let b = self.pop();
                let a_ = self.pop();
                let r = self
                    .long_of(&a_)?
                    .checked_add(self.long_of(&b)?)
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            SubLong => {
                let b = self.pop();
                let a_ = self.pop();
                let r = self
                    .long_of(&a_)?
                    .checked_sub(self.long_of(&b)?)
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            MulLong => {
                let b = self.pop();
                let a_ = self.pop();
                let r = self
                    .long_of(&a_)?
                    .checked_mul(self.long_of(&b)?)
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            DivLong => {
                let b = self.pop();
                let a_ = self.pop();
                let divisor = self.long_of(&b)?;
                if divisor == 0 {
                    return Err(self.err_at("division by zero"));
                }
                let r = self
                    .long_of(&a_)?
                    .checked_div(divisor)
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            ModLong => {
                let b = self.pop();
                let a_ = self.pop();
                let divisor = self.long_of(&b)?;
                if divisor == 0 {
                    return Err(self.err_at("modulo by zero"));
                }
                let r = self
                    .long_of(&a_)?
                    .checked_rem(divisor)
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            NegLong => {
                let v = self.pop();
                let r = self
                    .long_of(&v)?
                    .checked_neg()
                    .ok_or_else(|| self.err_at("integer overflow"))?;
                self.push(Value::Long(r));
            }
            // ---- double arithmetic -------------------------------------
            AddDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Double(self.double_of(&a_)? + self.double_of(&b)?));
            }
            SubDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Double(self.double_of(&a_)? - self.double_of(&b)?));
            }
            MulDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Double(self.double_of(&a_)? * self.double_of(&b)?));
            }
            DivDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Double(self.double_of(&a_)? / self.double_of(&b)?));
            }
            ModDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Double(self.double_of(&a_)? % self.double_of(&b)?));
            }
            NegDouble => {
                let v = self.pop();
                self.push(Value::Double(-self.double_of(&v)?));
            }
            // ---- conversions --------------------------------------------
            ToLong => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::CONV_LONG,
                    &[v],
                )?;
                self.push(r);
            }
            ToDouble => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::CONV_DOUBLE,
                    &[v],
                )?;
                self.push(r);
            }
            ToByte => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::CONV_BYTE,
                    &[v],
                )?;
                self.push(r);
            }
            ToBool => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::CONV_BOOL,
                    &[v],
                )?;
                self.push(r);
            }
            ToChar => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::CONV_CHAR,
                    &[v],
                )?;
                self.push(r);
            }
            ToStringValue => {
                let v = self.pop();
                let r = crate::vm::natives::call_native(
                    self,
                    crate::stdlib::builtins::nat::TO_STRING,
                    &[v],
                )?;
                self.push(r);
            }
            // ---- logic / null -------------------------------------------
            Not => {
                let v = self.pop();
                self.push(Value::Bool(!self.bool_of(&v)?));
            }
            And => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Bool(self.bool_of(&a_)? && self.bool_of(&b)?));
            }
            Pop => {
                self.pop();
            }
            IsNull => {
                let v = self.pop();
                self.push(Value::Bool(matches!(v, Value::Null)));
            }
            NullCheck => {
                let v = self.pop();
                if matches!(v, Value::Null) {
                    let r = self.heap_mut().alloc(HeapObject::Exception {
                        message: "null reference".to_string(),
                    });
                    self.do_throw(Value::Object(r))?;
                } else {
                    self.push(v);
                }
            }
            // ---- equality -------------------------------------------------
            EqLong => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Bool(self.long_of(&a_)? == self.long_of(&b)?));
            }
            EqDouble => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Bool(self.double_of(&a_)? == self.double_of(&b)?));
            }
            EqBool => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Bool(self.bool_of(&a_)? == self.bool_of(&b)?));
            }
            EqChar => {
                let b = self.pop();
                let a_ = self.pop();
                self.push(Value::Bool(self.char_of(&a_)? == self.char_of(&b)?));
            }
            EqString => {
                let b = self.pop();
                let a_ = self.pop();
                let (ta, tb) = self.strs(&a_, &b)?;
                self.push(Value::Bool(ta == tb));
            }
            EqObject => {
                let b = self.pop();
                let a_ = self.pop();
                let eq = {
                    let heap = self.heap();
                    Self::values_equal(&heap, &a_, &b)
                };
                self.push(Value::Bool(eq));
            }
            IdentityEq => {
                let b = self.pop();
                let a_ = self.pop();
                let eq = match (&a_, &b) {
                    (Value::Null, Value::Null) => true,
                    (Value::Object(ra), Value::Object(rb)) => ra == rb,
                    _ => false,
                };
                self.push(Value::Bool(eq));
            }
            IdentityNe => {
                let b = self.pop();
                let a_ = self.pop();
                let eq = match (&a_, &b) {
                    (Value::Null, Value::Null) => true,
                    (Value::Object(ra), Value::Object(rb)) => ra == rb,
                    _ => false,
                };
                self.push(Value::Bool(!eq));
            }
            EqEnum => {
                let b = self.pop();
                let a_ = self.pop();
                let eq = {
                    let heap = self.heap();
                    Self::values_equal(&heap, &a_, &b)
                };
                self.push(Value::Bool(eq));
            }
            EqDyn => {
                let b = self.pop();
                let a_ = self.pop();
                let (ta, tb) = self.displays(&a_, &b);
                self.push(Value::Bool(ta == tb));
            }
            // ---- ordering ---------------------------------------------------
            LtLong => {
                self.cmp_int(true, false)?;
            }
            LeLong => {
                self.cmp_int(true, true)?;
            }
            GtLong => {
                self.cmp_int(false, false)?;
            }
            GeLong => {
                self.cmp_int(false, true)?;
            }
            LtDouble => {
                self.cmp_float(true, false)?;
            }
            LeDouble => {
                self.cmp_float(true, true)?;
            }
            GtDouble => {
                self.cmp_float(false, false)?;
            }
            GeDouble => {
                self.cmp_float(false, true)?;
            }
            LtChar => {
                self.cmp_char(true, false)?;
            }
            LeChar => {
                self.cmp_char(true, true)?;
            }
            GtChar => {
                self.cmp_char(false, false)?;
            }
            GeChar => {
                self.cmp_char(false, true)?;
            }
            LtString => {
                self.cmp_string(true, false)?;
            }
            LeString => {
                self.cmp_string(true, true)?;
            }
            GtString => {
                self.cmp_string(false, false)?;
            }
            GeString => {
                self.cmp_string(false, true)?;
            }
            LtDyn => {
                self.cmp_dyn(true, false)?;
            }
            LeDyn => {
                self.cmp_dyn(true, true)?;
            }
            GtDyn => {
                self.cmp_dyn(false, false)?;
            }
            GeDyn => {
                self.cmp_dyn(false, true)?;
            }
            // ---- control flow ---------------------------------------------
            Jump => {
                self.frames.last_mut().unwrap().ip = a[0];
            }
            JumpIfFalse => {
                let v = self.pop();
                if !self.bool_of(&v)? {
                    self.frames.last_mut().unwrap().ip = a[0];
                }
            }
            JumpIfTrue => {
                let v = self.pop();
                if self.bool_of(&v)? {
                    self.frames.last_mut().unwrap().ip = a[0];
                }
            }
            // ---- calls -------------------------------------------------------
            CallFn => {
                let fid = a[0];
                let args = self.pop_n(a[1] as usize);
                self.call_function(fid, &args, None)?;
            }
            CallStatic => {
                let fid = a[0];
                let target = a[2] as u16;
                let args = self.pop_n(a[1] as usize);
                let construct_as = if target != 0xFFFF { Some(target) } else { None };
                self.call_function(fid, &args, construct_as)?;
            }
            CallVirtual => {
                let (recv, args) = self.pop_call(a[2] as usize);
                let actual = self.receiver_class(&recv)?;
                let target = module
                    .classes
                    .get(actual as usize)
                    .and_then(|c| c.vtable.get(a[1] as usize))
                    .copied()
                    .ok_or_else(|| self.err_at("vtable slot out of range"))?;
                let mut full = vec![recv];
                full.extend(args);
                self.call_function(target, &full, None)?;
            }
            CallInterface => {
                let (recv, args) = self.pop_call(a[2] as usize);
                let actual = self.receiver_class(&recv)?;
                let entry = module
                    .classes
                    .get(actual as usize)
                    .and_then(|c| c.interfaces.iter().find(|(iid, _)| *iid == a[0]))
                    .ok_or_else(|| self.err_at("object does not implement interface"))?;
                let target = entry
                    .1
                    .get(a[1] as usize)
                    .copied()
                    .ok_or_else(|| self.err_at("interface slot out of range"))?;
                let mut full = vec![recv];
                full.extend(args);
                self.call_function(target, &full, None)?;
            }
            CallSuper => {
                let (recv, args) = self.pop_call(a[2] as usize);
                // Operand 0 is the parent class itself; dispatch through
                // its vtable.
                let target = module
                    .classes
                    .get(a[0] as usize)
                    .and_then(|c| c.vtable.get(a[1] as usize))
                    .copied()
                    .ok_or_else(|| self.err_at("vtable slot out of range"))?;
                let mut full = vec![recv];
                full.extend(args);
                self.call_function(target, &full, None)?;
            }
            CallNative => {
                let mut args = self.pop_n(a[1] as usize);
                if crate::stdlib::builtins::native_takes_receiver(a[0] as u16) {
                    let recv = self.pop();
                    args.insert(0, recv);
                }
                let res = crate::vm::natives::call_native(self, a[0] as u16, &args)?;
                if crate::stdlib::builtins::native_returns_value(a[0] as u16) {
                    self.push(res);
                }
            }
            CallDynamic => {
                let (recv, args) = self.pop_call(a[1] as usize);
                let name = module
                    .dyn_names
                    .get(a[0] as usize)
                    .cloned()
                    .unwrap_or_default();
                let actual = self.receiver_class(&recv)?;
                let mut target = None;
                let mut c: Option<usize> = Some(actual as usize);
                while let Some(ci) = c {
                    let cm = &module.classes[ci];
                    if let Some(pos) = cm.vtable_names.iter().position(|n| *n == name) {
                        target = cm.vtable.get(pos).copied();
                        break;
                    }
                    c = cm.parent.map(|p| p as usize);
                }
                let target = target.ok_or_else(|| {
                    self.err_at(format!(
                        "no method '{}' on {}",
                        name, module.classes[actual as usize].name
                    ))
                })?;
                let mut full = vec![recv];
                full.extend(args);
                // The call-site static type is Object, so it always expects a
                // result. When the dynamically-dispatched method is actually
                // void it would leave nothing on the stack; plant a null
                // result slot beneath the callee frame so the stack stays
                // balanced (the callee's Return/ReturnVoid leaves it above
                // its base).
                if !module.functions[target as usize].returns_value {
                    self.push(Value::Null);
                }
                self.call_function(target, &full, None)?;
            }
            // ---- objects -----------------------------------------------------
            NewObject => {
                // Allocate an instance with default (null) fields; the
                // checker emits one (value, StoreField) pair per field.
                // An inherited constructor call overrides the class so
                // `Sub.new()` (inherited from Super) builds a Sub.
                let (class, n) = match self.frames.last().and_then(|f| f.construct_as) {
                    Some(t) if t != a[0] as u16 => {
                        (t, module.classes[t as usize].field_count as usize)
                    }
                    _ => (a[0] as u16, a[1] as usize),
                };
                let fields = vec![Value::Null; n];
                let r = self
                    .heap_mut()
                    .alloc(HeapObject::Instance { class, fields });
                self.push(Value::Object(r));
            }
            LoadField => {
                // [recv] -> [value]: the receiver is consumed.
                let top = self.pop();
                let loc = self.current_location();
                let v = {
                    let heap = self.heap();
                    match Self::ref_of(&top) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Instance { fields, .. }) => {
                                *fields.get(a[0] as usize).ok_or_else(|| {
                                    VmError::with_loc("field index out of range", loc.clone())
                                })?
                            }
                            _ => return Err(VmError::with_loc("not an object", loc.clone())),
                        },
                        None => return Err(VmError::with_loc("not an object", loc.clone())),
                    }
                };
                self.push(v);
            }
            StoreField => {
                // [recv, value] -> [recv]: pop the value, keep the receiver.
                let val = self.pop();
                let top = self.stack.last().copied().unwrap_or(Value::Null);
                let loc = self.current_location();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&top) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::Instance { fields, .. }) => {
                                *fields.get_mut(a[0] as usize).ok_or_else(|| {
                                    VmError::with_loc("field index out of range", loc.clone())
                                })? = val;
                            }
                            _ => return Err(VmError::with_loc("not an object", loc.clone())),
                        },
                        None => return Err(VmError::with_loc("not an object", loc.clone())),
                    }
                }
            }
            CopyFields => {
                // [src, dst] -> [dst]: copy the first `count` fields from src
                // into dst, then drop src. Used by `super:` construction.
                let count = a[0] as usize;
                let dst = self.pop();
                let src = self.pop();
                {
                    let mut heap = self.heap_mut();
                    let sref = Self::ref_of(&src)
                        .ok_or_else(|| VmError::new("copy source not an object"))?;
                    let dref = Self::ref_of(&dst)
                        .ok_or_else(|| VmError::new("copy dest not an object"))?;
                    let sfields = match heap.get(sref) {
                        Some(HeapObject::Instance { fields, .. }) => fields.clone(),
                        _ => return Err(VmError::new("copy source not an instance")),
                    };
                    if let Some(HeapObject::Instance { fields, .. }) = heap.get_mut(dref) {
                        let n = count.min(sfields.len()).min(fields.len());
                        fields[..n].copy_from_slice(&sfields[..n]);
                    } else {
                        return Err(VmError::new("copy dest not an instance"));
                    }
                }
                self.push(dst);
            }
            // ---- collections ---------------------------------------------------
            NewList => {
                let r = self
                    .heap_mut()
                    .alloc(HeapObject::List { items: Vec::new() });
                self.push(Value::Object(r));
            }
            NewStack => {
                let r = self
                    .heap_mut()
                    .alloc(HeapObject::Stack { items: Vec::new() });
                self.push(Value::Object(r));
            }
            NewMap => {
                let r = self.heap_mut().alloc(HeapObject::Map {
                    entries: Vec::new(),
                });
                self.push(Value::Object(r));
            }
            ListSpread => {
                let list = self.pop();
                let items = {
                    let heap = self.heap();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => items.clone(),
                            _ => return Err(VmError::new("spread requires a List")),
                        },
                        None => return Err(VmError::new("spread requires a List")),
                    }
                };
                for item in items {
                    self.push(item);
                }
            }
            ListExtend => {
                let source = self.pop();
                let destination = self.pop();
                let source_ref =
                    Self::ref_of(&source).ok_or_else(|| VmError::new("spread requires a List"))?;
                let items = {
                    let heap = self.heap();
                    match heap.get(source_ref) {
                        Some(HeapObject::List { items }) => items.clone(),
                        _ => return Err(VmError::new("spread requires a List")),
                    }
                };
                let destination_ref = Self::ref_of(&destination)
                    .ok_or_else(|| VmError::new("spread destination is not a List"))?;
                {
                    let mut heap = self.heap_mut();
                    match heap.get_mut(destination_ref) {
                        Some(HeapObject::List { items: dest }) => dest.extend(items),
                        _ => return Err(VmError::new("spread destination is not a List")),
                    }
                }
                self.push(destination);
            }
            ListAdd => {
                let item = self.pop();
                let list = self.pop();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::List { items }) => items.push(item),
                            _ => return Err(VmError::new("not a List")),
                        },
                        None => return Err(VmError::new("not a List")),
                    }
                }
                self.push(list);
            }
            ListGet => {
                let iv = self.pop();
                let idx = self.long_of(&iv)?;
                let list = self.pop();
                let loc = self.current_location();
                let v = {
                    let heap = self.heap();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => *usize::try_from(idx)
                                .ok()
                                .and_then(|index| items.get(index))
                                .ok_or_else(|| {
                                    VmError::with_loc("list index out of range", loc.clone())
                                })?,
                            _ => return Err(VmError::with_loc("not a List", loc.clone())),
                        },
                        None => return Err(VmError::with_loc("not a List", loc.clone())),
                    }
                };
                self.push(v);
            }
            ListSet => {
                let val = self.pop();
                let iv = self.pop();
                let idx = self.long_of(&iv)?;
                let list = self.pop();
                let loc = self.current_location();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::List { items }) => {
                                *usize::try_from(idx)
                                    .ok()
                                    .and_then(|index| items.get_mut(index))
                                    .ok_or_else(|| {
                                        VmError::with_loc("list index out of range", loc.clone())
                                    })? = val;
                            }
                            _ => return Err(VmError::with_loc("not a List", loc.clone())),
                        },
                        None => return Err(VmError::with_loc("not a List", loc.clone())),
                    }
                }
                self.push(list);
            }
            ListRemove => {
                let iv = self.pop();
                let idx = self.long_of(&iv)?;
                let list = self.pop();
                let loc = self.current_location();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::List { items }) => {
                                if idx < 0 || idx as usize >= items.len() {
                                    return Err(VmError::with_loc(
                                        "list index out of range",
                                        loc.clone(),
                                    ));
                                }
                                items.remove(idx as usize);
                            }
                            _ => return Err(VmError::with_loc("not a List", loc.clone())),
                        },
                        None => return Err(VmError::with_loc("not a List", loc.clone())),
                    }
                }
                self.push(list);
            }
            ListContains => {
                let v = self.pop();
                let list = self.pop();
                let found = {
                    let heap = self.heap();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => {
                                items.iter().any(|x| Self::values_equal(&heap, x, &v))
                            }
                            _ => return Err(VmError::new("not a List")),
                        },
                        None => return Err(VmError::new("not a List")),
                    }
                };
                self.push(Value::Bool(found));
            }
            ListIndexOf => {
                let v = self.pop();
                let list = self.pop();
                let pos = {
                    let heap = self.heap();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => items
                                .iter()
                                .position(|x| Self::values_equal(&heap, x, &v))
                                .map(|p| p as i64)
                                .unwrap_or(-1),
                            _ => return Err(VmError::new("not a List")),
                        },
                        None => return Err(VmError::new("not a List")),
                    }
                };
                self.push(Value::Long(pos));
            }
            ListReverse | ListSort | ListClear => {
                let list = self.pop();
                if matches!(op, ListSort) {
                    // Read items + display keys first, then sort in place.
                    let (r, snapshot) = {
                        let heap = self.heap();
                        match Self::ref_of(&list) {
                            Some(r) => match heap.get(r) {
                                Some(HeapObject::List { items }) => (r, items.clone()),
                                _ => return Err(VmError::new("not a List")),
                            },
                            None => return Err(VmError::new("not a List")),
                        }
                    };
                    let keys: Vec<String> = {
                        let heap = self.heap();
                        snapshot.iter().map(|v| v.to_display(&heap)).collect()
                    };
                    let mut order: Vec<usize> = (0..snapshot.len()).collect();
                    order.sort_by(|&x, &y| keys[x].cmp(&keys[y]));
                    let sorted: Vec<Value> = order.into_iter().map(|i| snapshot[i]).collect();
                    {
                        let mut heap = self.heap_mut();
                        if let Some(HeapObject::List { items }) = heap.get_mut(r) {
                            *items = sorted;
                        }
                    }
                } else {
                    {
                        let mut heap = self.heap_mut();
                        match Self::ref_of(&list) {
                            Some(r) => match heap.get_mut(r) {
                                Some(HeapObject::List { items }) => match op {
                                    ListReverse => items.reverse(),
                                    _ => items.clear(),
                                },
                                _ => return Err(VmError::new("not a List")),
                            },
                            None => return Err(VmError::new("not a List")),
                        }
                    }
                }
                self.push(list);
            }
            ListLen => {
                let list = self.pop();
                let n = {
                    let heap = self.heap();
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => items.len() as i64,
                            _ => return Err(VmError::new("not a List")),
                        },
                        None => return Err(VmError::new("not a List")),
                    }
                };
                self.push(Value::Long(n));
            }
            ListJoin => {
                let sep = self.pop();
                let list = self.pop();
                let text = {
                    let heap = self.heap();
                    let sep_t = match Self::ref_of(&sep) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::String { text }) => text.clone(),
                            _ => return Err(VmError::new("expected String separator")),
                        },
                        None => return Err(VmError::new("expected String separator")),
                    };
                    match Self::ref_of(&list) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::List { items }) => items
                                .iter()
                                .map(|v| v.to_display(&heap))
                                .collect::<Vec<_>>()
                                .join(&sep_t),
                            _ => return Err(VmError::new("not a List")),
                        },
                        None => return Err(VmError::new("not a List")),
                    }
                };
                let v = self.make_string(text);
                self.push(v);
            }
            MapPut => {
                let val = self.pop();
                let key = self.pop();
                let map = self.pop();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&map) {
                        Some(r) => {
                            // Locate the entry with an immutable view first,
                            // then mutate through the handle.
                            let pos = match heap.get(r) {
                                Some(HeapObject::Map { entries }) => entries
                                    .iter()
                                    .position(|(k, _)| Self::values_equal(&heap, k, &key)),
                                _ => return Err(VmError::new("not a Map")),
                            };
                            match heap.get_mut(r) {
                                Some(HeapObject::Map { entries }) => match pos {
                                    Some(p) => entries[p].1 = val,
                                    None => entries.push((key, val)),
                                },
                                _ => return Err(VmError::new("not a Map")),
                            }
                        }
                        None => return Err(VmError::new("not a Map")),
                    }
                }
                self.push(map);
            }
            MapGet => {
                let key = self.pop();
                let map = self.pop();
                let v = {
                    let heap = self.heap();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Map { entries }) => entries
                                .iter()
                                .find(|(k, _)| Self::values_equal(&heap, k, &key))
                                .map(|(_, v)| *v)
                                .unwrap_or(Value::Null),
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                };
                self.push(v);
            }
            MapRemove => {
                let key = self.pop();
                let map = self.pop();
                // Content equality (strings by text), like every other map
                // operation: locate first through an immutable view.
                let drop_idx: Vec<usize> = {
                    let heap = self.heap();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Map { entries }) => entries
                                .iter()
                                .enumerate()
                                .filter(|(_, (k, _))| Self::values_equal(&heap, k, &key))
                                .map(|(i, _)| i)
                                .collect(),
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                };
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&map).and_then(|r| heap.get_mut(r)) {
                        Some(HeapObject::Map { entries }) => {
                            for i in drop_idx.into_iter().rev() {
                                entries.remove(i);
                            }
                        }
                        _ => return Err(VmError::new("not a Map")),
                    }
                }
                self.push(map);
            }
            MapContainsKey => {
                let key = self.pop();
                let map = self.pop();
                let found = {
                    let heap = self.heap();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Map { entries }) => entries
                                .iter()
                                .any(|(k, _)| Self::values_equal(&heap, k, &key)),
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                };
                self.push(Value::Bool(found));
            }
            MapLen => {
                let map = self.pop();
                let n = {
                    let heap = self.heap();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Map { entries }) => entries.len() as i64,
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                };
                self.push(Value::Long(n));
            }
            MapKeys | MapValues => {
                let map = self.pop();
                let items = {
                    let heap = self.heap();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Map { entries }) => entries
                                .iter()
                                .map(|e| if matches!(op, MapKeys) { e.0 } else { e.1 })
                                .collect(),
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                };
                let r = self.heap_mut().alloc(HeapObject::List { items });
                self.push(Value::Object(r));
            }
            MapClear => {
                let map = self.pop();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&map) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::Map { entries }) => entries.clear(),
                            _ => return Err(VmError::new("not a Map")),
                        },
                        None => return Err(VmError::new("not a Map")),
                    }
                }
                self.push(map);
            }
            StackPush => {
                let item = self.pop();
                let stack = self.pop();
                {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::Stack { items }) => items.push(item),
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                }
                self.push(stack);
            }
            StackPop => {
                let stack = self.pop();
                let v = {
                    let mut heap = self.heap_mut();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::Stack { items }) => items.pop().unwrap_or(Value::Null),
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                };
                self.push(v);
            }
            StackPeek => {
                let stack = self.pop();
                let v = {
                    let heap = self.heap();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Stack { items }) => {
                                items.last().copied().unwrap_or(Value::Null)
                            }
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                };
                self.push(v);
            }
            StackGet => {
                let iv = self.pop();
                let idx = self.long_of(&iv)?;
                let stack = self.pop();
                let v = {
                    let heap = self.heap();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Stack { items }) => *usize::try_from(idx)
                                .ok()
                                .and_then(|index| items.get(index))
                                .ok_or_else(|| VmError::new("stack index out of range"))?,
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                };
                self.push(v);
            }
            StackLen => {
                let stack = self.pop();
                let n = {
                    let heap = self.heap();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Stack { items }) => items.len() as i64,
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                };
                self.push(Value::Long(n));
            }
            StackEmpty => {
                let stack = self.pop();
                let empty = {
                    let heap = self.heap();
                    match Self::ref_of(&stack) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Stack { items }) => items.is_empty(),
                            _ => return Err(VmError::new("not a Stack")),
                        },
                        None => return Err(VmError::new("not a Stack")),
                    }
                };
                self.push(Value::Bool(empty));
            }
            // ---- strings -------------------------------------------------------
            StrLen => {
                let s = self.pop();
                let n = {
                    let heap = self.heap();
                    match Self::ref_of(&s) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::String { text }) => text.chars().count() as i64,
                            _ => return Err(VmError::new("expected String")),
                        },
                        None => return Err(VmError::new("expected String")),
                    }
                };
                self.push(Value::Long(n));
            }
            StrConcat => {
                let b = self.pop();
                let a_ = self.pop();
                let (ta, tb) = self.displays(&a_, &b);
                let mut t = ta;
                t.push_str(&tb);
                let v = self.make_string(t);
                self.push(v);
            }
            StrSubstr => {
                let endtmp = self.pop();
                let end = self.long_of(&endtmp)?;
                let starttmp = self.pop();
                let start = self.long_of(&starttmp)?;
                let s = self.pop();
                let text = {
                    let heap = self.heap();
                    match Self::ref_of(&s) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::String { text }) => text.clone(),
                            _ => return Err(VmError::new("expected String")),
                        },
                        None => return Err(VmError::new("expected String")),
                    }
                };
                let chars: Vec<char> = text.chars().collect();
                let n = chars.len() as i64;
                let lo = start.clamp(0, n).max(0);
                let hi = end.clamp(0, n);
                if lo > hi {
                    return Err(self.err_at("substring start is after end"));
                }
                let v = self.make_string(chars[lo as usize..hi as usize].iter().collect());
                self.push(v);
            }
            StrContains | StrStartsWith | StrEndsWith => {
                let x = self.pop();
                let s = self.pop();
                let (ta, tb) = self.strs(&s, &x)?;
                let r = match op {
                    StrContains => ta.contains(tb.as_str()),
                    StrStartsWith => ta.starts_with(tb.as_str()),
                    _ => ta.ends_with(tb.as_str()),
                };
                self.push(Value::Bool(r));
            }
            StrSplit => {
                let sep = self.pop();
                let s = self.pop();
                let (ta, tb) = self.strs(&s, &sep)?;
                let items: Vec<Value> = ta
                    .split(tb.as_str())
                    .map(|p| self.make_string(p.to_string()))
                    .collect();
                let r = self.heap_mut().alloc(HeapObject::List { items });
                self.push(Value::Object(r));
            }
            StrReplace => {
                let to = self.pop();
                let from = self.pop();
                let s = self.pop();
                let (ta, tf, tt) = {
                    let (a, b) = self.strs(&s, &from)?;
                    (a, b, self.str_of(&to)?)
                };
                let v = self.make_string(ta.replace(tf.as_str(), tt.as_str()));
                self.push(v);
            }
            StrTrim | StrUpper | StrLower => {
                let s = self.pop();
                let text = self.str_of(&s)?;
                let out = match op {
                    StrTrim => text.trim().to_string(),
                    StrUpper => text.to_uppercase(),
                    _ => text.to_lowercase(),
                };
                let v = self.make_string(out);
                self.push(v);
            }
            StrIndex => {
                let x = self.pop();
                let s = self.pop();
                let (ta, tb) = self.strs(&s, &x)?;
                let pos = ta
                    .find(tb.as_str())
                    .map(|b| ta[..b].chars().count() as i64)
                    .unwrap_or(-1);
                self.push(Value::Long(pos));
            }
            StrCharAt => {
                let itmp = self.pop();
                let i = self.long_of(&itmp)?;
                let s = self.pop();
                let text = self.str_of(&s)?;
                match usize::try_from(i)
                    .ok()
                    .and_then(|index| text.chars().nth(index))
                {
                    Some(c) => self.push(Value::Char(c)),
                    None => return Err(self.err_at("char index out of range")),
                }
            }
            // ---- enums -----------------------------------------------------------
            NewEnum => {
                let payload = if a[2] != 0 { Some(self.pop()) } else { None };
                let r = self.heap_mut().alloc(HeapObject::Enum {
                    enum_id: a[0] as u16,
                    index: a[1] as u8,
                    payload,
                });
                self.push(Value::Object(r));
            }
            EnumIndex => {
                let e = self.pop();
                let idx = {
                    let heap = self.heap();
                    match Self::ref_of(&e) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Enum { index, .. }) => *index as i64,
                            _ => return Err(VmError::new("not an Enum")),
                        },
                        None => return Err(VmError::new("not an Enum")),
                    }
                };
                self.push(Value::Long(idx));
            }
            EnumPayload => {
                let e = self.pop();
                let p = {
                    let heap = self.heap();
                    match Self::ref_of(&e) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Enum { payload, .. }) => {
                                payload.unwrap_or(Value::Null)
                            }
                            _ => return Err(VmError::new("not an Enum")),
                        },
                        None => return Err(VmError::new("not an Enum")),
                    }
                };
                self.push(p);
            }
            // ---- exceptions ---------------------------------------------------------
            Throw => {
                let v = self.pop();
                self.do_throw(v)?;
            }
            TryBegin => {
                self.try_regions.push(TryRegion {
                    catch_ip: a[0],
                    // Offset 0 encodes "no finally"; handler regions are
                    // always emitted after the TryBegin itself.
                    finally_ip: if a[1] == 0 { None } else { Some(a[1]) },
                    stack_base: self.stack.len(),
                    frame_depth: self.frames.len(),
                });
            }
            TryEnd => {
                self.try_regions.pop();
            }
            FinallyDivert => {
                // break/continue trampoline: run the innermost enclosing
                // finally, then resume at the next instruction.
                let resume = self.frames.last().unwrap().ip;
                if self.divert_to_finally() {
                    self.finally_resume = Some(resume);
                }
            }
            FinallyEnd => {
                if let Some(e) = self.rethrow.take() {
                    self.do_throw(e)?;
                } else if self.pending_return.is_some() {
                    if !self.divert_to_finally() {
                        match self.pending_return.take().unwrap() {
                            PendingReturn::Void => {
                                self.return_from_frame()?;
                            }
                            PendingReturn::Value(v) => {
                                self.return_from_frame()?;
                                self.push(v);
                            }
                        }
                    }
                    // else: an outer finally will complete the return.
                } else if let Some(resume) = self.finally_resume.take() {
                    self.frames.last_mut().unwrap().ip = resume;
                }
            }
            // ---- termination ----------------------------------------------------------
            Return => {
                let v = self.pop();
                if self.divert_to_finally() {
                    // A return inside a finally suppresses any exception
                    // that was passing through it.
                    self.rethrow = None;
                    self.finally_resume = None;
                    self.pending_return = Some(PendingReturn::Value(v));
                } else {
                    self.return_from_frame()?;
                    self.push(v);
                }
            }
            ReturnVoid => {
                if self.divert_to_finally() {
                    self.rethrow = None;
                    self.finally_resume = None;
                    self.pending_return = Some(PendingReturn::Void);
                } else {
                    self.return_from_frame()?;
                }
            }
            // ---- stack manipulation -------------------------------------------------------
            Dup => {
                let top = self.stack.last().copied().unwrap_or(Value::Null);
                self.push(top);
            }
            // ---- housekeeping ------------------------------------------------------------
            GcHint => {
                self.maybe_gc();
            }
        }
        Ok(())
    }

    // ---- typed comparison helpers ------------------------------------------

    fn cmp_int(&mut self, lt: bool, or_eq: bool) -> Result<(), VmError> {
        let btmp = self.pop();
        let b = self.long_of(&btmp)?;
        let a_tmp = self.pop();
        let a_ = self.long_of(&a_tmp)?;
        let r = if lt {
            if or_eq {
                a_ <= b
            } else {
                a_ < b
            }
        } else if or_eq {
            a_ >= b
        } else {
            a_ > b
        };
        self.push(Value::Bool(r));
        Ok(())
    }

    fn cmp_float(&mut self, lt: bool, or_eq: bool) -> Result<(), VmError> {
        let btmp = self.pop();
        let b = self.double_of(&btmp)?;
        let a_tmp = self.pop();
        let a_ = self.double_of(&a_tmp)?;
        let r = if lt {
            if or_eq {
                a_ <= b
            } else {
                a_ < b
            }
        } else if or_eq {
            a_ >= b
        } else {
            a_ > b
        };
        self.push(Value::Bool(r));
        Ok(())
    }

    fn cmp_char(&mut self, lt: bool, or_eq: bool) -> Result<(), VmError> {
        let btmp = self.pop();
        let b = self.char_of(&btmp)?;
        let a_tmp = self.pop();
        let a_ = self.char_of(&a_tmp)?;
        let r = if lt {
            if or_eq {
                a_ <= b
            } else {
                a_ < b
            }
        } else if or_eq {
            a_ >= b
        } else {
            a_ > b
        };
        self.push(Value::Bool(r));
        Ok(())
    }

    fn cmp_string(&mut self, lt: bool, or_eq: bool) -> Result<(), VmError> {
        let b = self.pop();
        let a_ = self.pop();
        let (ta, tb) = self.strs(&a_, &b)?;
        let r = if lt {
            if or_eq {
                ta <= tb
            } else {
                ta < tb
            }
        } else if or_eq {
            ta >= tb
        } else {
            ta > tb
        };
        self.push(Value::Bool(r));
        Ok(())
    }

    fn cmp_dyn(&mut self, lt: bool, or_eq: bool) -> Result<(), VmError> {
        let b = self.pop();
        let a_ = self.pop();
        let (ta, tb) = self.displays(&a_, &b);
        let r = if lt {
            if or_eq {
                ta <= tb
            } else {
                ta < tb
            }
        } else if or_eq {
            ta >= tb
        } else {
            ta > tb
        };
        self.push(Value::Bool(r));
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ir::IrOp;
    use crate::stdlib::builtins::nat;

    fn test_vm() -> Vm {
        vm_with_constants(vec![])
    }

    fn vm_with_constants(constants: Vec<ConstVal>) -> Vm {
        Vm::new(SharedState::new(CodeModule {
            version: CodeModule::FORMAT_VERSION,
            constants,
            functions: vec![],
            classes: vec![],
            interfaces: vec![],
            dyn_names: vec![],
            entry: None,
            sources: vec![],
        }))
    }

    #[test]
    fn new_stack_creates_stack_object() {
        let mut vm = test_vm();
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::NewStack, &[]).unwrap();
        let Value::Object(stack) = vm.pop() else {
            panic!("expected stack object")
        };
        assert!(
            matches!(vm.heap().get(stack), Some(HeapObject::Stack { items }) if items.is_empty())
        );
    }

    #[test]
    fn list_spread_does_not_consume_source() {
        let mut vm = test_vm();
        let list = vm.heap_mut().alloc(HeapObject::List {
            items: vec![Value::Long(1), Value::Long(2)],
        });
        vm.push(Value::Object(list));
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::ListSpread, &[]).unwrap();
        assert!(matches!(
            vm.heap().get(list),
            Some(HeapObject::List { items }) if items == &vec![Value::Long(1), Value::Long(2)]
        ));
        assert_eq!(vm.pop(), Value::Long(2));
        assert_eq!(vm.pop(), Value::Long(1));
    }

    #[test]
    fn semaphore_rejects_invalid_counts() {
        let mut vm = test_vm();
        for value in [Value::Long(-1), Value::Long(i64::from(i32::MAX) + 1)] {
            assert!(natives::call_native(&mut vm, nat::SEM_NEW, &[value]).is_err());
        }
    }

    #[test]
    fn random_seed_repeats_integer_and_float_sequences() {
        let mut vm = test_vm();
        natives::call_native(&mut vm, nat::RANDOM_SEED, &[Value::Long(42)]).unwrap();
        let first_int =
            natives::call_native(&mut vm, nat::RANDOM_NEXT_LONG, &[Value::Long(100)]).unwrap();
        let first_float = natives::call_native(&mut vm, nat::RANDOM_NEXT_DOUBLE, &[]).unwrap();
        natives::call_native(&mut vm, nat::RANDOM_SEED, &[Value::Long(42)]).unwrap();
        assert_eq!(
            natives::call_native(&mut vm, nat::RANDOM_NEXT_LONG, &[Value::Long(100)]).unwrap(),
            first_int
        );
        assert_eq!(
            natives::call_native(&mut vm, nat::RANDOM_NEXT_DOUBLE, &[]).unwrap(),
            first_float
        );
    }

    #[test]
    fn process_wait_returns_and_preserves_status() {
        let mut vm = test_vm();
        let process = Value::Object(vm.heap_mut().alloc(HeapObject::Process {
            child: None,
            exit_code: Some(7),
            cmd: None,
            argv: vec![],
            stdin: None,
            stdout: None,
            stderr: None,
        }));
        for _ in 0..2 {
            assert_eq!(
                natives::call_native(&mut vm, nat::PROC_WAIT, &[process]).unwrap(),
                Value::Long(7)
            );
            assert_eq!(
                natives::call_native(&mut vm, nat::PROC_EXIT_CODE, &[process]).unwrap(),
                Value::Long(7)
            );
        }
    }

    #[test]
    fn waiting_before_start_does_not_change_lifecycle() {
        let mut vm = test_vm();
        let process = Value::Object(vm.heap_mut().alloc(HeapObject::Process {
            child: None,
            exit_code: None,
            cmd: None,
            argv: vec![],
            stdin: None,
            stdout: None,
            stderr: None,
        }));
        assert!(natives::call_native(&mut vm, nat::PROC_WAIT, &[process]).is_err());
        assert!(natives::call_native(&mut vm, nat::PROC_EXIT_CODE, &[process]).is_err());
    }

    #[test]
    fn joining_before_start_does_not_change_lifecycle() {
        let mut vm = test_vm();
        let thread = Value::Object(vm.heap_mut().alloc(HeapObject::Thread {
            runnable: Some(0),
            handle: None,
            done: false,
        }));
        assert!(natives::call_native(&mut vm, nat::THREAD_JOIN, &[thread]).is_err());
        assert!(matches!(
            vm.heap().get(thread.as_object().unwrap()),
            Some(HeapObject::Thread {
                done: false,
                runnable: Some(0),
                ..
            })
        ));
    }

    fn load_constant(vm: &mut Vm, id: u32) -> Value {
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::LoadConst, &[id]).unwrap();
        vm.pop()
    }

    #[test]
    #[ignore = "informational timing; run explicitly in release mode with --nocapture"]
    fn literal_pool_profile() {
        use std::time::Instant;
        let constants = (0..10_000)
            .map(|i| ConstVal::Str(format!("{i:08}{}", "x".repeat(1016))))
            .collect();
        let start = Instant::now();
        let mut vm = vm_with_constants(constants);
        let startup = start.elapsed();
        let payload_bytes: usize = vm
            .shared
            .str_consts
            .iter()
            .flatten()
            .map(|r| match vm.heap().get(*r).unwrap() {
                HeapObject::String { text } => text.len(),
                _ => unreachable!(),
            })
            .sum();
        let count = vm.heap().live_count();
        let start = Instant::now();
        for _ in 0..100_000 {
            std::hint::black_box(load_constant(&mut vm, 0));
        }
        let loads = start.elapsed();
        assert_eq!(vm.heap().live_count(), count);
        let start = Instant::now();
        for _ in 0..1000 {
            vm.push(Value::Object(vm.shared.str_consts[0].unwrap()));
            vm.push(Value::Object(vm.shared.str_consts[1].unwrap()));
            let module = vm.shared.module.clone();
            vm.step(&module, IrOp::StrConcat, &[]).unwrap();
            std::hint::black_box(vm.pop());
        }
        let concat = start.elapsed();
        // Collection is explicitly eligible here, unlike run_main today.
        vm.maybe_gc();
        assert_eq!(vm.heap().live_count(), count);
        eprintln!("10,000 unique 1KiB literals: startup={startup:?}, retained string payload={payload_bytes} bytes (excludes module/cache/object overhead); 100,000 loads={loads:?}; 1,000 runtime concats={concat:?}; forced GC reclaimed concat results");
    }

    #[test]
    fn literal_loads_share_across_call_frames() {
        let mut module = CodeModule {
            version: CodeModule::FORMAT_VERSION,
            constants: vec![ConstVal::Str("shared".into())],
            functions: vec![],
            classes: vec![],
            interfaces: vec![],
            dyn_names: vec![],
            entry: None,
            sources: vec![],
        };
        module.functions.push(crate::bytecode::CodeFunction {
            name: "literal".into(),
            params: vec![],
            local_count: 0,
            returns_value: true,
            code: vec![IrOp::LoadConst.code(), 0, 0, 0, 0, IrOp::Return.code()],
            line_map: vec![],
            source_file: 0,
        });
        let mut vm = Vm::new(SharedState::new(module));
        let expected = Value::Object(vm.shared.str_consts[0].unwrap());
        let count = vm.heap().live_count();
        for _ in 0..100 {
            vm.call_function(0, &[], None).unwrap();
            vm.execute().unwrap();
            assert_eq!(vm.pop(), expected);
            assert!(vm.frames.is_empty());
        }
        assert_eq!(vm.heap().live_count(), count);
    }

    #[test]
    fn literal_pool_roundtrip_preserves_exact_text_and_scalar_slots() {
        let texts = [
            "".to_string(),
            "a\0b\n".into(),
            "é".into(),
            "e\u{301}".into(),
            "界🙂".into(),
            "x".repeat(100_000),
        ];
        let mut constants = vec![
            ConstVal::Null,
            ConstVal::Bool(true),
            ConstVal::Long(-7),
            ConstVal::Double(1.5),
            ConstVal::Char('界'),
        ];
        for text in &texts {
            constants.push(ConstVal::Str(text.clone()));
            constants.push(ConstVal::Str(text.clone()));
        }
        let original = vm_with_constants(constants);
        let bytes = crate::bytecode::encode::encode(&original.shared.module);
        let mut vm = Vm::new(SharedState::new(
            crate::bytecode::decode::decode(&bytes).unwrap(),
        ));
        assert_eq!(vm.heap().live_count(), texts.len() + 3);
        for (id, expected) in [
            Value::Null,
            Value::Bool(true),
            Value::Long(-7),
            Value::Double(1.5),
            Value::Char('界'),
        ]
        .into_iter()
        .enumerate()
        {
            assert_eq!(load_constant(&mut vm, id as u32), expected);
            assert!(vm.shared.str_consts[id].is_none());
        }
        for (i, text) in texts.iter().enumerate() {
            let id = 5 + i as u32 * 2;
            let a = load_constant(&mut vm, id);
            assert_eq!(a, load_constant(&mut vm, id + 1));
            assert_eq!(vm.str_of(&a).unwrap(), *text);
            for _ in 0..100 {
                assert_eq!(a, load_constant(&mut vm, id));
            }
        }
        assert_ne!(vm.shared.str_consts[9], vm.shared.str_consts[11]);
        assert_eq!(vm.heap().live_count(), texts.len() + 3);
        assert!(!vm.heap().gc_due());
        assert!(test_vm().shared.str_consts.is_empty());
        assert!(vm_with_constants(vec![ConstVal::Null]).shared.str_consts[0].is_none());
    }

    #[test]
    fn literal_pool_survives_collection_reuse_and_worker_lifetime() {
        let mut vm = vm_with_constants(
            (0..4200)
                .map(|i| ConstVal::Str(format!("literal {i}")))
                .collect(),
        );
        let expected = vm.shared.str_consts.clone();
        // No literal has ever been put on a VM stack. Startup alone makes GC due.
        let dead = vm.alloc_string("garbage");
        vm.maybe_gc();
        assert!(
            vm.heap().get(dead).is_none(),
            "collection must actually sweep"
        );
        let reused = vm.alloc_string("replacement");
        assert_eq!(dead, reused);
        let shared = vm.shared.clone();
        vm.shared.active_threads.fetch_add(1, Ordering::SeqCst);
        let worker = std::thread::spawn(move || {
            let mut worker = Vm::new(shared);
            let cache = worker.shared.str_consts.clone();
            assert_eq!(
                load_constant(&mut worker, 0),
                Value::Object(cache[0].unwrap())
            );
            for _ in 0..4200 {
                worker.alloc_string("worker garbage");
            }
            worker.shared.active_threads.fetch_sub(1, Ordering::SeqCst);
            cache
        });
        assert!(Arc::ptr_eq(&expected, &worker.join().unwrap()));
        vm.maybe_gc();
        assert!(vm.heap().get(reused).is_none());
        for (i, r) in expected.iter().enumerate() {
            let value = load_constant(&mut vm, i as u32);
            assert_eq!(value, Value::Object(r.unwrap()));
            assert_eq!(vm.str_of(&value).unwrap(), format!("literal {i}"));
        }
        assert_eq!(vm.heap().live_count(), 4203);
    }

    #[test]
    fn literal_operations_preserve_aliases_and_runtime_content_equality() {
        let mut vm = vm_with_constants(vec![ConstVal::Str("héllo".into())]);
        let literal = load_constant(&mut vm, 0);
        let runtime = vm.make_string("héllo".into());
        assert_ne!(literal, runtime);
        assert!(Vm::values_equal(&vm.heap(), &literal, &runtime));
        let module = vm.shared.module.clone();
        vm.push(literal);
        vm.push(runtime);
        vm.step(&module, IrOp::StrConcat, &[]).unwrap();
        let concat = vm.pop();
        assert_eq!(vm.str_of(&concat).unwrap(), "héllohéllo");
        vm.push(literal);
        vm.push(Value::Long(1));
        vm.push(Value::Long(2));
        vm.step(&module, IrOp::StrSubstr, &[]).unwrap();
        let substring = vm.pop();
        assert_eq!(vm.str_of(&substring).unwrap(), "é");
        assert_eq!(load_constant(&mut vm, 0), literal);
        assert_eq!(vm.str_of(&literal).unwrap(), "héllo");
        let a = vm.heap_mut().alloc(HeapObject::Enum {
            enum_id: 0,
            index: 0,
            payload: Some(literal),
        });
        let b = vm.heap_mut().alloc(HeapObject::Enum {
            enum_id: 0,
            index: 0,
            payload: Some(runtime),
        });
        assert!(Vm::values_equal(
            &vm.heap(),
            &Value::Object(a),
            &Value::Object(b)
        ));
    }

    #[test]
    fn literal_cache_invalid_state_errors_without_fallback() {
        let mut vm = vm_with_constants(vec![ConstVal::Str("literal".into())]);
        let module = vm.shared.module.clone();
        let count = vm.heap().live_count();
        assert_eq!(
            vm.step(&module, IrOp::LoadConst, &[1]).unwrap_err().message,
            "constant index out of range"
        );
        vm.shared.str_consts = vec![None].into();
        assert!(vm
            .step(&module, IrOp::LoadConst, &[0])
            .unwrap_err()
            .message
            .contains("missing string constant"));
        vm.shared.str_consts = vec![].into();
        assert!(vm
            .step(&module, IrOp::LoadConst, &[0])
            .unwrap_err()
            .message
            .contains("cache length mismatch"));
        assert_eq!(vm.heap().live_count(), count);
        assert!(vm.stack.is_empty());
    }

    #[test]
    fn literal_caches_are_owned_by_independent_programs() {
        let mut first = vm_with_constants(vec![ConstVal::Str("first".into())]);
        let mut second = vm_with_constants(vec![ConstVal::Str("second".into())]);
        assert!(!Arc::ptr_eq(&first.shared.heap, &second.shared.heap));
        assert!(!Arc::ptr_eq(
            &first.shared.str_consts,
            &second.shared.str_consts
        ));
        let a = load_constant(&mut first, 0);
        let b = load_constant(&mut second, 0);
        assert_eq!(first.str_of(&a).unwrap(), "first");
        let weak_heap = Arc::downgrade(&first.shared.heap);
        let weak_cache = Arc::downgrade(&first.shared.str_consts);
        drop(first);
        assert!(weak_heap.upgrade().is_none());
        assert!(weak_cache.upgrade().is_none());
        assert_eq!(second.str_of(&b).unwrap(), "second");
    }

    #[test]
    fn gc_roots_globals_and_keeps_streams_alive() {
        let mut vm = test_vm();
        // Force a collection to be due, then drop every live reference so
        // only the global stream bindings keep their objects alive.
        let mut dead = Vec::new();
        for _ in 0..4200 {
            dead.push(vm.heap_mut().alloc(HeapObject::String { text: "x".into() }));
        }
        let first_dead = dead[0];
        drop(dead);
        vm.maybe_gc();
        for g in &vm.globals {
            let Value::Object(r) = g else {
                panic!("global is not an object")
            };
            assert!(
                matches!(vm.heap().get(*r), Some(HeapObject::Stream { .. })),
                "stream global was collected by GC"
            );
        }
        assert!(
            vm.heap().get(first_dead).is_none(),
            "unreachable object survived"
        );
    }

    #[test]
    fn map_remove_opcode_uses_content_equality() {
        let mut vm = test_vm();
        // Two distinct String objects with the same text: the stored key
        // and the removal key.
        let key_a = vm.make_string("a".into());
        let key_b = vm.make_string("a".into());
        let key_c = vm.make_string("b".into());
        let map = Value::Object(vm.heap_mut().alloc(HeapObject::Map {
            entries: vec![(key_a, Value::Long(1)), (key_c, Value::Long(2))],
        }));
        vm.stack = vec![map, key_b];
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::MapRemove, &[]).unwrap();
        let Value::Object(r) = map else {
            unreachable!()
        };
        let heap = vm.heap();
        let Some(HeapObject::Map { entries }) = heap.get(r) else {
            unreachable!()
        };
        assert_eq!(entries.len(), 1);
        assert!(matches!(entries[0].1, Value::Long(2)));
    }

    #[test]
    fn stack_get_rejects_negative_index() {
        let mut vm = test_vm();
        let stack = Value::Object(vm.heap_mut().alloc(HeapObject::Stack {
            items: vec![Value::Long(7)],
        }));
        for idx in [i64::MIN, -1] {
            vm.stack = vec![stack, Value::Long(idx)];
            let module = vm.shared.module.clone();
            assert!(
                vm.step(&module, IrOp::StackGet, &[]).is_err(),
                "index {idx}"
            );
        }
        vm.stack = vec![stack, Value::Long(0)];
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::StackGet, &[]).unwrap();
        assert!(matches!(vm.pop(), Value::Long(7)));
    }

    #[test]
    fn json_rejects_cycles_but_accepts_shared_children() {
        let mut vm = test_vm();
        let child = Value::Object(vm.heap_mut().alloc(HeapObject::List {
            items: vec![Value::Long(7)],
        }));
        let parent = Value::Object(vm.heap_mut().alloc(HeapObject::List {
            items: vec![child, child],
        }));
        let json = natives::call_native(&mut vm, nat::JSON_STRINGIFY, &[parent]).unwrap();
        assert_eq!(vm.str_of(&json).unwrap(), "[[7],[7]]");
        let Value::Object(child_ref) = child else {
            unreachable!()
        };
        if let Some(HeapObject::List { items }) = vm.heap_mut().get_mut(child_ref) {
            items.push(parent);
        }
        assert_eq!(
            natives::call_native(&mut vm, nat::JSON_STRINGIFY, &[parent])
                .unwrap_err()
                .message,
            "cyclic value is not JSON-serializable"
        );
        for object in [
            HeapObject::Map {
                entries: vec![(Value::Long(1), Value::Null)],
            },
            HeapObject::Instance {
                class: 0,
                fields: vec![Value::Null],
            },
        ] {
            let r = vm.heap_mut().alloc(object);
            match vm.heap_mut().get_mut(r).unwrap() {
                HeapObject::Map { entries } => entries[0].1 = Value::Object(r),
                HeapObject::Instance { fields, .. } => fields[0] = Value::Object(r),
                _ => unreachable!(),
            }
            assert_eq!(
                natives::call_native(&mut vm, nat::JSON_STRINGIFY, &[Value::Object(r)])
                    .unwrap_err()
                    .message,
                "cyclic value is not JSON-serializable"
            );
        }
    }

    #[test]
    fn float_to_integer_conversion_checks_range() {
        let mut vm = test_vm();
        for input in [
            f64::NAN,
            f64::INFINITY,
            f64::NEG_INFINITY,
            9223372036854775808.0,
            -9223372036854777856.0,
        ] {
            assert!(
                natives::call_native(&mut vm, nat::CONV_LONG, &[Value::Double(input)]).is_err(),
                "accepted {input}"
            );
        }
        for (input, expected) in [
            (1.9, 1),
            (-1.9, -1),
            (-9223372036854775808.0, i64::MIN),
            (9223372036854774784.0, 9223372036854774784),
        ] {
            assert!(
                matches!(natives::call_native(&mut vm, nat::CONV_LONG, &[Value::Double(input)]).unwrap(), Value::Long(v) if v == expected)
            );
        }
    }

    #[test]
    fn concat_formats_exception_values() {
        let mut vm = test_vm();
        let prefix = vm.make_string("outer caught ".into());
        let exception = Value::Object(vm.heap_mut().alloc(HeapObject::Exception {
            message: "boom".into(),
        }));
        vm.stack = vec![prefix, exception];
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::StrConcat, &[]).unwrap();
        let result = vm.pop();
        assert_eq!(vm.str_of(&result).unwrap(), "outer caught boom");
    }

    #[test]
    fn index_bounds_in_natives_and_opcodes() {
        for (op, native, string, write) in [
            (IrOp::ListGet, nat::LIST_GET, false, false),
            (IrOp::ListSet, nat::LIST_SET, false, true),
            (IrOp::StrCharAt, nat::STR_CHAR_AT, true, false),
        ] {
            for index in [i64::MIN, -1, 0, 1, 2, i64::MAX] {
                for use_native in [false, true] {
                    let mut vm = test_vm();
                    let receiver = if string {
                        vm.make_string("é😀".into())
                    } else {
                        Value::Object(vm.heap_mut().alloc(HeapObject::List {
                            items: vec![Value::Long(7), Value::Long(8)],
                        }))
                    };
                    let mut args = vec![receiver, Value::Long(index)];
                    if write {
                        args.push(Value::Long(9));
                    }
                    let result = if use_native {
                        natives::call_native(&mut vm, native, &args).map(|_| ())
                    } else {
                        vm.stack = args;
                        let module = vm.shared.module.clone();
                        vm.step(&module, op, &[])
                    };
                    assert_eq!(
                        result.is_ok(),
                        (0..2).contains(&index),
                        "op={op:?} index={index} native={use_native}"
                    );
                    if write && !(0..2).contains(&index) {
                        let Value::Object(r) = receiver else {
                            unreachable!()
                        };
                        let heap = vm.heap();
                        let Some(HeapObject::List { items }) = heap.get(r) else {
                            unreachable!()
                        };
                        assert!(matches!(items[0], Value::Long(7)));
                    }
                }
            }
        }
    }
}
