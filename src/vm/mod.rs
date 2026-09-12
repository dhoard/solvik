//! The Solvik bytecode virtual machine.

pub mod collections;
pub mod frames;
pub mod heap;
pub mod natives;
pub mod streams;
pub mod value;

use std::collections::HashMap;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Condvar, Mutex};

use num_traits::{ToPrimitive, Zero};

use crate::bytecode::{CodeModule, ConstVal};
use crate::ir::IrOp;
use frames::{CallFrame, TryRegion};
use heap::{GcRef, Heap, HeapObject, HeapStats};
use value::Value;

/// One pre-decoded bytecode instruction.
///
/// Bytecode is decoded once per function when the shared state is created,
/// so the execution loop never pays for opcode lookup or operand decoding.
/// Control-flow operands (jump targets, try handlers) are
/// instruction indexes into the owning `FuncCode`, not byte offsets.
#[derive(Debug, Clone, Copy)]
struct DecodedInstr {
    op: IrOp,
    a0: u32,
    a1: u32,
    a2: u32,
}

/// Decoded code plus diagnostic metadata for one function.
struct FuncCode {
    instrs: Vec<DecodedInstr>,
    /// Source line of each instruction.
    lines: Vec<u32>,
}

/// Decode a function's raw bytecode. `Err` carries the byte offset of the
/// first malformed instruction.
fn decode_code(code: &[u8], line_map: &[(u32, u32)]) -> Result<FuncCode, u32> {
    let mut instrs: Vec<DecodedInstr> = Vec::with_capacity(code.len());
    let mut offsets: Vec<u32> = Vec::with_capacity(code.len());
    let mut ip = 0usize;
    while ip < code.len() {
        let start = ip;
        let op = IrOp::from_code(code[ip]).ok_or(start as u32)?;
        ip += 1;
        let mut a = [0u32; 3];
        for (i, slot) in a.iter_mut().enumerate().take(op.operand_count()) {
            let size = op.operand_size(i);
            if ip + size > code.len() {
                return Err(start as u32);
            }
            let mut v: u32 = 0;
            for k in 0..size {
                v |= (code[ip + k] as u32) << (8 * k);
            }
            *slot = v;
            ip += size;
        }
        offsets.push(start as u32);
        instrs.push(DecodedInstr {
            op,
            a0: a[0],
            a1: a[1],
            a2: a[2],
        });
    }
    // Byte offset -> instruction index, for rewriting control-flow operands.
    let off_to_idx: std::collections::HashMap<u32, usize> =
        offsets.iter().enumerate().map(|(i, &o)| (o, i)).collect();
    for (i, d) in instrs.iter_mut().enumerate() {
        match d.op {
            IrOp::Jump | IrOp::JumpIfFalse | IrOp::JumpIfTrue => {
                d.a0 = *off_to_idx.get(&d.a0).ok_or(offsets[i])? as u32;
            }
            // Offset 0 is the "no handler" sentinel for TryBegin and cannot
            // be a real target (handlers are emitted after the TryBegin).
            IrOp::TryBegin => {
                if d.a0 != 0 {
                    d.a0 = *off_to_idx.get(&d.a0).ok_or(offsets[i])? as u32;
                }
                if d.a1 != 0 {
                    d.a1 = *off_to_idx.get(&d.a1).ok_or(offsets[i])? as u32;
                }
            }
            _ => {}
        }
    }
    // Per-instruction source lines from the (byte offset, line) map.
    let mut lines = vec![0u32; instrs.len()];
    let mut cur = 0u32;
    let mut li = 0usize;
    for (i, &off) in offsets.iter().enumerate() {
        while li < line_map.len() && line_map[li].0 <= off {
            cur = line_map[li].1;
            li += 1;
        }
        lines[i] = cur;
    }
    Ok(FuncCode { instrs, lines })
}

/// Per-class initialization state shared by all Solvik threads of one
/// program. A class's static field initializers and its single static block
/// form one unit that runs at most once, immediately before the class's
/// first active use (static field access, static method call, object
/// construction, or entry-point dispatch).
#[derive(Debug, Clone)]
enum ClassInit {
    /// The class has not yet been actively used.
    Uninit,
    /// The owning thread is executing the class's synthetic initializer.
    Initializing(std::thread::ThreadId),
    /// The initializer completed; later active uses are no-ops.
    Initialized,
    /// The initializer failed; later active uses fail with this message
    /// without rerunning user code.
    Failed(String),
}

/// State shared between all Solvik threads of one program.
#[derive(Clone)]
pub struct SharedState {
    module: Arc<CodeModule>,
    heap: Arc<Mutex<Heap>>,
    /// Indexed by constant ID; None exactly for non-string constants.
    str_consts: Arc<[Option<GcRef>]>,
    /// Pre-decoded code per function id.
    decoded: Arc<Vec<Result<FuncCode, u32>>>,
    /// One initialization state per class; see `ClassInit`.
    class_init: Arc<Vec<Mutex<ClassInit>>>,
    /// Wakes threads waiting for any in-progress class initialization.
    /// Waiting always re-checks the owning class's state, so a single
    /// shared condvar is correct (spurious wakeups are harmless).
    class_init_wait: Arc<Condvar>,
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
        // Per-class static field storage, shared by all instances and all
        // threads. Slots start null; a class's synthetic initializer stores
        // real values on the class's first active use (lazy initialization).
        heap.statics = module
            .classes
            .iter()
            .map(|c| vec![Value::Null; c.static_fields.len()])
            .collect();
        let class_init = (0..module.classes.len())
            .map(|_| Mutex::new(ClassInit::Uninit))
            .collect::<Vec<_>>()
            .into();
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
        let decoded: Arc<Vec<Result<FuncCode, u32>>> = module
            .functions
            .iter()
            .map(|f| decode_code(&f.code, &f.line_map))
            .collect::<Vec<_>>()
            .into();
        Self {
            module: Arc::new(module),
            heap: Arc::new(Mutex::new(heap)),
            str_consts,
            decoded,
            class_init,
            class_init_wait: Arc::new(Condvar::new()),
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
}

/// The virtual machine: one per Solvik thread.
pub struct Vm {
    pub shared: SharedState,
    /// Contiguous value stack (frames' locals live here).
    stack: Vec<Value>,
    frames: Vec<CallFrame>,
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
    /// Temporaries popped from the VM stack during one instruction. A pop
    /// transfers the stack's ownership here; a push consumes a matching
    /// temporary. Anything left at the instruction boundary is released.
    temp_values: Vec<Value>,
    /// Scratch buffer for batching object-reference releases; reused across
    /// instructions so the hot path does not allocate.
    temp_refs: Vec<GcRef>,
}

/// A return intercepted by an enclosing finally block.
#[derive(Debug, Clone)]
enum PendingReturn {
    /// The function returns no value.
    Void,
    /// The function returns this value.
    Value(Value),
}

/// Fused instruction dispatch: expands the complete opcode match inline at
/// the call site. The hot execution loop (`execute`) and the single-step
/// test driver (`step`) share this one definition, so there is exactly one
/// implementation of every opcode.
macro_rules! vm_dispatch {
    ($vm:expr, $module:expr, $op:expr, $a0:expr, $a1:expr, $a2:expr) => {{
        use crate::ir::IrOp::*;
        match $op {
            // ---- constants / locals / globals --------------------------
            LoadConst => {
                let c = $module
                    .constants
                    .get($a0 as usize)
                    .ok_or_else(|| $vm.err_at("constant index out of range"))?;
                if $vm.shared.str_consts.len() != $module.constants.len() {
                    return Err(
                        $vm.err_at("internal VM invariant: string constant cache length mismatch")
                    );
                }
                match c {
                    ConstVal::Str(_) => {
                        let r = $vm
                            .shared
                            .str_consts
                            .get($a0 as usize)
                            .copied()
                            .flatten()
                            .ok_or_else(|| {
                                $vm.err_at("internal VM invariant: missing string constant")
                            })?;
                        $vm.push(Value::Object(r));
                    }
                    ConstVal::Null => $vm.push(Value::Null),
                    ConstVal::Bool(b) => $vm.push(Value::Boolean(*b)),
                    ConstVal::Byte(i) => $vm.push(Value::Byte(*i)),
                    ConstVal::Short(i) => $vm.push(Value::Short(*i)),
                    ConstVal::Integer(i) => $vm.push(Value::Integer(*i)),
                    ConstVal::Long(i) => $vm.push(Value::Long(*i)),
                    ConstVal::Float(f) => $vm.push(Value::Float(*f)),
                    ConstVal::Double(f) => $vm.push(Value::Double(*f)),
                    ConstVal::BigInt(s) => {
                        use crate::bignum::BigInt;
                        let value = BigInt::parse_bytes(s.as_bytes(), 10)
                            .unwrap_or_else(|| BigInt::from(0));
                        let r = $vm.alloc(HeapObject::BigInteger { value });
                        $vm.push(Value::Object(r));
                    }
                    ConstVal::BigDecimal(s) => {
                        use crate::bignum::Dec;
                        let value = Dec::parse(s).unwrap_or_else(|_| Dec::zero());
                        let r = $vm.alloc(HeapObject::BigDecimal { value });
                        $vm.push(Value::Object(r));
                    }
                    ConstVal::Char(ch) => $vm.push(Value::Char(*ch)),
                }
            }
            LoadLocal => {
                let base = $vm.frames.last().map(|f| f.base).unwrap_or(0);
                let idx = base + $a0 as usize;
                let v = *$vm
                    .stack
                    .get(idx)
                    .ok_or_else(|| $vm.err_at("local index out of range"))?;
                $vm.push(v);
            }
            StoreLocal => {
                let base = $vm.frames.last().map(|f| f.base).unwrap_or(0);
                let idx = base + $a0 as usize;
                let v = $vm.pop();
                let old = match $vm.stack.get_mut(idx) {
                    Some(slot) => {
                        let old = *slot;
                        *slot = v;
                        old
                    }
                    None => return Err($vm.err_at("local index out of range")),
                };
                $vm.finish_replacement(v, old);
            }
            // ---- arithmetic (runtime-dispatched on value kinds) ----------
            Add | Sub | Mul | Div | Mod => {
                let b = $vm.pop();
                let a_ = $vm.pop();
                let r = $vm.arith($op, &a_, &b)?;
                $vm.push(r);
            }
            Neg => {
                let v = $vm.pop();
                let r = $vm.negate(&v)?;
                $vm.push(r);
            }
            // ---- conversions --------------------------------------------
            Convert => {
                let v = $vm.pop();
                let r = $vm.convert(v, $a0 as u8)?;
                $vm.push(r);
            }
            // ---- logic / null -------------------------------------------
            Not => {
                let v = $vm.pop();
                $vm.push(Value::Boolean(!$vm.bool_of(&v)?));
            }
            And => {
                let b = $vm.pop();
                let a_ = $vm.pop();
                $vm.push(Value::Boolean($vm.bool_of(&a_)? && $vm.bool_of(&b)?));
            }
            Pop => {
                $vm.pop();
            }
            IsNull => {
                let v = $vm.pop();
                $vm.push(Value::Boolean(matches!(v, Value::Null)));
            }
            NullCheck => {
                let v = $vm.pop();
                if matches!(v, Value::Null) {
                    let r = $vm.alloc(HeapObject::Exception {
                        kind: crate::types::native_kind::EXCEPTION,
                        message: "null reference".to_string(),
                    });
                    $vm.do_throw(Value::Object(r))?;
                } else {
                    $vm.push(v);
                }
            }
            // ---- equality / ordering (runtime-dispatched) -----------------
            Eq => {
                let b = $vm.pop();
                let a_ = $vm.pop();
                let eq = {
                    let heap = $vm.heap();
                    Self::values_equal(&heap, &a_, &b)
                };
                $vm.push(Value::Boolean(eq));
            }
            Lt | Le | Gt | Ge => {
                $vm.cmp_values($op)?;
            }
            // ---- catch-clause matching -------------------------------------
            Conforms => {
                let v = $vm.pop();
                let r = $vm.conforms(&v, $a0 as u16, $a1 as u8)?;
                $vm.push(Value::Boolean(r));
            }

            // ---- control flow ---------------------------------------------
            Jump => {
                $vm.frames.last_mut().unwrap().ip = $a0;
            }
            JumpIfFalse => {
                let v = $vm.pop();
                if !$vm.bool_of(&v)? {
                    $vm.frames.last_mut().unwrap().ip = $a0;
                }
            }
            JumpIfTrue => {
                let v = $vm.pop();
                if $vm.bool_of(&v)? {
                    $vm.frames.last_mut().unwrap().ip = $a0;
                }
            }
            // ---- calls -------------------------------------------------------
            CallFn => {
                $vm.call_stack($a0, $a1 as usize)?;
            }
            CallStatic => {
                // The encoded target class owns the call: first active use
                // initializes it. u16::MAX is the "no class" sentinel the
                // verifier accepts for hand-built bytecode.
                if $a2 != u16::MAX as u32 {
                    $vm.ensure_class_initialized($a2)?;
                }
                $vm.call_stack($a0, $a1 as usize)?;
            }
            CallClass => {
                let arity = $a2 as usize;
                let recv = *$vm
                    .stack
                    .get($vm.stack.len() - arity - 1)
                    .ok_or_else(|| $vm.err_at("stack underflow in virtual call"))?;
                let actual = $vm.receiver_class(&recv)?;
                let target = $module
                    .classes
                    .get(actual as usize)
                    .and_then(|c| c.method_table.get($a1 as usize))
                    .copied()
                    .ok_or_else(|| $vm.err_at("method_table slot out of range"))?;
                $vm.call_stack(target, arity + 1)?;
            }
            CallInterface => {
                let arity = $a2 as usize;
                let recv = *$vm
                    .stack
                    .get($vm.stack.len() - arity - 1)
                    .ok_or_else(|| $vm.err_at("stack underflow in interface call"))?;
                let actual = $vm.receiver_class(&recv)?;
                let entry = $module
                    .classes
                    .get(actual as usize)
                    .and_then(|c| c.interfaces.iter().find(|(iid, _)| *iid == $a0))
                    .ok_or_else(|| $vm.err_at("object does not implement interface"))?;
                let target = entry
                    .1
                    .get($a1 as usize)
                    .copied()
                    .ok_or_else(|| $vm.err_at("interface slot out of range"))?;
                $vm.call_stack(target, arity + 1)?;
            }
            CallNative => {
                let native = $a0 as u16;
                let arity = $a1 as usize;
                let takes_recv = crate::stdlib::builtins::native_takes_receiver(native);
                let total = arity + usize::from(takes_recv);
                let start = $vm.stack.len() - total;
                // Copy the contiguous stack arguments into a small buffer so
                // routine native calls do not allocate.
                let mut buf = [Value::Null; 16];
                let res = if total <= buf.len() {
                    buf[..total].copy_from_slice(&$vm.stack[start..]);
                    // Remove the arguments before the call, matching the
                    // pre-call stack shape the natives expect.
                    $vm.move_stack_suffix_to_temps(start);
                    crate::vm::natives::call_native($vm, native, &buf[..total])?
                } else {
                    let args: Vec<Value> = $vm.move_stack_suffix_to_temps(start);
                    crate::vm::natives::call_native($vm, native, &args)?
                };
                if crate::stdlib::builtins::native_returns_value(native) {
                    $vm.push(res);
                }
            }
            CallDynamic => {
                let arity = $a1 as usize;
                let recv = *$vm
                    .stack
                    .get($vm.stack.len() - arity - 1)
                    .ok_or_else(|| $vm.err_at("stack underflow in dynamic call"))?;
                let name = $module
                    .dyn_names
                    .get($a0 as usize)
                    .map(String::as_str)
                    .unwrap_or_default();
                // Dynamic dispatch is defined for class instances; a
                // built-in value (String, List, ...) or null produces a
                // deterministic "no method" error naming its dynamic type.
                let actual = match $vm.receiver_class(&recv) {
                    Ok(c) => c,
                    Err(_) => {
                        return Err($vm.err_at(format!(
                            "no method '{}' on {}",
                            name,
                            crate::vm::natives::type_tag($vm, &recv)
                        )))
                    }
                };
                // Direct per-class dynamic lookup from the public effective
                // method table: no parent-chain walk, no private methods.
                let target = $module
                    .classes
                    .get(actual as usize)
                    .and_then(|c| {
                        c.dyn_methods
                            .iter()
                            .find(|(n, _)| n == name)
                            .map(|(_, fid)| *fid)
                    })
                    .ok_or_else(|| {
                        $vm.err_at(format!(
                            "no method '{}' on {}",
                            name, $module.classes[actual as usize].name
                        ))
                    })?;
                // The call-site static type is Object, so it always expects a
                // result. When the dynamically-dispatched method is actually
                // void it would leave nothing on the stack; plant a null
                // placeholder *below* the callee frame (shifting the
                // arguments up one slot) so the void return leaves exactly
                // one value behind.
                if !$module.functions[target as usize].returns_value {
                    let base = $vm.stack.len() - arity - 1;
                    $vm.stack.insert(base, Value::Null);
                }
                $vm.call_stack(target, arity + 1)?;
            }
            // ---- objects -----------------------------------------------------
            NewObject => {
                // Construction is an active use: initialize the class first.
                // Allocate exactly this class's instance with default
                // (null) fields; the checker emits one (value, StoreField)
                // pair per field. There is no inheritance or
                // constructor-target override.
                $vm.ensure_class_initialized($a0)?;
                let n = $a1 as usize;
                let fields = vec![Value::Null; n];
                let r = $vm.alloc(HeapObject::Instance {
                    class: $a0 as u16,
                    fields,
                });
                $vm.push(Value::Object(r));
            }
            LoadField => {
                // [recv] -> [value]: the receiver is consumed.
                let top = $vm.pop();
                let v = {
                    let heap = $vm.heap();
                    match Self::ref_of(&top) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Instance { fields, .. }) => *fields
                                .get($a0 as usize)
                                .ok_or_else(|| $vm.err_at("field index out of range"))?,
                            _ => return Err($vm.err_at("not an object")),
                        },
                        None => return Err($vm.err_at("not an object")),
                    }
                };
                $vm.push(v);
            }
            StoreField => {
                // [recv, value] -> [recv]: pop the value, keep the receiver.
                let val = $vm.pop();
                let top = $vm.stack.last().copied().unwrap_or(Value::Null);
                let old = {
                    let mut heap = $vm.heap_mut();
                    match Self::ref_of(&top) {
                        Some(r) => match heap.get_mut(r) {
                            Some(HeapObject::Instance { fields, .. }) => {
                                let slot = fields
                                    .get_mut($a0 as usize)
                                    .ok_or_else(|| $vm.err_at("field index out of range"))?;
                                let old = *slot;
                                *slot = val;
                                old
                            }
                            _ => return Err($vm.err_at("not an object")),
                        },
                        None => return Err($vm.err_at("not an object")),
                    }
                };
                $vm.finish_replacement(val, old);
            }
            LoadStatic => {
                // Reading a static slot is an active use: initialize the
                // declaring class first, then push its static slot value.
                $vm.ensure_class_initialized($a0)?;
                let v = $vm
                    .heap()
                    .statics
                    .get($a0 as usize)
                    .and_then(|s| s.get($a1 as usize))
                    .copied()
                    .ok_or_else(|| $vm.err_at("static slot out of range"))?;
                $vm.push(v);
            }
            StoreStatic => {
                // Write the top-of-stack value into the declaring class's
                // static slot. The value expression is already evaluated
                // and rooted on the operand stack; initializing the class
                // before the pop keeps the store's temporary ownership
                // intact across the bounded initializer run and preserves
                // expression evaluation order.
                $vm.ensure_class_initialized($a0)?;
                let val = $vm.pop();
                let old = {
                    let mut heap = $vm.heap_mut();
                    let slot = heap
                        .statics
                        .get_mut($a0 as usize)
                        .and_then(|s| s.get_mut($a1 as usize))
                        .ok_or_else(|| $vm.err_at("static slot out of range"))?;
                    let old = *slot;
                    *slot = val;
                    old
                };
                $vm.finish_replacement(val, old);
            }

            // ---- collections (shared helpers: same semantics + sync as natives) ----
            NewList => {
                let r = crate::vm::collections::list_alloc($vm, $a0 as usize);
                $vm.push(Value::Object(r));
            }
            NewStack => {
                let r = crate::vm::collections::stack_alloc($vm, 0);
                $vm.push(Value::Object(r));
            }
            NewMap => {
                let r = crate::vm::collections::map_alloc($vm, $a0 as usize);
                $vm.push(Value::Object(r));
            }
            ListSpread => {
                // Variable stack expansion is not part of the accepted
                // bytecode contract (the verifier rejects it); variadic
                // spread compiles to NewList/ListAdd/ListExtend.
                return Err($vm.err_at("ListSpread is not supported"));
            }
            ListExtend => {
                let source = $vm.pop();
                let destination = $vm.pop();
                let source_ref =
                    Self::ref_of(&source).ok_or_else(|| VmError::new("spread requires a List"))?;
                let destination_ref = Self::ref_of(&destination)
                    .ok_or_else(|| VmError::new("spread destination is not a List"))?;
                crate::vm::collections::list_extend($vm, destination_ref, source_ref)?;
                $vm.push(destination);
            }
            ListAdd => {
                let item = $vm.pop();
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                crate::vm::collections::list_push($vm, r, item)?;
                $vm.push(list);
            }
            ListGet => {
                let iv = $vm.pop();
                let idx = $vm.long_of(&iv)?;
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                let v = crate::vm::collections::list_get($vm, r, idx)?;
                $vm.push(v);
            }
            ListSet => {
                let val = $vm.pop();
                let iv = $vm.pop();
                let idx = $vm.long_of(&iv)?;
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                // Returns the replaced element (Java List.set shape).
                let v = crate::vm::collections::list_set($vm, r, idx, val)?;
                $vm.push(v);
            }
            ListRemove => {
                let iv = $vm.pop();
                let idx = $vm.long_of(&iv)?;
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                // Returns the removed element (Java List.remove(int) shape).
                let v = crate::vm::collections::list_remove_at($vm, r, idx)?;
                $vm.push(v);
            }
            ListContains => {
                let v = $vm.pop();
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                let found = crate::vm::collections::list_contains($vm, r, v)?;
                $vm.push(Value::Boolean(found));
            }
            ListIndexOf => {
                let v = $vm.pop();
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                let pos = crate::vm::collections::list_index_of($vm, r, v)?;
                $vm.push(Value::Integer(
                    i32::try_from(pos).map_err(|_| $vm.err_at("list index out of range"))?,
                ));
            }
            ListReverse | ListSort | ListClear => {
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                match $op {
                    ListReverse => crate::vm::collections::list_reverse($vm, r)?,
                    ListSort => crate::vm::collections::list_sort($vm, r)?,
                    _ => crate::vm::collections::list_clear($vm, r)?,
                }
                $vm.push(list);
            }
            ListLen => {
                let list = $vm.pop();
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                let n = crate::vm::collections::list_len($vm, r)?;
                let size = i32::try_from(n).map_err(|_| $vm.err_at("list size out of range"))?;
                $vm.push(Value::Integer(size));
            }
            ListJoin => {
                let sep = $vm.pop();
                let list = $vm.pop();
                let sep_t = {
                    let heap = $vm.heap();
                    Self::str_ref(&heap, &sep)?.to_string()
                };
                let r = Self::ref_of(&list).ok_or_else(|| VmError::new("not a List"))?;
                let text = crate::vm::collections::list_join($vm, r, &sep_t)?;
                let s = $vm.make_string(text);
                $vm.push(s);
            }
            MapPut => {
                let val = $vm.pop();
                let key = $vm.pop();
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                // Returns the previous value or null (Java Map.put shape).
                let prev = crate::vm::collections::map_put($vm, r, key, val)?;
                $vm.push(prev);
            }
            MapGet => {
                let key = $vm.pop();
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                let v = crate::vm::collections::map_get($vm, r, key)?;
                $vm.push(v);
            }
            MapRemove => {
                let key = $vm.pop();
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                // Returns the removed value or null (Java Map.remove shape).
                let v = crate::vm::collections::map_remove($vm, r, key)?;
                $vm.push(v);
            }
            MapContainsKey => {
                let key = $vm.pop();
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                let found = crate::vm::collections::map_contains_key($vm, r, key)?;
                $vm.push(Value::Boolean(found));
            }
            MapLen => {
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                let n = crate::vm::collections::map_len($vm, r)?;
                let size = i32::try_from(n).map_err(|_| $vm.err_at("map size out of range"))?;
                $vm.push(Value::Integer(size));
            }
            MapKeys | MapValues => {
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                let ref_ = match $op {
                    MapKeys => crate::vm::collections::map_keys($vm, r)?,
                    _ => crate::vm::collections::map_values($vm, r)?,
                };
                $vm.push(Value::Object(ref_));
            }
            MapClear => {
                let map = $vm.pop();
                let r = Self::ref_of(&map).ok_or_else(|| VmError::new("not a Map"))?;
                crate::vm::collections::map_clear($vm, r)?;
                $vm.push(map);
            }
            StackPush => {
                let item = $vm.pop();
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                crate::vm::collections::stack_push($vm, r, item)?;
                $vm.push(stack);
            }
            StackPop => {
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                // Underflow is a runtime error (Java Deque.removeLast shape).
                let v = crate::vm::collections::stack_pop($vm, r)?;
                $vm.push(v);
            }
            StackPeek => {
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                // Null when empty (Java Deque.peek shape).
                let v = crate::vm::collections::stack_peek($vm, r)?;
                $vm.push(v);
            }
            StackGet => {
                let iv = $vm.pop();
                let idx = $vm.long_of(&iv)?;
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                let v = crate::vm::collections::stack_get($vm, r, idx)?;
                $vm.push(v);
            }
            StackLen => {
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                let n = crate::vm::collections::stack_len($vm, r)?;
                let size = i32::try_from(n).map_err(|_| $vm.err_at("stack size out of range"))?;
                $vm.push(Value::Integer(size));
            }
            StackEmpty => {
                let stack = $vm.pop();
                let r = Self::ref_of(&stack).ok_or_else(|| VmError::new("not a Stack"))?;
                let empty = crate::vm::collections::stack_len($vm, r)? == 0;
                $vm.push(Value::Boolean(empty));
            }
            // ---- strings -------------------------------------------------------
            StrLen => {
                let s = $vm.pop();
                let n = {
                    let heap = $vm.heap();
                    match Self::ref_of(&s) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::String { text }) => text.chars().count() as i64,
                            _ => return Err(VmError::new("expected String")),
                        },
                        None => return Err(VmError::new("expected String")),
                    }
                };
                $vm.push(Value::Long(n));
            }
            StrConcat => {
                let b = $vm.pop();
                let a_ = $vm.pop();
                // Fast path: both operands are String objects; build the
                // result directly from borrowed text.
                let text = {
                    let heap = $vm.heap();
                    match (&a_, &b) {
                        (Value::Object(ra), Value::Object(rb)) => {
                            match (heap.get(*ra), heap.get(*rb)) {
                                (
                                    Some(HeapObject::String { text: ta }),
                                    Some(HeapObject::String { text: tb }),
                                ) => {
                                    let mut t = String::with_capacity(ta.len() + tb.len());
                                    t.push_str(ta);
                                    t.push_str(tb);
                                    t
                                }
                                _ => {
                                    let (ta, tb) = (a_.to_display(&heap), b.to_display(&heap));
                                    let mut t = ta;
                                    t.push_str(&tb);
                                    t
                                }
                            }
                        }
                        _ => {
                            let (ta, tb) = (a_.to_display(&heap), b.to_display(&heap));
                            let mut t = ta;
                            t.push_str(&tb);
                            t
                        }
                    }
                };
                let v = $vm.make_string(text);
                $vm.push(v);
            }
            StrSubstr => {
                let endtmp = $vm.pop();
                let end = $vm.long_of(&endtmp)?;
                let starttmp = $vm.pop();
                let start = $vm.long_of(&starttmp)?;
                let s = $vm.pop();
                let sub: String = {
                    let heap = $vm.heap();
                    let text = Self::str_ref(&heap, &s)?;
                    let chars: Vec<char> = text.chars().collect();
                    let n = chars.len() as i64;
                    let lo = start.clamp(0, n).max(0);
                    let hi = end.clamp(0, n);
                    if lo > hi {
                        return Err($vm.err_at("substring start is after end"));
                    }
                    chars[lo as usize..hi as usize].iter().collect()
                };
                let v = $vm.make_string(sub);
                $vm.push(v);
            }
            StrContains | StrStartsWith | StrEndsWith => {
                let x = $vm.pop();
                let s = $vm.pop();
                let r = {
                    let heap = $vm.heap();
                    let ta = Self::str_ref(&heap, &s)?;
                    let tb = Self::str_ref(&heap, &x)?;
                    match $op {
                        StrContains => ta.contains(tb),
                        StrStartsWith => ta.starts_with(tb),
                        _ => ta.ends_with(tb),
                    }
                };
                $vm.push(Value::Boolean(r));
            }
            StrSplit => {
                let sep = $vm.pop();
                let s = $vm.pop();
                let parts: Vec<String> = {
                    let heap = $vm.heap();
                    let ta = Self::str_ref(&heap, &s)?;
                    let tb = Self::str_ref(&heap, &sep)?;
                    ta.split(tb).map(str::to_string).collect()
                };
                let items: Vec<Value> = parts.into_iter().map(|p| $vm.make_string(p)).collect();
                let r = crate::vm::collections::list_alloc_with_items($vm, items);
                $vm.push(Value::Object(r));
            }
            StrReplace => {
                let to = $vm.pop();
                let from = $vm.pop();
                let s = $vm.pop();
                let out = {
                    let heap = $vm.heap();
                    let ta = Self::str_ref(&heap, &s)?;
                    let tf = Self::str_ref(&heap, &from)?;
                    let tt = Self::str_ref(&heap, &to)?;
                    ta.replace(tf, tt)
                };
                let v = $vm.make_string(out);
                $vm.push(v);
            }
            StrTrim | StrUpper | StrLower => {
                let s = $vm.pop();
                let out = {
                    let heap = $vm.heap();
                    let t = Self::str_ref(&heap, &s)?;
                    match $op {
                        StrTrim => t.trim().to_string(),
                        StrUpper => t.to_uppercase(),
                        _ => t.to_lowercase(),
                    }
                };
                let v = $vm.make_string(out);
                $vm.push(v);
            }
            StrIndex => {
                let x = $vm.pop();
                let s = $vm.pop();
                let pos: i64 = {
                    let heap = $vm.heap();
                    let ta = Self::str_ref(&heap, &s)?;
                    let tb = Self::str_ref(&heap, &x)?;
                    ta.find(tb)
                        .map(|b| ta[..b].chars().count() as i64)
                        .unwrap_or(-1)
                };
                $vm.push(Value::Long(pos));
            }
            StrCharAt => {
                let itmp = $vm.pop();
                let i = $vm.long_of(&itmp)?;
                let s = $vm.pop();
                let c: Option<char> = {
                    let heap = $vm.heap();
                    let t = Self::str_ref(&heap, &s)?;
                    usize::try_from(i)
                        .ok()
                        .and_then(|index| t.chars().nth(index))
                };
                match c {
                    Some(c) => $vm.push(Value::Char(c)),
                    None => return Err($vm.err_at("char index out of range")),
                }
            }
            // ---- enums -----------------------------------------------------------
            NewEnum => {
                let payload = if $a2 != 0 { Some($vm.pop()) } else { None };
                let r = $vm.alloc(HeapObject::Enum {
                    enum_id: $a0 as u16,
                    index: $a1 as u8,
                    payload,
                });
                $vm.push(Value::Object(r));
            }
            EnumIndex => {
                let e = $vm.pop();
                let idx = {
                    let heap = $vm.heap();
                    match Self::ref_of(&e) {
                        Some(r) => match heap.get(r) {
                            Some(HeapObject::Enum { index, .. }) => *index as i64,
                            _ => return Err(VmError::new("not an Enum")),
                        },
                        None => return Err(VmError::new("not an Enum")),
                    }
                };
                $vm.push(Value::Long(idx));
            }
            EnumPayload => {
                let e = $vm.pop();
                let p = {
                    let heap = $vm.heap();
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
                $vm.push(p);
            }
            // ---- exceptions ---------------------------------------------------------
            Throw => {
                let v = $vm.pop();
                $vm.do_throw(v)?;
            }
            TryBegin => {
                $vm.try_regions.push(TryRegion {
                    catch_ip: $a0,
                    // Offset 0 encodes "no finally"; handler regions are
                    // always emitted after the TryBegin itself.
                    finally_ip: if $a1 == 0 { None } else { Some($a1) },
                    stack_base: $vm.stack.len(),
                    frame_depth: $vm.frames.len(),
                });
            }
            TryEnd => {
                $vm.try_regions.pop();
            }
            FinallyDivert => {
                // break/continue trampoline: run the innermost enclosing
                // finally, then resume at the next instruction.
                let resume = $vm.frames.last().unwrap().ip;
                if $vm.divert_to_finally() {
                    $vm.finally_resume = Some(resume);
                }
            }
            FinallyEnd => {
                if let Some(e) = $vm.rethrow.take() {
                    // Transfer the rethrow slot's ownership into the
                    // pending-throw path without an extra retain.
                    $vm.temp_values.push(e);
                    $vm.do_throw(e)?;
                } else if $vm.pending_return.is_some() {
                    if !$vm.divert_to_finally() {
                        match $vm.pending_return.take().unwrap() {
                            PendingReturn::Void => {
                                $vm.return_from_frame()?;
                            }
                            PendingReturn::Value(v) => {
                                $vm.return_from_frame()?;
                                $vm.push(v);
                                $vm.release_value(v);
                            }
                        }
                    }
                    // else: an outer finally will complete the return.
                } else if let Some(resume) = $vm.finally_resume.take() {
                    $vm.frames.last_mut().unwrap().ip = resume;
                }
            }
            // ---- termination ----------------------------------------------------------
            Return => {
                let v = $vm.pop();
                if $vm.divert_to_finally() {
                    // A return inside a finally suppresses any exception
                    // that was passing through it.
                    $vm.rethrow = None;
                    $vm.finally_resume = None;
                    if !$vm.take_temp(v) {
                        $vm.retain_value(v);
                    }
                    if let Some(PendingReturn::Value(previous)) =
                        $vm.pending_return.replace(PendingReturn::Value(v))
                    {
                        $vm.release_value(previous);
                    }
                } else {
                    $vm.return_from_frame()?;
                    $vm.push(v);
                }
            }
            ReturnVoid => {
                if $vm.divert_to_finally() {
                    $vm.rethrow = None;
                    $vm.finally_resume = None;
                    $vm.pending_return = Some(PendingReturn::Void);
                } else {
                    $vm.return_from_frame()?;
                }
            }
            // ---- stack manipulation -------------------------------------------------------
            Dup => {
                let top = $vm.stack.last().copied().unwrap_or(Value::Null);
                $vm.push(top);
            }
            // ---- housekeeping ------------------------------------------------------------
            GcHint => {
                $vm.maybe_gc();
            }
        }
        Ok(())
    }};
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
        // The entry-point dispatch actively uses Main, so Main's static
        // fields and block initialize now, before the entry function runs.
        // Every other class initializes lazily at its own first active use.
        if let Some(entry) = vm.shared.module.entry {
            if let Some(cid) = vm.shared.module.classes.iter().position(|c| {
                c.statics
                    .iter()
                    .any(|(name, fid)| name == "run" && *fid == entry)
            }) {
                vm.ensure_class_initialized(cid as u32)?;
            }
        }
        let result = vm.run_entry(args);
        vm.shared.active_threads.fetch_sub(1, Ordering::SeqCst);
        result
    }

    /// Initialize `class_id` if this is its first active use. No-op when the
    /// class has no static fields or block, or already initialized. The
    /// synthetic initializer runs as a bounded call on top of the current
    /// frames; an uncaught initializer error fails the active operation,
    /// marks the class failed (cached for later uses), and never retries.
    fn ensure_class_initialized(&mut self, class_id: u32) -> Result<(), VmError> {
        let module = &self.shared.module;
        let Some(class) = module.classes.get(class_id as usize) else {
            return Err(self.err_at("class id out of range"));
        };
        let Some(fid) = class.static_init else {
            // No static fields and no static block: nothing to run.
            return Ok(());
        };
        let name = class.name.clone();
        let thread = std::thread::current().id();
        {
            let mut guard = self.shared.class_init[class_id as usize]
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            loop {
                // Clone the state so the wait arm can move the guard
                // without conflicting with the match's borrow.
                let state = (*guard).clone();
                match state {
                    ClassInit::Initialized => return Ok(()),
                    ClassInit::Failed(message) => {
                        return Err(VmError::new(format!(
                            "static initialization of '{}' failed: {}",
                            name, message
                        )))
                    }
                    ClassInit::Initializing(owner) if owner == thread => {
                        // Same-thread re-entry while our own initializer is
                        // running: expose the class's current (default)
                        // slots; do not recursively rerun the initializer.
                        return Ok(());
                    }
                    ClassInit::Initializing(_) => {
                        // Another thread is initializing: wait without the
                        // heap lock and without running user bytecode, then
                        // re-check the state.
                        guard = self
                            .shared
                            .class_init_wait
                            .wait(guard)
                            .unwrap_or_else(|e| e.into_inner());
                        continue;
                    }
                    ClassInit::Uninit => {
                        *guard = ClassInit::Initializing(thread);
                        break;
                    }
                }
            }
        }
        // Run the initializer until it returns to our frame depth, leaving
        // the caller frame intact. Caller try-regions are hidden so an
        // uncaught initializer error fails the active operation instead of
        // landing in a caller catch handler.
        let depth = self.frames.len();
        let saved_regions = std::mem::take(&mut self.try_regions);
        self.try_regions.retain(|r| r.frame_depth > depth);
        let result = self
            .call_function(fid, &[])
            .and_then(|_| self.execute_until(depth));
        self.try_regions = saved_regions;
        {
            let mut guard = self.shared.class_init[class_id as usize]
                .lock()
                .unwrap_or_else(|e| e.into_inner());
            *guard = match &result {
                Ok(()) => ClassInit::Initialized,
                Err(e) => ClassInit::Failed(e.message.clone()),
            };
            drop(guard);
            self.shared.class_init_wait.notify_all();
        }
        result.map_err(|e| VmError {
            message: format!("static initialization of '{}' failed: {}", name, e.message),
            location: e.location,
        })
    }

    fn new(shared: SharedState) -> Self {
        Vm {
            shared,
            stack: Vec::new(),
            frames: Vec::new(),
            try_regions: Vec::new(),
            pending_throw: None,
            rethrow: None,
            pending_return: None,
            finally_resume: None,
            temp_values: Vec::new(),
            temp_refs: Vec::new(),
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
        let list_ref = self.alloc(HeapObject::list());
        for a in args {
            let s = self.alloc_string(&a);
            self.list_add(list_ref, Value::Object(s))?;
        }
        self.call_function(entry, &[Value::Object(list_ref)])?;
        self.execute()?;
        let value = self.pop();
        let result = match value {
            Value::Long(i) => i,
            v => value_to_int(&v),
        };
        self.release_temps();
        Ok(result)
    }

    /// Spawn a new Solvik thread executing `runnable.run()`.
    pub fn spawn_thread(shared: SharedState, runnable: GcRef) -> std::thread::JoinHandle<()> {
        // The worker owns a strong reference independent of the Thread
        // object and of the caller's VM stack until its entry call returns.
        if runnable != 0 {
            let heap = shared.heap.lock().unwrap_or_else(|e| e.into_inner());
            assert!(heap.retain(runnable), "thread runnable is not live");
        }
        shared.active_threads.fetch_add(1, Ordering::SeqCst);
        std::thread::spawn(move || {
            let mut vm = Vm::new(shared.clone());
            if let Err(e) = vm.run_runnable(runnable) {
                eprintln!("thread error: {}", e.message);
            }
            if runnable != 0 {
                shared
                    .heap
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .release(runnable);
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
        self.call_function(target, &[Value::Object(runnable)])?;
        self.execute()?;
        if !self.stack.is_empty() {
            self.pop();
        }
        self.release_temps();
        Ok(())
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    fn heap(&self) -> std::sync::MutexGuard<'_, Heap> {
        self.shared.heap.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn heap_mut(&self) -> std::sync::MutexGuard<'_, Heap> {
        self.shared.heap.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Allocate an object and register the returned strong reference as an
    /// instruction temporary. The next VM storage operation either transfers
    /// it into a slot or the instruction-boundary cleanup releases it.
    pub(crate) fn alloc(&mut self, object: HeapObject) -> GcRef {
        let ref_ = self.heap_mut().alloc(object);
        self.temp_values.push(Value::Object(ref_));
        ref_
    }

    pub(crate) fn retain_value(&self, value: Value) {
        // Only object values own strong references; primitives never touch
        // the heap, so skip the lock entirely for them.
        if matches!(value, Value::Object(_)) {
            self.heap().retain_value(value);
        }
    }

    pub(crate) fn release_value(&self, value: Value) {
        if matches!(value, Value::Object(_)) {
            self.heap_mut().release_value(value);
        }
    }

    /// Give a value returned by a runtime helper one temporary strong owner.
    /// The enclosing opcode normally transfers that owner to the operand
    /// stack with `push`; direct native callers may keep it until VM teardown.
    pub(crate) fn promote_return(&mut self, value: Value) {
        if !value.is_null() {
            self.retain_value(value);
            self.temp_values.push(value);
        }
    }

    fn take_temp(&mut self, value: Value) -> bool {
        let Some(pos) = self.temp_values.iter().position(|v| *v == value) else {
            return false;
        };
        self.temp_values.swap_remove(pos);
        true
    }

    fn finish_replacement(&mut self, new_value: Value, old_value: Value) {
        if !self.take_temp(new_value) {
            self.retain_value(new_value);
        }
        self.release_value(old_value);
    }

    fn move_stack_suffix_to_temps(&mut self, start: usize) -> Vec<Value> {
        let args = self.stack[start..].to_vec();
        self.stack.truncate(start);
        self.temp_values.extend(args.iter().copied());
        args
    }

    fn release_temps(&mut self) {
        let temps = std::mem::take(&mut self.temp_values);
        self.temp_refs.clear();
        for value in temps {
            if let Value::Object(r) = value {
                self.temp_refs.push(r);
            }
        }
        // Release all leftover object references under one heap lock; the
        // per-value order is unchanged.
        if !self.temp_refs.is_empty() {
            let mut heap = self.heap_mut();
            for &r in &self.temp_refs {
                heap.release(r);
            }
        }
    }

    fn truncate_owned_stack(&mut self, target: usize) {
        if target >= self.stack.len() {
            return;
        }
        let values: Vec<Value> = self.stack.drain(target..).collect();
        self.temp_refs.clear();
        for value in values {
            if let Value::Object(r) = value {
                self.temp_refs.push(r);
            }
        }
        if !self.temp_refs.is_empty() {
            let mut heap = self.heap_mut();
            for &r in &self.temp_refs {
                heap.release(r);
            }
        }
    }

    fn alloc_string(&mut self, s: &str) -> GcRef {
        self.alloc(HeapObject::String {
            text: s.to_string(),
        })
    }

    /// Content equality: primitives by value, strings by text, enum values
    /// by (variant id, index, content-equal payload), other objects by
    /// identity. Cyclic structures terminate: a reference pair already under
    /// comparison is treated as equal.
    pub(crate) fn values_equal(heap: &Heap, a: &Value, b: &Value) -> bool {
        Self::values_equal_inner(heap, a, b, &mut None)
    }

    fn values_equal_inner(
        heap: &Heap,
        a: &Value,
        b: &Value,
        active: &mut Option<std::collections::HashSet<GcRef>>,
    ) -> bool {
        match (a, b) {
            (Value::Null, Value::Null) => true,
            (Value::Boolean(x), Value::Boolean(y)) => x == y,
            // Integral numerics compare by numeric value across widths.
            (x, y) if x.int_value().is_some() && y.int_value().is_some() => {
                x.int_value() == y.int_value()
            }
            // Floating-point numerics compare by value across precisions
            // (NaN is never equal, matching IEEE-754).
            (x, y) if x.float_value().is_some() && y.float_value().is_some() => {
                x.float_value() == y.float_value()
            }
            // Integer vs float compares numerically (Java-style promotion).
            (x, y) if x.num_f64().is_some() && y.num_f64().is_some() => x.num_f64() == y.num_f64(),
            (Value::Char(x), Value::Char(y)) => x == y,
            (Value::Object(ra), Value::Object(rb)) => {
                if ra == rb {
                    return true;
                }
                // Cycle guard: a pair already being compared above us is
                // assumed equal so recursive structures terminate. The set
                // is only borrowed for the insert/remove, never across the
                // recursive calls below.
                if active.is_none() {
                    *active = Some(std::collections::HashSet::new());
                }
                if !active.as_mut().unwrap().insert(*ra) {
                    return true;
                }
                let result = match (heap.get(*ra), heap.get(*rb)) {
                    (
                        Some(HeapObject::String { text: ta }),
                        Some(HeapObject::String { text: tb }),
                    ) => ta == tb,
                    (
                        Some(HeapObject::BigInteger { value: va }),
                        Some(HeapObject::BigInteger { value: vb }),
                    ) => va == vb,
                    (
                        Some(HeapObject::BigDecimal { value: da }),
                        Some(HeapObject::BigDecimal { value: db }),
                    ) => da.cmp_dec(db) == std::cmp::Ordering::Equal,
                    (Some(HeapObject::List { data: da }), Some(HeapObject::List { data: db })) => {
                        let ga = da.lock().unwrap_or_else(|e| e.into_inner());
                        let gb = db.lock().unwrap_or_else(|e| e.into_inner());
                        Self::value_lists_equal(heap, &ga.items, &gb.items, active)
                    }
                    (
                        Some(HeapObject::Stack { data: da }),
                        Some(HeapObject::Stack { data: db }),
                    ) => {
                        let ga = da.lock().unwrap_or_else(|e| e.into_inner());
                        let gb = db.lock().unwrap_or_else(|e| e.into_inner());
                        let a: Vec<Value> = ga.items.iter().copied().collect();
                        let b: Vec<Value> = gb.items.iter().copied().collect();
                        Self::value_lists_equal(heap, &a, &b, active)
                    }
                    (Some(HeapObject::Set { data: da }), Some(HeapObject::Set { data: db })) => {
                        let ga = da.lock().unwrap_or_else(|e| e.into_inner());
                        let gb = db.lock().unwrap_or_else(|e| e.into_inner());
                        Self::value_sets_equal(heap, &ga.items(), &gb.items(), active)
                    }
                    (Some(HeapObject::Map { data: da }), Some(HeapObject::Map { data: db })) => {
                        let ga = da.lock().unwrap_or_else(|e| e.into_inner());
                        let gb = db.lock().unwrap_or_else(|e| e.into_inner());
                        Self::value_maps_equal(heap, &ga.entries(), &gb.entries(), active)
                    }
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
                                (Some(x), Some(y)) => Self::values_equal_inner(heap, x, y, active),
                                _ => false,
                            }
                    }
                    _ => false,
                };
                active.as_mut().unwrap().remove(ra);
                result
            }
            _ => false,
        }
    }

    /// Universal `hashCode()`: deterministic and consistent with
    /// `values_equal` (equal values hash equal). Ordinary objects and
    /// natives use an identity-based hash derived from the heap slot, which
    /// is stable for the object's lifetime. Cyclic structures terminate:
    /// a reference already under hashing contributes zero.
    pub(crate) fn value_hash(heap: &Heap, v: &Value) -> i64 {
        Self::value_hash_inner(heap, v, &mut None)
    }

    fn value_hash_inner(
        heap: &Heap,
        v: &Value,
        active: &mut Option<std::collections::HashSet<GcRef>>,
    ) -> i64 {
        match v {
            Value::Null => 0,
            Value::Boolean(b) => i64::from(*b),
            x @ (Value::Byte(_) | Value::Short(_) | Value::Integer(_) | Value::Long(_)) => {
                x.int_value().unwrap_or(0)
            }
            Value::Float(f) => Self::float_hash(*f as f64),
            Value::Double(d) => Self::float_hash(*d),
            Value::Char(c) => *c as i64,
            Value::Object(r) => {
                let obj = match heap.get(*r) {
                    Some(o) => o,
                    None => return 0,
                };
                // Cycle guard: a reference already under hashing above us
                // contributes zero so recursive structures terminate. The
                // set is only borrowed for the insert/remove, never across
                // the recursive calls below.
                if active.is_none() {
                    *active = Some(std::collections::HashSet::new());
                }
                if !active.as_mut().unwrap().insert(*r) {
                    return 0;
                }
                let h = match obj {
                    HeapObject::String { text } => Self::text_hash(text),
                    HeapObject::BigInteger { value } => {
                        let (neg, digits) = value.to_u64_digits();
                        let mut h: i64 = 0;
                        for d in digits {
                            h = h.wrapping_mul(31).wrapping_add(d as i64);
                        }
                        if neg == num_bigint::Sign::Minus {
                            h = h.wrapping_neg();
                        }
                        h
                    }
                    HeapObject::BigDecimal { value } => {
                        // Hash the normalized form so scale-insensitively
                        // equal values (1.50 vs 1.5) hash identically.
                        let norm = value.normalize();
                        let (neg, digits) = norm.unscaled.to_u64_digits();
                        let mut h: i64 = 0;
                        for d in digits {
                            h = h.wrapping_mul(31).wrapping_add(d as i64);
                        }
                        if neg == num_bigint::Sign::Minus {
                            h = h.wrapping_neg();
                        }
                        h.wrapping_mul(31).wrapping_add(i64::from(norm.scale))
                    }
                    HeapObject::List { data } => {
                        let g = data.lock().unwrap_or_else(|e| e.into_inner());
                        let mut h: i64 = 1;
                        for item in &g.items {
                            h = h
                                .wrapping_mul(31)
                                .wrapping_add(Self::value_hash_inner(heap, item, active));
                        }
                        h
                    }
                    HeapObject::Stack { data } => {
                        let g = data.lock().unwrap_or_else(|e| e.into_inner());
                        let mut h: i64 = 1;
                        for item in g.items.iter() {
                            h = h
                                .wrapping_mul(31)
                                .wrapping_add(Self::value_hash_inner(heap, item, active));
                        }
                        h
                    }
                    HeapObject::Set { data } => {
                        let g = data.lock().unwrap_or_else(|e| e.into_inner());
                        let mut h: i64 = 0;
                        for item in g.items() {
                            // Order-independent: xor of per-element hashes.
                            h ^= Self::value_hash_inner(heap, &item, active);
                        }
                        h
                    }
                    HeapObject::Map { data } => {
                        let g = data.lock().unwrap_or_else(|e| e.into_inner());
                        let mut h: i64 = 0;
                        for (k, val) in g.entries() {
                            let pair = Self::value_hash_inner(heap, &k, active)
                                .wrapping_mul(31)
                                .wrapping_add(Self::value_hash_inner(heap, &val, active));
                            // Order-independent over entries.
                            h ^= pair;
                        }
                        h
                    }
                    HeapObject::Enum {
                        enum_id,
                        index,
                        payload,
                    } => {
                        let mut h: i64 = i64::from(*enum_id);
                        h = h.wrapping_mul(31).wrapping_add(i64::from(*index));
                        if let Some(p) = payload {
                            h = h
                                .wrapping_mul(31)
                                .wrapping_add(Self::value_hash_inner(heap, p, active));
                        }
                        h
                    }
                    // Instances, exceptions, threads, and other natives:
                    // identity-based, stable for the object's lifetime.
                    _ => (*r as i64).wrapping_mul(0x9E37_79B9_7F4A_7C15u64 as i64),
                };
                active.as_mut().unwrap().remove(r);
                h
            }
        }
    }

    /// Hash for floating-point values, consistent with cross-precision and
    /// integer-vs-float equality: signed zeros share one hash, and integral
    /// values that fit exactly in i64 share their hash with the equal
    /// integral value.
    fn float_hash(f: f64) -> i64 {
        if f.is_finite() && f.fract() == 0.0 && f.abs() < 9_223_372_036_854_775_808.0 {
            return f as i64;
        }
        // Normalize signed zero so -0.0 and 0.0 hash identically (they are
        // numerically equal).
        let bits = if f == 0.0 { 0u64 } else { f.to_bits() };
        bits as i64
    }

    /// Deterministic text hash (FNV-1a 64).
    fn text_hash(text: &str) -> i64 {
        let mut h: i64 = 0xcbf2_9ce4_8422_2325u64 as i64;
        for b in text.bytes() {
            h ^= i64::from(b);
            h = h.wrapping_mul(0x0000_0100_0000_01b3);
        }
        h
    }

    fn value_lists_equal(
        heap: &Heap,
        a: &[Value],
        b: &[Value],
        active: &mut Option<std::collections::HashSet<GcRef>>,
    ) -> bool {
        a.len() == b.len()
            && a.iter()
                .zip(b)
                .all(|(x, y)| Self::values_equal_inner(heap, x, y, active))
    }

    fn value_sets_equal(
        heap: &Heap,
        a: &[Value],
        b: &[Value],
        active: &mut Option<std::collections::HashSet<GcRef>>,
    ) -> bool {
        if a.len() != b.len() {
            return false;
        }
        // O(n^2) membership test; sets are small in practice.
        a.iter().all(|x| {
            b.iter()
                .any(|y| Self::values_equal_inner(heap, x, y, active))
        }) && b.iter().all(|y| {
            a.iter()
                .any(|x| Self::values_equal_inner(heap, x, y, active))
        })
    }

    fn value_maps_equal(
        heap: &Heap,
        a: &[(Value, Value)],
        b: &[(Value, Value)],
        active: &mut Option<std::collections::HashSet<GcRef>>,
    ) -> bool {
        if a.len() != b.len() {
            return false;
        }
        a.iter().all(|(k1, v1)| {
            b.iter()
                .find(|(k2, _)| Self::values_equal_inner(heap, k1, k2, active))
                .is_some_and(|(_, v2)| Self::values_equal_inner(heap, v1, v2, active))
        })
    }

    fn list_add(&mut self, list: GcRef, item: Value) -> Result<(), VmError> {
        crate::vm::collections::list_push(self, list, item)
    }

    pub(crate) fn current_location(&self) -> Option<(String, u32)> {
        let frame = self.frames.last()?;
        let f = &self.shared.module.functions[frame.fid as usize];
        let line = match &self.shared.decoded[frame.fid as usize] {
            Ok(fc) => fc.lines.get(frame.ip as usize).copied().unwrap_or(0),
            Err(_) => 0,
        };
        let file = self
            .shared
            .module
            .sources
            .get(f.source_file as usize)
            .cloned()
            .unwrap_or_default();
        Some((file, line))
    }

    /// Snapshot runtime memory-management counters for diagnostics and
    /// benchmarks. This is a host/runtime API, not a Solvik source feature.
    pub fn heap_stats(&self) -> HeapStats {
        self.heap().stats()
    }

    pub(crate) fn err_at(&self, message: impl Into<String>) -> VmError {
        let mut e = VmError::new(message);
        e.location = self.current_location();
        e
    }

    /// Run bounded ARC maintenance when we are the only active Solvik thread.
    /// Atomic reference counting handles ordinary objects on the release path;
    /// this maintenance point is reserved for deferred candidate work and
    /// unreachable cycles.
    fn maybe_gc(&self) {
        if self.shared.active_threads.load(Ordering::SeqCst) > 1 {
            return;
        }
        let mut heap = self.shared.heap.lock().unwrap_or_else(|e| e.into_inner());
        if !heap.maintenance_due() {
            return;
        }
        #[cfg(test)]
        {
            // The legacy in-crate heap tests intentionally allocate an
            // unrooted handle directly through a VM helper. Keep that debug
            // diagnostic path available without making it part of release
            // runtime maintenance.
            let mut roots: Vec<Value> = self.stack.to_vec();
            roots.extend(
                self.shared
                    .str_consts
                    .iter()
                    .flatten()
                    .copied()
                    .map(Value::Object),
            );
            roots.extend(heap.statics.iter().flatten().copied());
            if let Some(e) = &self.pending_throw {
                roots.push(*e);
            }
            if let Some(e) = &self.rethrow {
                roots.push(*e);
            }
            if let Some(PendingReturn::Value(v)) = &self.pending_return {
                roots.push(*v);
            }
            heap.collect(&roots);
        }
        #[cfg(not(test))]
        heap.collect_cycles(256);
    }

    // ------------------------------------------------------------------
    // Call machinery
    // ------------------------------------------------------------------

    /// Call function `fid` with `args` already evaluated (not on the stack).
    fn call_function(&mut self, fid: u32, args: &[Value]) -> Result<(), VmError> {
        for a in args {
            self.push(*a);
        }
        self.call_stack(fid, args.len())
    }

    /// Call function `fid` whose `arity` arguments already sit on top of
    /// the operand stack. No temporary argument vector is allocated: the
    /// callee frame simply reuses the caller's argument slots as its first
    /// locals.
    fn call_stack(&mut self, fid: u32, arity: usize) -> Result<(), VmError> {
        let f = &self.shared.module.functions[fid as usize];
        if arity != f.params.len() {
            return Err(self.err_at(format!(
                "call to '{}' passes {} args, expected {}",
                f.name,
                arity,
                f.params.len()
            )));
        }
        let base = self.stack.len() - arity;
        // Locals: arguments occupy the first slots; pad the rest with null.
        self.stack
            .resize(base + f.local_count as usize, Value::Null);
        // Reserve the verified maximum operand depth so executing this frame
        // does not reallocate the stack vector (capacity, not fake elements).
        self.stack
            .reserve(base + f.local_count as usize + f.max_stack as usize);
        self.frames.push(CallFrame {
            fid,
            ip: 0,
            base,
            args_count: arity as u16,
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
        let returns_value = self.shared.module.functions[frame.fid as usize].returns_value;
        // Drop this frame's local slots; the caller's stack is below `base`.
        let target = frame.base;
        self.truncate_owned_stack(target);
        self.try_regions
            .retain(|r| r.frame_depth <= self.frames.len());
        Ok(returns_value)
    }

    /// Throw a runtime exception value, unwinding try regions. A new
    /// exception supersedes any deferred return or diverted break/continue.
    fn do_throw(&mut self, exc: Value) -> Result<(), VmError> {
        if !self.take_temp(exc) {
            self.retain_value(exc);
        }
        if let Some(previous) = self.pending_throw.replace(exc) {
            self.release_value(previous);
        }
        if let Some(PendingReturn::Value(previous)) = self.pending_return.take() {
            self.release_value(previous);
        }
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
                    self.release_value(exc);
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
                self.truncate_owned_stack(target);
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
                self.push(exc);
                self.release_value(exc);
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
        self.truncate_owned_stack(base);
    }
}

fn value_to_int(v: &Value) -> i64 {
    match v {
        Value::Boolean(b) => i64::from(*b),
        other => other.int_value().unwrap_or(0),
    }
}

// ---------------------------------------------------------------------------
// Execution loop
// ---------------------------------------------------------------------------

impl Vm {
    fn pop(&mut self) -> Value {
        // Verified bytecode never pops an empty operand region; the Null
        // fallback keeps the raw-API behavior for unverified modules.
        debug_assert!(
            self.stack.len() > self.frames.last().map(|f| f.base).unwrap_or(0),
            "pop from empty operand stack"
        );
        let value = self.stack.pop().unwrap_or(Value::Null);
        if !value.is_null() {
            self.temp_values.push(value);
        }
        value
    }

    fn push(&mut self, v: Value) {
        if !v.is_null() && !self.take_temp(v) {
            self.retain_value(v);
        }
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
            Value::Boolean(b) => Ok(*b),
            _ => Err(self.err_at("expected Boolean")),
        }
    }

    /// Any integral value (Byte..Long) as i64.
    fn long_of(&self, v: &Value) -> Result<i64, VmError> {
        v.int_value()
            .ok_or_else(|| self.err_at("expected an integer"))
    }

    /// Build an integral value of the given width rank from an i64.
    /// Build a value of the given integral width; `None` when `v` does not
    /// fit (overflow is a runtime error, matching the language's checked
    /// integer semantics).
    fn int_value_of_rank(rank: u8, v: i64) -> Option<Value> {
        Some(match rank {
            0 => Value::Byte(i8::try_from(v).ok()?),
            1 => Value::Short(i16::try_from(v).ok()?),
            2 => Value::Integer(i32::try_from(v).ok()?),
            _ => Value::Long(v),
        })
    }

    /// Binary arithmetic dispatched on runtime value kinds. Integral
    /// operands compute in i64 and keep the wider operand's width (checked);
    /// floating operands compute in the wider precision; arbitrary-precision
    /// operands use exact math (division under the built-in decimal context).
    fn arith(&mut self, op: IrOp, a: &Value, b: &Value) -> Result<Value, VmError> {
        // Integral x integral.
        if let (Some(x), Some(ra)) = (a.int_value(), a.int_rank()) {
            if let (Some(y), Some(rb)) = (b.int_value(), b.int_rank()) {
                let r = ra.max(rb);
                let res = match op {
                    IrOp::Add => x.checked_add(y),
                    IrOp::Sub => x.checked_sub(y),
                    IrOp::Mul => x.checked_mul(y),
                    IrOp::Div => {
                        if y == 0 {
                            return Err(self.err_at("division by zero"));
                        }
                        x.checked_div(y)
                    }
                    IrOp::Mod => {
                        if y == 0 {
                            return Err(self.err_at("modulo by zero"));
                        }
                        x.checked_rem(y)
                    }
                    _ => unreachable!("arith opcode"),
                };
                let Some(res) = res else {
                    return Err(self.err_at("integer overflow"));
                };
                return Self::int_value_of_rank(r, res)
                    .ok_or_else(|| self.err_at("integer overflow"));
            }
        }
        // Floating x floating.
        if a.float_value().is_some() && b.float_value().is_some() {
            if matches!(a, Value::Float(_)) && matches!(b, Value::Float(_)) {
                let af = match a {
                    Value::Float(f) => *f,
                    _ => unreachable!(),
                };
                let bf = match b {
                    Value::Float(f) => *f,
                    _ => unreachable!(),
                };
                let r = match op {
                    IrOp::Add => af + bf,
                    IrOp::Sub => af - bf,
                    IrOp::Mul => af * bf,
                    IrOp::Div => af / bf,
                    IrOp::Mod => af % bf,
                    _ => unreachable!("arith opcode"),
                };
                return Ok(Value::Float(r));
            }
            let av = a.float_value().unwrap();
            let bv = b.float_value().unwrap();
            let r = match op {
                IrOp::Add => av + bv,
                IrOp::Sub => av - bv,
                IrOp::Mul => av * bv,
                IrOp::Div => av / bv,
                IrOp::Mod => av % bv,
                _ => unreachable!("arith opcode"),
            };
            return Ok(Value::Double(r));
        }
        // Integer x float: promote to the wider float kind.
        if a.num_f64().is_some() && b.num_f64().is_some() {
            let use_f32 = matches!(a, Value::Float(_)) && matches!(b, Value::Float(_))
                || (a.int_value().is_some() && matches!(b, Value::Float(_)))
                || (matches!(a, Value::Float(_)) && b.int_value().is_some());
            let av = a.num_f64().unwrap();
            let bv = b.num_f64().unwrap();
            let r = match op {
                IrOp::Add => av + bv,
                IrOp::Sub => av - bv,
                IrOp::Mul => av * bv,
                IrOp::Div => av / bv,
                IrOp::Mod => av % bv,
                _ => unreachable!("arith opcode"),
            };
            return if use_f32 {
                Ok(Value::Float(r as f32))
            } else {
                Ok(Value::Double(r))
            };
        }
        // Arbitrary precision (heap objects). Clone out of the heap under a
        // scoped guard so the result allocation can re-lock it.
        if let (Some(ra), Some(rb)) = (a.as_object(), b.as_object()) {
            #[derive(Clone)]
            enum BigPair {
                Int(crate::bignum::BigInt, crate::bignum::BigInt),
                Dec(crate::bignum::Dec, crate::bignum::Dec),
            }
            let pair = {
                let heap = self.heap();
                match (heap.get(ra), heap.get(rb)) {
                    (
                        Some(HeapObject::BigInteger { value: va }),
                        Some(HeapObject::BigInteger { value: vb }),
                    ) => Some(BigPair::Int(va.clone(), vb.clone())),
                    (
                        Some(HeapObject::BigDecimal { value: da }),
                        Some(HeapObject::BigDecimal { value: db }),
                    ) => Some(BigPair::Dec(da.clone(), db.clone())),
                    _ => None,
                }
            };
            if let Some(pair) = pair {
                return match pair {
                    BigPair::Int(va, vb) => {
                        let r = match op {
                            IrOp::Add => &va + &vb,
                            IrOp::Sub => &va - &vb,
                            IrOp::Mul => &va * &vb,
                            IrOp::Div => {
                                if vb.is_zero() {
                                    return Err(self.err_at("division by zero"));
                                }
                                va / &vb
                            }
                            IrOp::Mod => {
                                if vb.is_zero() {
                                    return Err(self.err_at("modulo by zero"));
                                }
                                va % &vb
                            }
                            _ => unreachable!("arith opcode"),
                        };
                        Ok(Value::Object(
                            self.alloc(HeapObject::BigInteger { value: r }),
                        ))
                    }
                    BigPair::Dec(da, db) => {
                        let r = match op {
                            IrOp::Add => da.add(&db),
                            IrOp::Sub => da.sub(&db),
                            IrOp::Mul => da.mul(&db),
                            IrOp::Div => {
                                da.div(&db).map_err(|_| self.err_at("division by zero"))?
                            }
                            IrOp::Mod => da.rem(&db).map_err(|_| self.err_at("modulo by zero"))?,
                            _ => unreachable!("arith opcode"),
                        };
                        Ok(Value::Object(
                            self.alloc(HeapObject::BigDecimal { value: r }),
                        ))
                    }
                };
            }
        }
        Err(self.err_at("type mismatch in arithmetic"))
    }

    /// Unary minus, preserving the operand's numeric type.
    fn negate(&mut self, v: &Value) -> Result<Value, VmError> {
        if let (Some(x), Some(rank)) = (v.int_value(), v.int_rank()) {
            let r = match rank {
                0 => i8::try_from(x)
                    .ok()
                    .and_then(|x| x.checked_neg())
                    .map(Value::Byte),
                1 => i16::try_from(x)
                    .ok()
                    .and_then(|x| x.checked_neg())
                    .map(Value::Short),
                2 => i32::try_from(x)
                    .ok()
                    .and_then(|x| x.checked_neg())
                    .map(Value::Integer),
                _ => x.checked_neg().map(Value::Long),
            };
            return r.ok_or_else(|| self.err_at("integer overflow"));
        }
        match v {
            Value::Float(f) => return Ok(Value::Float(-*f)),
            Value::Double(f) => return Ok(Value::Double(-*f)),
            Value::Object(r) => {
                // Clone out under a scoped guard; allocation re-locks.
                #[derive(Clone)]
                enum NegSrc {
                    Int(crate::bignum::BigInt),
                    Dec(crate::bignum::Dec),
                }
                let src = {
                    let heap = self.heap();
                    match heap.get(*r) {
                        Some(HeapObject::BigInteger { value }) => Some(NegSrc::Int(value.clone())),
                        Some(HeapObject::BigDecimal { value }) => Some(NegSrc::Dec(value.clone())),
                        _ => None,
                    }
                };
                if let Some(src) = src {
                    return match src {
                        NegSrc::Int(vi) => Ok(Value::Object(
                            self.alloc(HeapObject::BigInteger { value: -vi }),
                        )),
                        NegSrc::Dec(d) => Ok(Value::Object(
                            self.alloc(HeapObject::BigDecimal { value: d.neg() }),
                        )),
                    };
                }
            }
            _ => {}
        }
        Err(self.err_at("unary '-' requires a numeric operand"))
    }

    /// Read one String operand as text.
    #[cfg(test)]
    fn str_of(&self, v: &Value) -> Result<String, VmError> {
        let heap = self.heap();
        Ok(Self::str_ref(&heap, v)?.to_string())
    }

    /// Borrow a String operand's text from the heap without cloning.
    fn str_ref<'h>(heap: &'h Heap, v: &Value) -> Result<&'h str, VmError> {
        match v {
            Value::Object(r) => match heap.get(*r) {
                Some(HeapObject::String { text }) => Ok(text),
                _ => Err(VmError::new("expected String")),
            },
            _ => Err(VmError::new("expected String")),
        }
    }

    fn make_string(&mut self, text: String) -> Value {
        Value::Object(self.alloc(HeapObject::String { text }))
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

    /// Run until the frame stack is empty.
    fn execute(&mut self) -> Result<(), VmError> {
        self.execute_until(0)
    }

    /// Run until the frame stack shrinks to `stop_depth` frames (or
    /// empties). Used to execute a class initializer on top of an existing
    /// call stack, leaving the caller frames intact afterwards.
    fn execute_until(&mut self, stop_depth: usize) -> Result<(), VmError> {
        let module = self.shared.module.clone();
        // Own a shared-code reference independently of the mutable VM, so
        // fetching an instruction needs only the cached function slice.
        let decoded = self.shared.decoded.clone();
        let mut cur_fid: u32 = u32::MAX;
        let mut code: &[DecodedInstr] = &[];
        loop {
            if self.frames.len() <= stop_depth {
                return Ok(());
            }
            let frame = self.frames.last_mut().unwrap();
            let fid = frame.fid;
            if fid != cur_fid {
                cur_fid = fid;
                code = match &decoded[fid as usize] {
                    Ok(fc) => &fc.instrs,
                    Err(off) => {
                        return Err(self.err_at(format!("malformed bytecode at offset {}", off)))
                    }
                };
            }
            let ip = frame.ip as usize;
            let Some(d) = code.get(ip) else {
                // Fell off the end: implicit void return.
                self.return_from_frame()?;
                continue;
            };
            // The verifier's exact analysis bounds this frame's operand depth;
            // a violation means unverified or corrupted metadata.
            debug_assert!(
                self.stack.len()
                    <= frame.base
                        + module.functions[fid as usize].local_count as usize
                        + module.functions[fid as usize].max_stack as usize,
                "operand stack exceeded verified max_stack"
            );
            let (op, a0, a1, a2) = (d.op, d.a0, d.a1, d.a2);
            frame.ip += 1;
            let result = vm_dispatch!(self, &module, op, a0, a1, a2);
            self.release_temps();
            result?;
        }
    }

    /// Execute one instruction. Test/tooling entry point; the hot path uses
    /// the fused dispatch directly inside `execute`.
    #[cfg(test)]
    fn step(&mut self, module: &CodeModule, op: crate::ir::IrOp, a: &[u32]) -> Result<(), VmError> {
        // Unit tests and tooling use `step` with raw stack fixtures and may
        // keep returned handle values in Rust locals. Leave temporary
        // ownership in place; the VM destructor releases it after the test.
        vm_dispatch!(self, module, op, a[0], a[1], a[2])
    }

    // ---- typed comparison helpers ------------------------------------------

    /// Total ordering between two values for `<`, `<=`, `>`, `>=`.
    /// Delegates to the shared natural-ordering helper used by List.sort.
    fn value_cmp(&self, a: &Value, b: &Value) -> Option<std::cmp::Ordering> {
        let heap = self.heap();
        crate::vm::collections::value_cmp(&heap, a, b)
    }

    fn cmp_values(&mut self, op: IrOp) -> Result<(), VmError> {
        let b = self.pop();
        let a_ = self.pop();
        let ord = self.value_cmp(&a_, &b).ok_or_else(|| {
            self.err_at(match op {
                IrOp::Lt | IrOp::Le | IrOp::Gt | IrOp::Ge => "incomparable operands",
                _ => unreachable!(),
            })
        })?;
        let r = match op {
            IrOp::Lt => ord == std::cmp::Ordering::Less,
            IrOp::Le => ord != std::cmp::Ordering::Greater,
            IrOp::Gt => ord == std::cmp::Ordering::Greater,
            IrOp::Ge => ord != std::cmp::Ordering::Less,
            _ => unreachable!(),
        };
        self.push(Value::Boolean(r));
        Ok(())
    }

    /// Catch-clause matching for `Conforms(id, kind)`.
    fn conforms(&self, v: &Value, id: u16, kind: u8) -> Result<bool, VmError> {
        use crate::ir::conforms_kind;
        let Some(r) = v.as_object() else {
            return Ok(false);
        };
        let heap = self.heap();
        let Some(obj) = heap.get(r) else {
            return Ok(false);
        };
        Ok(match kind {
            conforms_kind::EXCEPTION => matches!(
                obj,
                HeapObject::Exception { kind, .. } if *kind == crate::types::native_kind::EXCEPTION
            ),
            conforms_kind::CLASS => {
                matches!(obj, HeapObject::Instance { class, .. } if *class == id)
            }
            conforms_kind::INTERFACE => {
                // The built-in Throwable interface matches every object.
                if id as u32 == crate::resolve::builtin::THROWSABLE {
                    true
                } else {
                    match obj {
                        HeapObject::Instance { class, .. } => self
                            .shared
                            .module
                            .classes
                            .get(*class as usize)
                            .is_some_and(|c| c.interfaces.iter().any(|&(i, _)| i == id as u32)),
                        // Built-in collection objects conform to the
                        // corresponding built-in interfaces.
                        HeapObject::List { .. }
                        | HeapObject::Stack { .. }
                        | HeapObject::Set { .. } => {
                            id as u32 == crate::resolve::builtin::ITERABLE
                                || id as u32 == crate::resolve::builtin::COUNTABLE
                        }
                        HeapObject::Map { .. } => id as u32 == crate::resolve::builtin::COUNTABLE,
                        _ => false,
                    }
                }
            }
            _ => false,
        })
    }

    /// Range check for float -> integer conversion. `i64::MAX as f64`
    /// rounds up to 2^63, so the Long upper bound needs an explicit
    /// `>= 2^63` test; smaller targets have exact f64 bounds.
    fn float_int_out_of_range(t: u8, f: f64, min: i64, max: i64) -> bool {
        if t == crate::ir::conv_target::LONG {
            f < i64::MIN as f64 || f >= 9223372036854775808.0
        } else {
            f < min as f64 || f > max as f64
        }
    }

    /// Central explicit-conversion engine backing `Convert` and the
    /// `*.from(...)` builtins.
    fn convert(&mut self, v: Value, target: u8) -> Result<Value, VmError> {
        use crate::ir::conv_target;
        // String operand text (if any); the heap guard is dropped at the
        // end of the block so later arms may mutate the heap.
        let s: Option<String> = {
            let heap = self.heap();
            match &v {
                Value::Object(r) => heap.get(*r).and_then(|o| match o {
                    HeapObject::String { text } => Some(text.clone()),
                    _ => None,
                }),
                _ => None,
            }
        };
        match target {
            t if t == conv_target::BYTE
                || t == conv_target::SHORT
                || t == conv_target::INTEGER
                || t == conv_target::LONG =>
            {
                let (name, min, max) = match t {
                    _ if t == conv_target::BYTE => ("Byte", i8::MIN as i64, i8::MAX as i64),
                    _ if t == conv_target::SHORT => ("Short", i16::MIN as i64, i16::MAX as i64),
                    _ if t == conv_target::INTEGER => ("Integer", i32::MIN as i64, i32::MAX as i64),
                    _ => ("Long", i64::MIN, i64::MAX),
                };
                let n: i64 = match &v {
                    Value::Byte(i) => *i as i64,
                    Value::Short(i) => *i as i64,
                    Value::Integer(i) => *i as i64,
                    Value::Long(i) => *i,
                    Value::Float(f) => {
                        let f = *f as f64;
                        if !f.is_finite() || Self::float_int_out_of_range(t, f, min, max) {
                            return Err(self.err_at(format!("value out of {} range", name)));
                        }
                        f as i64
                    }
                    Value::Double(f) => {
                        if !f.is_finite() || Self::float_int_out_of_range(t, *f, min, max) {
                            return Err(self.err_at(format!("value out of {} range", name)));
                        }
                        *f as i64
                    }
                    Value::Boolean(b) => i64::from(*b),
                    Value::Char(c) => *c as i64,
                    Value::Null => {
                        return Err(self.err_at(format!("cannot convert null to {}", name)))
                    }
                    Value::Object(r) => {
                        let heap = self.heap();
                        if let Some(HeapObject::BigInteger { value }) = heap.get(*r) {
                            value.to_i64().ok_or_else(|| {
                                self.err_at(format!("value out of {} range", name))
                            })?
                        } else if let Some(HeapObject::BigDecimal { value }) = heap.get(*r) {
                            value.to_bigint_truncating().to_i64().ok_or_else(|| {
                                self.err_at(format!("value out of {} range", name))
                            })?
                        } else if let Some(s) = &s {
                            s.trim().parse::<i64>().map_err(|_| {
                                self.err_at(format!("cannot parse {} from \"{}\"", name, s))
                            })?
                        } else {
                            return Err(self.err_at(format!("cannot convert to {}", name)));
                        }
                    }
                };
                if n < min || n > max {
                    return Err(self.err_at(format!("value out of {} range", name)));
                }
                Ok(match t {
                    _ if t == conv_target::BYTE => Value::Byte(n as i8),
                    _ if t == conv_target::SHORT => Value::Short(n as i16),
                    _ if t == conv_target::INTEGER => Value::Integer(n as i32),
                    _ => Value::Long(n),
                })
            }
            t if t == conv_target::FLOAT || t == conv_target::DOUBLE => {
                let name = if t == conv_target::FLOAT {
                    "Float"
                } else {
                    "Double"
                };
                let f: f64 = match &v {
                    Value::Byte(i) => *i as f64,
                    Value::Short(i) => *i as f64,
                    Value::Integer(i) => *i as f64,
                    Value::Long(i) => *i as f64,
                    Value::Float(f) => *f as f64,
                    Value::Double(f) => *f,
                    Value::Boolean(b) => f64::from(*b),
                    Value::Null => {
                        return Err(self.err_at(format!("cannot convert null to {}", name)))
                    }
                    Value::Object(r) => {
                        let heap = self.heap();
                        if let Some(HeapObject::BigInteger { value }) = heap.get(*r) {
                            value.to_f64().unwrap_or(f64::INFINITY)
                        } else if let Some(HeapObject::BigDecimal { value }) = heap.get(*r) {
                            value.to_f64()
                        } else if let Some(s) = &s {
                            s.trim().parse::<f64>().map_err(|_| {
                                self.err_at(format!("cannot parse {} from \"{}\"", name, s))
                            })?
                        } else {
                            return Err(self.err_at(format!("cannot convert to {}", name)));
                        }
                    }
                    Value::Char(_) => {
                        return Err(self.err_at(format!("cannot convert Char to {}", name)))
                    }
                };
                if !f.is_finite() && t == conv_target::FLOAT {
                    // Infinity/NaN are representable in Float; keep them.
                }
                Ok(if t == conv_target::FLOAT {
                    Value::Float(f as f32)
                } else {
                    Value::Double(f)
                })
            }
            conv_target::BOOLEAN => match &v {
                Value::Boolean(b) => Ok(Value::Boolean(*b)),
                Value::Null => Err(self.err_at("cannot convert null to Boolean")),
                Value::Object(_) => {
                    let Some(s) = &s else {
                        return Err(self.err_at("cannot convert to Boolean"));
                    };
                    // Conservative rule: only the canonical strings are
                    // accepted; numerics never convert to Boolean.
                    match s.trim() {
                        "true" => Ok(Value::Boolean(true)),
                        "false" => Ok(Value::Boolean(false)),
                        _ => {
                            Err(self.err_at(format!("cannot parse Boolean from \"{}\"", s.trim())))
                        }
                    }
                }
                _ => Err(self.err_at("cannot convert to Boolean")),
            },
            conv_target::CHAR => match &v {
                Value::Char(c) => Ok(Value::Char(*c)),
                Value::Null => Err(self.err_at("cannot convert null to Char")),
                Value::Object(_) => {
                    let Some(s) = &s else {
                        return Err(self.err_at("cannot convert to Char"));
                    };
                    let mut chars = s.chars();
                    match (chars.next(), chars.next()) {
                        (Some(c), None) => Ok(Value::Char(c)),
                        _ => Err(self.err_at("Char conversion requires exactly one character")),
                    }
                }
                _ => {
                    let n = match &v {
                        Value::Byte(i) => *i as i64,
                        Value::Short(i) => *i as i64,
                        Value::Integer(i) => *i as i64,
                        Value::Long(i) => *i,
                        _ => return Err(self.err_at("cannot convert to Char")),
                    };
                    let cp = u32::try_from(n)
                        .ok()
                        .and_then(char::from_u32)
                        .ok_or_else(|| self.err_at("not a valid code point"))?;
                    Ok(Value::Char(cp))
                }
            },
            conv_target::STRING => {
                let text = v.to_display(&self.heap());
                Ok(self.make_string(text))
            }
            conv_target::BIG_INTEGER => {
                use crate::bignum::BigInt;
                let value: BigInt = match &v {
                    Value::Byte(i) => BigInt::from(*i),
                    Value::Short(i) => BigInt::from(*i),
                    Value::Integer(i) => BigInt::from(*i),
                    Value::Long(i) => BigInt::from(*i),
                    Value::Boolean(b) => BigInt::from(i64::from(*b)),
                    Value::Null => return Err(self.err_at("cannot convert null to BigInteger")),
                    Value::Object(r) => {
                        let heap = self.heap();
                        if let Some(HeapObject::BigInteger { value }) = heap.get(*r) {
                            value.clone()
                        } else if let Some(HeapObject::BigDecimal { value }) = heap.get(*r) {
                            value.to_bigint_truncating()
                        } else if let Some(s) = &s {
                            BigInt::parse_bytes(s.trim().as_bytes(), 10).ok_or_else(|| {
                                self.err_at(format!("cannot parse BigInteger from \"{}\"", s))
                            })?
                        } else {
                            return Err(self.err_at("cannot convert to BigInteger"));
                        }
                    }
                    _ => return Err(self.err_at("cannot convert to BigInteger")),
                };
                Ok(Value::Object(self.alloc(HeapObject::BigInteger { value })))
            }
            conv_target::BIG_DECIMAL => {
                use crate::bignum::Dec;
                let value: Dec = match &v {
                    Value::Byte(i) => Dec::from_i64(*i as i64),
                    Value::Short(i) => Dec::from_i64(*i as i64),
                    Value::Integer(i) => Dec::from_i64(*i as i64),
                    Value::Long(i) => Dec::from_i64(*i),
                    Value::Float(f) => Dec::from_f32(*f)
                        .map_err(|_| self.err_at("cannot convert Float to BigDecimal"))?,
                    Value::Double(f) => Dec::from_f64(*f)
                        .map_err(|_| self.err_at("cannot convert Double to BigDecimal"))?,
                    Value::Boolean(b) => Dec::from_i64(i64::from(*b)),
                    Value::Null => return Err(self.err_at("cannot convert null to BigDecimal")),
                    Value::Object(r) => {
                        let heap = self.heap();
                        if let Some(HeapObject::BigDecimal { value }) = heap.get(*r) {
                            value.clone()
                        } else if let Some(HeapObject::BigInteger { value }) = heap.get(*r) {
                            Dec::from_bigint(value.clone())
                        } else if let Some(s) = &s {
                            Dec::parse(s.trim()).map_err(|_| {
                                self.err_at(format!("cannot parse BigDecimal from \"{}\"", s))
                            })?
                        } else {
                            return Err(self.err_at("cannot convert to BigDecimal"));
                        }
                    }
                    Value::Char(_) => return Err(self.err_at("cannot convert Char to BigDecimal")),
                };
                Ok(Value::Object(self.alloc(HeapObject::BigDecimal { value })))
            }
            _ => Err(self.err_at("unknown conversion target")),
        }
    }
}

impl Drop for Vm {
    fn drop(&mut self) {
        let mut values = std::mem::take(&mut self.stack);
        values.extend(std::mem::take(&mut self.temp_values));
        if let Some(value) = self.pending_throw.take() {
            values.push(value);
        }
        if let Some(value) = self.rethrow.take() {
            values.push(value);
        }
        if let Some(PendingReturn::Value(value)) = self.pending_return.take() {
            values.push(value);
        }
        if values.is_empty() {
            return;
        }
        let mut heap = self.shared.heap.lock().unwrap_or_else(|e| e.into_inner());
        for value in values {
            heap.release_value(value);
        }
    }
}

#[cfg(test)]
pub(crate) fn test_vm() -> Vm {
    Vm::new(SharedState::new(crate::bytecode::CodeModule {
        version: crate::bytecode::CodeModule::FORMAT_VERSION,
        constants: vec![],
        functions: vec![],
        classes: vec![],
        interfaces: vec![],
        dyn_names: vec![],
        entry: None,
        sources: vec![],
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ir::IrOp;
    use crate::stdlib::builtins::nat;

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
        assert!(matches!(
            vm.heap().get(stack),
            Some(HeapObject::Stack { .. })
        ));
        assert_eq!(
            crate::vm::collections::stack_len(&mut vm, stack).unwrap(),
            0
        );
    }

    fn concurrent_collection_ops(new: u16, operations: &[u16]) {
        concurrent_collection_ops_with_opcode(new, operations, None);
    }

    fn concurrent_collection_ops_with_opcode(new: u16, operations: &[u16], opcode: Option<IrOp>) {
        let mut vm = test_vm();
        let collection = natives::call_native(&mut vm, new, &[]).unwrap();
        let barrier = Arc::new(std::sync::Barrier::new(4));
        let handles: Vec<_> = (0..4)
            .map(|_| {
                let mut worker = Vm::new(vm.shared.clone());
                let barrier = barrier.clone();
                let operations = operations.to_vec();
                std::thread::spawn(move || {
                    barrier.wait();
                    for i in 0..2000 {
                        for op in &operations {
                            natives::call_native(
                                &mut worker,
                                *op,
                                &[collection, Value::Long(i % 8), Value::Long(i)],
                            )
                            .unwrap();
                        }
                        if let Some(op) = opcode {
                            let module = worker.shared.module.clone();
                            worker.push(collection);
                            worker.push(Value::Long(i % 8));
                            worker.step(&module, op, &[]).unwrap();
                            // MapRemove returns the removed value or null.
                            assert!(matches!(worker.pop(), Value::Null | Value::Long(_)));
                        }
                    }
                })
            })
            .collect();
        let results: Vec<_> = handles.into_iter().map(|h| h.join()).collect();
        assert!(
            results.iter().all(Result::is_ok),
            "concurrent collection operation panicked"
        );
    }

    #[test]
    fn concurrent_map_updates_do_not_use_stale_indices() {
        concurrent_collection_ops(nat::MAP_NEW, &[nat::MAP_PUT, nat::MAP_REMOVE]);
    }

    #[test]
    fn concurrent_map_remove_opcode_does_not_use_stale_indices() {
        concurrent_collection_ops_with_opcode(nat::MAP_NEW, &[nat::MAP_PUT], Some(IrOp::MapRemove));
    }

    #[test]
    fn concurrent_set_updates_do_not_use_stale_indices() {
        concurrent_collection_ops(nat::SET_NEW, &[nat::SET_ADD, nat::SET_REMOVE]);
    }

    #[test]
    fn concurrent_sort_opcode_preserves_appended_items() {
        let mut vm = test_vm();
        let list = natives::call_native(&mut vm, nat::LIST_NEW, &[]).unwrap();
        let barrier = Arc::new(std::sync::Barrier::new(4));
        let handles: Vec<_> = (0..4)
            .map(|_| {
                let mut worker = Vm::new(vm.shared.clone());
                let barrier = barrier.clone();
                std::thread::spawn(move || {
                    let module = worker.shared.module.clone();
                    barrier.wait();
                    for i in 0..300 {
                        natives::call_native(&mut worker, nat::LIST_ADD, &[list, Value::Long(i)])
                            .unwrap();
                        worker.push(list);
                        worker.step(&module, IrOp::ListSort, &[]).unwrap();
                        assert_eq!(worker.pop(), list);
                    }
                })
            })
            .collect();
        for handle in handles {
            handle.join().unwrap();
        }
        assert_eq!(
            natives::call_native(&mut vm, nat::LIST_SIZE, &[list]).unwrap(),
            Value::Integer(1200)
        );
        // Every append survived and the final order is the natural one:
        // each of the four threads appended 0..300.
        let items = crate::vm::collections::list_snapshot(&mut vm, list_ref_of(&list)).unwrap();
        let expected: Vec<Value> = (0..300i64)
            .flat_map(|i| std::iter::repeat_n(Value::Long(i), 4))
            .collect();
        assert_eq!(items, expected);
    }

    fn list_ref_of(v: &Value) -> GcRef {
        match v {
            Value::Object(r) => *r,
            _ => panic!("expected object"),
        }
    }

    #[test]
    fn concurrent_list_sort_does_not_use_stale_keys() {
        concurrent_collection_ops(
            nat::LIST_NEW,
            &[nat::LIST_ADD, nat::LIST_SORT, nat::LIST_CLEAR],
        );
    }

    #[test]
    fn native_collection_reads_preserve_content_equality_and_aliases() {
        let mut vm = test_vm();
        let key = Value::Object(vm.alloc_string("key"));
        let equal_key = Value::Object(vm.alloc_string("key"));
        let value = Value::Object(crate::vm::collections::list_alloc_with_items(
            &mut vm,
            vec![key],
        ));
        let map_ref = crate::vm::collections::map_alloc(&mut vm, 0);
        crate::vm::collections::map_put(&mut vm, map_ref, key, value).unwrap();
        let map = Value::Object(map_ref);
        assert_eq!(
            natives::call_native(&mut vm, nat::MAP_GET, &[map, equal_key]).unwrap(),
            value
        );
        assert_eq!(
            natives::call_native(&mut vm, nat::MAP_CONTAINS_KEY, &[map, equal_key]).unwrap(),
            Value::Boolean(true)
        );
        assert_eq!(
            natives::call_native(&mut vm, nat::MAP_GET, &[map, Value::Null]).unwrap(),
            Value::Null
        );
        assert_eq!(
            natives::call_native(&mut vm, nat::LIST_CONTAINS, &[value, equal_key]).unwrap(),
            Value::Boolean(true)
        );
        assert_eq!(
            natives::call_native(&mut vm, nat::LIST_INDEX_OF, &[value, equal_key]).unwrap(),
            Value::Integer(0)
        );
        let set_ref = crate::vm::collections::set_alloc(&mut vm, 0);
        crate::vm::collections::set_add(&mut vm, set_ref, key).unwrap();
        let set = Value::Object(set_ref);
        assert_eq!(
            natives::call_native(&mut vm, nat::SET_CONTAINS, &[set, equal_key]).unwrap(),
            Value::Boolean(true)
        );
    }

    #[test]
    fn list_spread_is_rejected() {
        let mut vm = test_vm();
        let list = crate::vm::collections::list_alloc_with_items(
            &mut vm,
            vec![Value::Long(1), Value::Long(2)],
        );
        vm.push(Value::Object(list));
        let module = vm.shared.module.clone();
        assert!(vm.step(&module, IrOp::ListSpread, &[]).is_err());
        // The source list and stack are untouched.
        assert_eq!(
            crate::vm::collections::list_snapshot(&mut vm, list).unwrap(),
            vec![Value::Long(1), Value::Long(2)]
        );
        assert_eq!(vm.pop(), Value::Object(list));
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

    #[cfg(unix)]
    #[test]
    fn process_println_appends_a_newline() {
        let mut vm = test_vm();
        let command = vm.make_string("cat".into());
        let args = natives::call_native(&mut vm, nat::LIST_NEW, &[]).unwrap();
        let process = natives::call_native(&mut vm, nat::PROC_NEW, &[command, args]).unwrap();
        natives::call_native(&mut vm, nat::PROC_START, &[process]).unwrap();
        let input = natives::call_native(&mut vm, nat::PROC_STDIN, &[process]).unwrap();
        let output = natives::call_native(&mut vm, nat::PROC_STDOUT, &[process]).unwrap();
        let text = vm.make_string("abc".into());
        natives::call_native(&mut vm, nat::PRINT, &[input, text]).unwrap();
        natives::call_native(&mut vm, nat::PRINTLN, &[input, text]).unwrap();
        // Close the pipe so cat exits even when println omits the newline.
        if let Some(HeapObject::Process { stdin, .. }) =
            vm.heap_mut().get_mut(process.as_object().unwrap())
        {
            stdin.take();
        }
        natives::call_native(&mut vm, nat::PROC_WAIT, &[process]).unwrap();
        let result = natives::call_native(&mut vm, nat::READ_ALL, &[output]).unwrap();
        assert_eq!(vm.str_of(&result).unwrap(), "abcabc\n");
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
            max_stack: 1,
            returns_value: true,
            code: vec![IrOp::LoadConst.code(), 0, 0, 0, 0, IrOp::Return.code()],
            line_map: vec![],
            source_file: 0,
        });
        let mut vm = Vm::new(SharedState::new(module));
        let expected = Value::Object(vm.shared.str_consts[0].unwrap());
        let count = vm.heap().live_count();
        for _ in 0..100 {
            vm.call_function(0, &[]).unwrap();
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
        // Only the six distinct text literals remain live; the per-thread
        // global stream handles (stdin/stdout/stderr) no longer exist, so
        // nothing beyond the literal pool is kept alive.
        assert_eq!(vm.heap().live_count(), texts.len());
        for (id, expected) in [
            Value::Null,
            Value::Boolean(true),
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
        // Only the six distinct text literals remain live; the former
        // per-thread global stream handles (stdin/stdout/stderr) no longer
        // exist, so they no longer keep objects alive.
        assert_eq!(vm.heap().live_count(), texts.len());
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
        // 4200 string constants survive; the per-thread global stream
        // handles are gone, so nothing beyond the literal pool is live.
        assert_eq!(vm.heap().live_count(), 4200);
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
    fn map_remove_opcode_uses_content_equality() {
        let mut vm = test_vm();
        // Two distinct String objects with the same text: the stored key
        // and the removal key.
        let key_a = vm.make_string("a".into());
        let key_b = vm.make_string("a".into());
        let key_c = vm.make_string("b".into());
        let map_ref = crate::vm::collections::map_alloc(&mut vm, 0);
        crate::vm::collections::map_put(&mut vm, map_ref, key_a, Value::Long(1)).unwrap();
        crate::vm::collections::map_put(&mut vm, map_ref, key_c, Value::Long(2)).unwrap();
        let map = Value::Object(map_ref);
        vm.stack = vec![map, key_b];
        let module = vm.shared.module.clone();
        vm.step(&module, IrOp::MapRemove, &[]).unwrap();
        // The opcode returns the removed value (Java Map.remove shape).
        assert_eq!(vm.pop(), Value::Long(1));
        let heap = vm.heap();
        let Some(HeapObject::Map { data }) = heap.get(map_ref) else {
            unreachable!()
        };
        let g = data.lock().unwrap();
        assert_eq!(g.len, 1);
        let entries = g.entries();
        assert!(matches!(entries[0].1, Value::Long(2)));
    }

    #[test]
    fn stack_get_rejects_negative_index() {
        let mut vm = test_vm();
        let stack_ref = crate::vm::collections::stack_alloc(&mut vm, 0);
        crate::vm::collections::stack_push(&mut vm, stack_ref, Value::Long(7)).unwrap();
        let stack = Value::Object(stack_ref);
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
        let child = Value::Object(crate::vm::collections::list_alloc_with_items(
            &mut vm,
            vec![Value::Long(7)],
        ));
        let parent = Value::Object(crate::vm::collections::list_alloc_with_items(
            &mut vm,
            vec![child, child],
        ));
        let json = natives::call_native(&mut vm, nat::JSON_STRINGIFY, &[parent]).unwrap();
        assert_eq!(vm.str_of(&json).unwrap(), "[[7],[7]]");
        let Value::Object(child_ref) = child else {
            unreachable!()
        };
        crate::vm::collections::list_push(&mut vm, child_ref, parent).unwrap();
        assert_eq!(
            natives::call_native(&mut vm, nat::JSON_STRINGIFY, &[parent])
                .unwrap_err()
                .message,
            "cyclic value is not JSON-serializable"
        );
        for object in [
            HeapObject::map(),
            HeapObject::Instance {
                class: 0,
                fields: vec![Value::Null],
            },
        ] {
            let r = vm.heap_mut().alloc(object);
            if matches!(vm.heap().get(r), Some(HeapObject::Map { .. })) {
                crate::vm::collections::map_put(&mut vm, r, Value::Long(1), Value::Object(r))
                    .unwrap();
            } else if let HeapObject::Instance { fields, .. } = vm.heap_mut().get_mut(r).unwrap() {
                fields[0] = Value::Object(r);
            } else {
                unreachable!();
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
            kind: crate::types::native_kind::EXCEPTION,
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
                        Value::Object(crate::vm::collections::list_alloc_with_items(
                            &mut vm,
                            vec![Value::Long(7), Value::Long(8)],
                        ))
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
                        let items = crate::vm::collections::list_snapshot(&mut vm, r).unwrap();
                        assert!(matches!(items[0], Value::Long(7)));
                    }
                }
            }
        }
    }
}
