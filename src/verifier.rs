//! Bytecode verifier: validates a compiled module before execution.
//!
//! Checks performed per function:
//!   * every instruction decodes cleanly and stays in bounds
//!   * jump/handler targets land on instruction boundaries
//!   * constant/local/function/class/interface indices are in range
//!   * call arities match the callee's parameter count, and every possible
//!     virtual/interface dispatch target agrees on arity and return shape
//!   * exact operand-stack propagation over a finite abstract state
//!     (stack height plus the active try-region stack): every basic block
//!     receives exactly one consistent state; incompatible joins are
//!     rejected with both predecessor paths identified
//!   * try/finally region discipline: `TryEnd` matches an active region,
//!     finally bodies restore the region-entry stack height at `FinallyEnd`,
//!     and break/continue resume points join consistently
//!   * return heights, missing value returns, and implicit exit heights
//!
//! The analysis is a worklist over a finite domain: heights are bounded by
//! the instruction count and the number of processed states by a multiple of
//! the block count, so it always terminates and rejects deterministically
//! when a bound is exceeded. There is no maximum-height merge or other
//! permissive fallback. See `docs/VERIFIER.md` for the full contract.

use crate::bytecode::CodeModule;
use crate::diagnostic::Diagnostics;
use crate::ir::IrOp;
use crate::stdlib::builtins;
use std::collections::{HashMap, HashSet, VecDeque};

struct Instr {
    offset: u32,
    op: IrOp,
    /// Decoded operands (u32-normalized).
    args: [u32; 3],
}

/// Decode one instruction at `pos`; returns (instr, next_pos) or an error.
fn decode_instr(code: &[u8], pos: usize) -> Result<(Instr, usize), String> {
    if pos >= code.len() {
        return Err(format!("code ends mid-instruction at offset {}", pos));
    }
    let byte = code[pos];
    let pos1 = pos + 1;
    let op = match IrOp::from_code(byte) {
        Some(o) => o,
        None => return Err(format!("unknown opcode 0x{:02x} at offset {}", byte, pos)),
    };
    let mut args = [0; 3];
    let mut p = pos1;
    for (i, arg) in args.iter_mut().enumerate().take(op.operand_count()) {
        let size = op.operand_size(i);
        if p + size > code.len() {
            return Err(format!(
                "truncated operands for opcode 0x{:02x} at offset {}",
                byte, pos
            ));
        }
        let mut v: u32 = 0;
        for i in 0..size {
            v |= (code[p + i] as u32) << (8 * i);
        }
        *arg = v;
        p += size;
    }
    Ok((
        Instr {
            offset: pos as u32,
            op,
            args,
        },
        p,
    ))
}

/// One active try region, packed: operand height right after the region's
/// `TryBegin` plus a finally flag. Packed into a `u64` so region stacks hash
/// and compare cheaply.
type RegionSlot = u64;

fn region_pack(base: u32, has_finally: bool) -> RegionSlot {
    ((base as u64) << 1) | u64::from(has_finally)
}

fn region_has_finally(slot: RegionSlot) -> bool {
    slot & 1 == 1
}

/// Abstract state at a block entry: operand height, the stack of active try
/// regions (innermost last), and whether a non-normal transfer (a passed-
/// through exception, a deferred return, or a diverted break/continue) is
/// pending. The VM's `rethrow` slot is sticky: it survives nested catches
/// and suppresses the fallthrough of every `FinallyEnd` until consumed.
#[derive(Clone, PartialEq, Eq, Hash)]
struct StateKey {
    block: usize,
    height: u32,
    regions: Vec<RegionSlot>,
    diverted: bool,
}

/// A control-flow edge produced by simulating one block.
struct Edge {
    block: usize,
    height: u32,
    regions: Vec<RegionSlot>,
    diverted: bool,
    /// Offset of the instruction that created the edge (for diagnostics).
    src: u32,
}

/// One verification error with stable reporting metadata.
#[derive(Clone, PartialEq, Eq)]
struct FnError {
    code: &'static str,
    offset: Option<u32>,
    msg: String,
}

/// Stack effect of an instruction: Some(delta) or None for terminators
/// (`Return`, `ReturnVoid`, `Throw`). A terminator has no normal successor;
/// its exceptional/diverted continuations are summarized by the enclosing
/// `TryBegin` edges.
fn stack_effect(instr: &Instr, module: &CodeModule) -> Option<i32> {
    use IrOp::*;
    let delta: i32 = match instr.op {
        LoadConst | LoadLocal | LoadGlobal => 1,
        StoreLocal | StoreGlobal | Pop | ListAdd | ListRemove | StackPush => -1,
        AddLong | SubLong | MulLong | DivLong | ModLong | AddDouble | SubDouble | MulDouble
        | DivDouble | ModDouble | And | EqLong | EqDouble | EqBool | EqChar | EqString
        | EqObject | EqEnum | EqDyn | LtLong | LeLong | GtLong | GeLong | LtDouble | LeDouble
        | GtDouble | GeDouble | LtChar | LeChar | GtChar | GeChar | LtString | LeString
        | GtString | GeString | LtDyn | LeDyn | GtDyn | GeDyn | ListGet | ListContains
        | ListIndexOf | ListJoin | MapGet | MapRemove | MapContainsKey | StackGet | StrContains
        | StrStartsWith | StrEndsWith | StrSplit | StrIndex | StrCharAt | IdentityEq
        | IdentityNe => -1,
        ListSet | MapPut | StrSubstr | StrReplace => -2,
        StrConcat => -1,
        // NewEnum pops the payload (if any) and pushes the enum value.
        NewEnum => {
            if instr.args[2] != 0 {
                0
            } else {
                1
            }
        }
        NegLong | NegDouble | Not | IsNull | NullCheck | ToLong | ToDouble | ToByte | ToBool
        | ToChar | ToStringValue | ListLen | ListReverse | ListSort | ListClear | TryEnd
        | MapLen | MapKeys | MapValues | MapClear | StackLen | StackEmpty | StrLen | StrTrim
        | StrUpper | StrLower | EnumIndex | EnumPayload | Jump | GcHint | TryBegin | FinallyEnd
        | FinallyDivert | StackPop | StackPeek | ListSpread => 0,
        // Conditional jumps consume the condition.
        JumpIfFalse | JumpIfTrue => -1,
        // Fixed-stack collection append: [dest, src] -> [dest].
        ListExtend => -1,
        // LoadField consumes the receiver: [recv] -> [value].
        // StoreField pops the value, keeps the receiver: [recv, value] -> [recv].
        // NewObject allocates and pushes: +1.
        LoadField => 0,
        StoreField => -1,
        // CopyFields pops src+dst, pushes dst: net -1.
        CopyFields => -1,
        NewObject | NewList | NewMap | NewStack => 1,
        Dup => 1,
        CallFn | CallStatic => {
            let fid = instr.args[0] as usize;
            let arity = instr.args[1] as i32;
            let rets = module
                .functions
                .get(fid)
                .map(|f| f.returns_value)
                .unwrap_or(false) as i32;
            rets - arity
        }
        CallVirtual | CallInterface | CallSuper => {
            let arity = instr.args[2] as i32;
            // Dispatch consistency is validated structurally: every possible
            // target agrees on arity and return shape, so any one target
            // determines the effect.
            let rets = call_target_function(instr, module)
                .and_then(|fid| module.functions.get(fid as usize))
                .map(|f| f.returns_value)
                .unwrap_or(false) as i32;
            // Receiver sits below the arguments on the stack.
            rets - arity - 1
        }
        CallNative => {
            let native = instr.args[0] as u16;
            let arity = instr.args[1] as i32;
            let rets = builtins::native_returns_value(native) as i32;
            // Instance natives take the receiver as args[0].
            let recv = if builtins::native_takes_receiver(native) {
                1
            } else {
                0
            };
            rets - arity - recv
        }
        CallDynamic => {
            let arity = instr.args[1] as i32;
            // Receiver plus arguments are consumed; one result produced.
            -arity
        }
        Return | ReturnVoid | Throw => return None,
    };
    Some(delta)
}

/// Minimum operands consumed, independent of the net stack-height change.
fn required_stack(instr: &Instr, module: &CodeModule) -> i32 {
    use IrOp::*;
    match instr.op {
        LoadConst | LoadLocal | LoadGlobal | NewObject | NewList | NewMap | NewStack | Jump
        | TryBegin | TryEnd | FinallyEnd | FinallyDivert | GcHint | ReturnVoid => 0,
        StoreLocal | StoreGlobal | Pop | Throw | JumpIfFalse | JumpIfTrue | Return | Dup
        | ListExtend => 1,
        StoreField | ListAdd | ListRemove | StackPush | CopyFields => 2,
        NewEnum => i32::from(instr.args[2] != 0),
        CallFn | CallStatic => instr.args[1] as i32,
        CallVirtual | CallInterface | CallSuper => instr.args[2] as i32 + 1,
        CallDynamic => instr.args[1] as i32 + 1,
        CallNative => {
            instr.args[1] as i32 + i32::from(builtins::native_takes_receiver(instr.args[0] as u16))
        }
        // Every remaining instruction consumes operands and produces one value.
        _ => 1 - stack_effect(instr, module).unwrap_or(0),
    }
}

/// Map a virtual/interface/super call to one concrete function id (any
/// possible target; all targets are checked to agree structurally).
fn call_target_function(instr: &Instr, module: &CodeModule) -> Option<u32> {
    use IrOp::*;
    match instr.op {
        CallVirtual => {
            let class = instr.args[0] as usize;
            let slot = instr.args[1] as usize;
            module.classes.get(class)?.vtable.get(slot).copied()
        }
        CallInterface => {
            let iface = instr.args[0] as usize;
            let slot = instr.args[1] as usize;
            if let Some(d) = module
                .interfaces
                .get(iface)?
                .defaults
                .get(slot)
                .copied()
                .flatten()
            {
                return Some(d);
            }
            module
                .classes
                .iter()
                .find_map(|c| c.interfaces.iter().find(|(iid, _)| *iid == iface as u32))
                .and_then(|(_, fids)| fids.get(slot).copied())
        }
        CallSuper => {
            let class = instr.args[0] as usize;
            let slot = instr.args[1] as usize;
            module.classes.get(class)?.vtable.get(slot).copied()
        }
        _ => None,
    }
}

/// Result of simulating one basic block from a given entry state.
struct SimResult {
    edges: Vec<Edge>,
    errors: Vec<FnError>,
    /// (FinallyEnd index, height there, finally-entry block) checks that can
    /// only be resolved once all block heights have converged.
    pending_fe: Vec<(usize, u32, usize)>,
    /// Implicit function exit: (height, source offset).
    exit: Option<(u32, u32)>,
    /// Maximum operand height reached on any path through this block
    /// (including handler-entry heights produced by its `TryBegin`).
    max_h: u32,
}

impl SimResult {
    /// Record that some reachable path reaches operand height `h`.
    fn bump(&mut self, h: u32) {
        self.max_h = self.max_h.max(h);
    }
}

/// Add a control-flow edge, rejecting heights beyond the analysis bound.
#[allow(clippy::too_many_arguments)]
fn push_edge(
    r: &mut SimResult,
    fname: &str,
    offset_block: &HashMap<u32, usize>,
    target_off: u32,
    height: u32,
    regions: &[RegionSlot],
    diverted: bool,
    src: u32,
    height_cap: u32,
) {
    if let Some(tb) = offset_block.get(&target_off) {
        if height > height_cap {
            r.errors.push(FnError {
                code: "V014",
                offset: Some(src),
                msg: format!(
                    "function '{}' stack height exceeds the analysis bound at offset {}",
                    fname, src
                ),
            });
            return;
        }
        r.edges.push(Edge {
            block: *tb,
            height,
            regions: regions.to_vec(),
            diverted,
            src,
        });
    }
}

/// Simulate one basic block from entry state (h_in, regions_in), producing
/// successor edges and local errors. Block `b` spans instrs[s..e].
#[allow(clippy::too_many_arguments)]
fn simulate_block(
    fname: &str,
    returns_value: bool,
    module: &CodeModule,
    instrs: &[Instr],
    blocks: &[(usize, usize)],
    offset_block: &HashMap<u32, usize>,
    fe_entry: &[Option<usize>],
    block_state: &[Option<(u32, Vec<RegionSlot>)>],
    b: usize,
    h_in: u32,
    regions_in: &[RegionSlot],
    diverted_in: bool,
    height_cap: u32,
) -> SimResult {
    let mut r = SimResult {
        edges: vec![],
        errors: vec![],
        pending_fe: vec![],
        exit: None,
        max_h: 0,
    };
    let (s_, e_) = blocks[b];
    let mut h = h_in;
    let mut regions: Vec<RegionSlot> = regions_in.to_vec();
    let mut diverted = diverted_in;
    for idx in s_..e_ {
        let instr = &instrs[idx];
        // Every operand height at an instruction boundary is a reachable
        // runtime height; record it for the exact maximum.
        r.bump(h);
        if h < required_stack(instr, module) as u32 {
            r.errors.push(FnError {
                code: "V004",
                offset: Some(instr.offset),
                msg: format!(
                    "function '{}' stack underflow at offset {} (height {} < {} required)",
                    fname,
                    instr.offset,
                    h,
                    required_stack(instr, module)
                ),
            });
            return r;
        }
        match instr.op {
            IrOp::Jump => {
                push_edge(
                    &mut r,
                    fname,
                    offset_block,
                    instr.args[0],
                    h,
                    &regions,
                    diverted,
                    instr.offset,
                    height_cap,
                );
                return r; // rest of block unreachable
            }
            IrOp::JumpIfFalse | IrOp::JumpIfTrue => {
                push_edge(
                    &mut r,
                    fname,
                    offset_block,
                    instr.args[0],
                    h - 1,
                    &regions,
                    diverted,
                    instr.offset,
                    height_cap,
                );
                h -= 1; // fall-through continues
            }
            IrOp::Return => {
                if !returns_value {
                    r.errors.push(FnError {
                        code: "V005",
                        offset: Some(instr.offset),
                        msg: format!(
                            "function '{}' returns a value at offset {} but is declared void",
                            fname, instr.offset
                        ),
                    });
                }
                if h != 1 {
                    r.errors.push(FnError {
                        code: "V005",
                        offset: Some(instr.offset),
                        msg: format!(
                            "function '{}' returns with stack height {} at offset {} (expected 1)",
                            fname, h, instr.offset
                        ),
                    });
                }
                return r;
            }
            IrOp::ReturnVoid => {
                if returns_value {
                    r.errors.push(FnError {
                        code: "V005",
                        offset: Some(instr.offset),
                        msg: format!(
                            "function '{}' returns void at offset {} but is declared to return a value",
                            fname, instr.offset
                        ),
                    });
                }
                if h != 0 {
                    r.errors.push(FnError {
                        code: "V005",
                        offset: Some(instr.offset),
                        msg: format!(
                            "function '{}' returns void with stack height {} at offset {} (expected 0)",
                            fname, h, instr.offset
                        ),
                    });
                }
                return r;
            }
            IrOp::Throw => {
                // Terminator: the VM unwinds to a catch/finally or fails; it
                // never falls through. Handler continuations are summarized
                // by the enclosing TryBegin edges.
                return r;
            }
            IrOp::TryBegin => {
                let c = instr.args[0];
                let fi = instr.args[1];
                // catch_ip == finally_ip encodes "no catch"; finally_ip == 0
                // encodes "no finally". On both exceptional entries the VM
                // has already popped this region and reset the stack to its
                // base (plus the exception value for a catch). A catch entry
                // consumes its exception but inherits any older pending
                // transfer (the VM's rethrow slot is sticky); a finally entry
                // always carries a pending transfer.
                if c != 0 && c != fi {
                    // The catch entry receives the exception value.
                    r.bump(h + 1);
                    push_edge(
                        &mut r,
                        fname,
                        offset_block,
                        c,
                        h + 1,
                        &regions,
                        diverted,
                        instr.offset,
                        height_cap,
                    );
                }
                if fi != 0 {
                    push_edge(
                        &mut r,
                        fname,
                        offset_block,
                        fi,
                        h,
                        &regions,
                        true,
                        instr.offset,
                        height_cap,
                    );
                }
                regions.push(region_pack(h, fi != 0));
            }
            IrOp::TryEnd => {
                if regions.is_empty() {
                    r.errors.push(FnError {
                        code: "V013",
                        offset: Some(instr.offset),
                        msg: format!(
                            "function '{}' TryEnd at offset {} without an active try region",
                            fname, instr.offset
                        ),
                    });
                    return r;
                }
                regions.pop();
            }
            IrOp::FinallyDivert => {
                // The VM diverts to the innermost finally (popping that
                // region and resetting the stack to its base) or falls
                // through when the innermost region has no finally. The
                // diverted path re-enters this same fallthrough point via
                // the finally body's FinallyEnd, which consumes the pending
                // resume, so model the state that survives at the resume
                // point.
                if regions.last().is_some_and(|slot| region_has_finally(*slot)) {
                    regions.pop();
                    diverted = false;
                }
            }
            IrOp::FinallyEnd => {
                // A finally body must restore the region-entry stack height:
                // the resume path continues without a stack reset, and the
                // rethrow/return paths discard nothing the caller needs.
                match fe_entry[idx] {
                    Some(fe_idx) => {
                        let off = instrs[fe_idx].offset;
                        if let Some(hb) = offset_block.get(&off) {
                            match &block_state[*hb] {
                                Some((entry_h, _)) if *entry_h != h => {
                                    r.errors.push(FnError {
                                        code: "V013",
                                        offset: Some(instr.offset),
                                        msg: format!(
                                            "function '{}' finally body ending at offset {} leaves stack height {} (expected {}, the region-entry height)",
                                            fname, instr.offset, h, entry_h
                                        ),
                                    });
                                }
                                None => r.pending_fe.push((idx, h, *hb)),
                                _ => {}
                            }
                        }
                    }
                    None => {
                        r.errors.push(FnError {
                            code: "V013",
                            offset: Some(instr.offset),
                            msg: format!(
                                "function '{}' FinallyEnd at offset {} does not match any finally region",
                                fname, instr.offset
                            ),
                        });
                    }
                }
                // A pending transfer (passed-through exception, deferred
                // return, or diverted break/continue) is consumed here:
                // the VM rethrows, returns, or resumes instead of falling
                // through.
                if diverted {
                    return r;
                }
                diverted = false;
            }
            _ => {
                let d = stack_effect(instr, module).unwrap_or(0);
                h = match h.checked_add_signed(d) {
                    Some(v) if v <= height_cap => v,
                    Some(_) => {
                        r.errors.push(FnError {
                            code: "V014",
                            offset: Some(instr.offset),
                            msg: format!(
                                "function '{}' stack height exceeds the analysis bound at offset {}",
                                fname, instr.offset
                            ),
                        });
                        return r;
                    }
                    None => {
                        r.errors.push(FnError {
                            code: "V004",
                            offset: Some(instr.offset),
                            msg: format!(
                                "function '{}' stack underflow at offset {}",
                                fname, instr.offset
                            ),
                        });
                        return r;
                    }
                };
            }
        }
    }
    // Fell off the end of the block: continue into the next block, or
    // (for the last block) record the implicit function exit height.
    let last_off = instrs[e_ - 1].offset;
    if b + 1 < blocks.len() {
        r.edges.push(Edge {
            block: b + 1,
            height: h,
            regions,
            diverted,
            src: last_off,
        });
    } else {
        r.exit = Some((h, last_off));
    }
    r
}

/// Record one per-instruction operand/reference error.
fn operand_error(
    errors: &mut Vec<FnError>,
    fname: &str,
    offset: u32,
    code: &'static str,
    msg: String,
) {
    errors.push(FnError {
        code,
        offset: Some(offset),
        msg: format!("function '{}' at offset {}: {}", fname, offset, msg),
    });
}

/// Verify one function; returns its errors plus the exact maximum
/// operand-stack height (above the local region) over all accepted paths.
/// Module-level context is used for dispatch consistency.
#[allow(clippy::too_many_arguments)]
fn verify_function(
    module: &CodeModule,
    fidx: usize,
    subclass_of: &[Vec<usize>],
    impls_of: &[Vec<usize>],
) -> (Vec<FnError>, u32) {
    let f = &module.functions[fidx];
    let name = f.name.clone();
    // Decode all instructions.
    let mut instrs: Vec<Instr> = Vec::new();
    let mut pos = 0usize;
    while pos < f.code.len() {
        match decode_instr(&f.code, pos) {
            Ok((instr, next)) => {
                pos = next;
                instrs.push(instr);
            }
            Err(msg) => {
                return (
                    vec![FnError {
                        code: "V001",
                        offset: None,
                        msg: format!("function '{}': {}", name, msg),
                    }],
                    0,
                );
            }
        }
    }
    let n = instrs.len();
    let offsets: HashSet<u32> = instrs.iter().map(|i| i.offset).collect();
    let mut max_h = 0u32;

    // Validate each instruction's operands.
    let mut errors: Vec<FnError> = vec![];
    for instr in &instrs {
        use IrOp::*;
        match instr.op {
            ListSpread => operand_error(
                &mut errors,
                &name,
                instr.offset,
                "V015",
                "opcode ListSpread is not part of the accepted bytecode contract; \
                 variadic spread compiles to NewList/ListAdd/ListExtend"
                    .to_string(),
            ),
            LoadConst => {
                if instr.args[0] as usize >= module.constants.len() {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("constant index {} out of range", instr.args[0]),
                    );
                }
            }
            LoadLocal | StoreLocal => {
                if instr.args[0] as usize >= f.local_count as usize {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!(
                            "local slot {} out of range ({} locals)",
                            instr.args[0], f.local_count
                        ),
                    );
                }
            }
            LoadGlobal | StoreGlobal => {
                if instr.args[0] > 2 {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("global slot {} out of range", instr.args[0]),
                    );
                }
            }
            Jump | JumpIfFalse | JumpIfTrue => {
                if !offsets.contains(&instr.args[0]) {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!(
                            "jump target {} is not an instruction boundary",
                            instr.args[0]
                        ),
                    );
                }
            }
            TryBegin => {
                for t in &instr.args {
                    if *t != 0 && !offsets.contains(t) {
                        operand_error(
                            &mut errors,
                            &name,
                            instr.offset,
                            "V002",
                            format!("try target {} is not an instruction boundary", t),
                        );
                    }
                }
            }
            CallFn | CallStatic => {
                let fid = instr.args[0] as usize;
                let arity = instr.args[1];
                match module.functions.get(fid) {
                    Some(callee) => {
                        if arity as usize != callee.params.len() {
                            operand_error(
                                &mut errors,
                                &name,
                                instr.offset,
                                "V002",
                                format!(
                                    "call to '{}' passes {} args, expected {}",
                                    callee.name,
                                    arity,
                                    callee.params.len()
                                ),
                            );
                        }
                    }
                    None => operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("function id {} out of range", fid),
                    ),
                }
                if instr.op == CallStatic
                    && instr.args[2] != u16::MAX as u32
                    && instr.args[2] as usize >= module.classes.len()
                {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("target class id {} out of range", instr.args[2]),
                    );
                }
            }
            CallVirtual => {
                let class = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                let arity = instr.args[2] as i32;
                match module.classes.get(class) {
                    Some(c) => {
                        let mut targets: Vec<u32> = vec![];
                        match c.vtable.get(slot) {
                            Some(&fid) => targets.push(fid),
                            None => operand_error(
                                &mut errors,
                                &name,
                                instr.offset,
                                "V002",
                                format!("vtable slot {} out of range in class {}", slot, class),
                            ),
                        }
                        for &sc in &subclass_of[class] {
                            match module.classes[sc].vtable.get(slot) {
                                Some(&fid) => targets.push(fid),
                                None => operand_error(
                                    &mut errors,
                                    &name,
                                    instr.offset,
                                    "V011",
                                    format!(
                                        "class '{}' vtable has no slot {} inherited from class {}",
                                        module.classes[sc].name, slot, c.name
                                    ),
                                ),
                            }
                        }
                        check_dispatch_targets(
                            &targets,
                            arity + 1,
                            module,
                            &mut errors,
                            &name,
                            instr.offset,
                        );
                    }
                    None => operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("class id {} out of range", class),
                    ),
                }
            }
            CallInterface => {
                let iface = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                let arity = instr.args[2] as i32;
                match module.interfaces.get(iface) {
                    Some(i) if slot < i.slots.len() => {
                        let mut targets: Vec<u32> = vec![];
                        if let Some(d) = i.defaults.get(slot).copied().flatten() {
                            targets.push(d);
                        }
                        for &ci in &impls_of[iface] {
                            let fids: &[u32] = module.classes[ci]
                                .interfaces
                                .iter()
                                .find(|(iid, _)| *iid == iface as u32)
                                .map(|(_, fids)| fids.as_slice())
                                .unwrap_or(&[]);
                            match fids.get(slot) {
                                Some(&fid) => targets.push(fid),
                                None => operand_error(
                                    &mut errors,
                                    &name,
                                    instr.offset,
                                    "V011",
                                    format!(
                                        "class '{}' interface table has no slot {} of interface {}",
                                        module.classes[ci].name, slot, i.name
                                    ),
                                ),
                            }
                        }
                        check_dispatch_targets(
                            &targets,
                            arity + 1,
                            module,
                            &mut errors,
                            &name,
                            instr.offset,
                        );
                    }
                    Some(_) => operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("interface slot {} out of range", slot),
                    ),
                    None => operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("interface id {} out of range", iface),
                    ),
                }
            }
            CallSuper => {
                let class = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                let arity = instr.args[2] as i32;
                match module.classes.get(class) {
                    Some(c) => match c.vtable.get(slot) {
                        Some(&fid) => {
                            check_dispatch_targets(
                                &[fid],
                                arity + 1,
                                module,
                                &mut errors,
                                &name,
                                instr.offset,
                            );
                        }
                        None => operand_error(
                            &mut errors,
                            &name,
                            instr.offset,
                            "V002",
                            format!("vtable slot {} out of range in class {}", slot, class),
                        ),
                    },
                    None => operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("class id {} out of range", class),
                    ),
                }
            }
            CallDynamic => {
                if instr.args[0] as usize >= module.dyn_names.len() {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("dynamic name id {} out of range", instr.args[0]),
                    );
                }
            }
            CallNative => {
                if !builtins::native_known(instr.args[0] as u16) {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("native id {} is not implemented", instr.args[0]),
                    );
                } else if let Some((min_args, max_args)) =
                    builtins::native_arity(instr.args[0] as u16)
                {
                    let actual = instr.args[1] as usize;
                    if actual < min_args || actual > max_args {
                        operand_error(
                            &mut errors,
                            &name,
                            instr.offset,
                            "V002",
                            format!(
                                "native id {} passes {} explicit args, expected {}..{}",
                                instr.args[0], actual, min_args, max_args
                            ),
                        );
                    }
                }
            }
            NewObject => {
                let class = instr.args[0] as usize;
                if class >= module.classes.len() {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!("class id {} out of range", class),
                    );
                } else if instr.args[1] as usize != module.classes[class].field_count as usize {
                    operand_error(
                        &mut errors,
                        &name,
                        instr.offset,
                        "V002",
                        format!(
                            "NewObject field count {} does not match class '{}' field count {}",
                            instr.args[1],
                            module.classes[class].name,
                            module.classes[class].field_count
                        ),
                    );
                }
            }
            _ => {}
        }
    }

    if n == 0 {
        if f.returns_value {
            errors.push(FnError {
                code: "V007",
                offset: None,
                msg: format!("function '{}' is empty and never returns a value", name),
            });
        }
        return (finish_errors(errors), max_h);
    }

    // ---- Basic-block construction ------------------------------------
    let targets: HashSet<u32> = instrs
        .iter()
        .flat_map(|i| match i.op {
            IrOp::Jump | IrOp::JumpIfFalse | IrOp::JumpIfTrue => Some(vec![i.args[0]]),
            IrOp::TryBegin => Some(i.args.to_vec()),
            _ => None,
        })
        .flatten()
        .collect();
    let mut is_block_start = vec![false; n];
    is_block_start[0] = true;
    for i in 1..n {
        if targets.contains(&instrs[i].offset) {
            is_block_start[i] = true;
        }
        if matches!(
            instrs[i - 1].op,
            IrOp::Jump | IrOp::Return | IrOp::ReturnVoid | IrOp::Throw
        ) {
            is_block_start[i] = true;
        }
    }
    let mut blocks: Vec<(usize, usize)> = Vec::new();
    let mut bstart = 0;
    for (i, &is_start) in is_block_start.iter().enumerate().skip(1) {
        if is_start {
            blocks.push((bstart, i));
            bstart = i;
        }
    }
    blocks.push((bstart, n));
    let nb = blocks.len();
    // Map byte offset -> block index (for jump/handler targets).
    let mut offset_block: HashMap<u32, usize> = HashMap::new();
    for (b, &(s_, _)) in blocks.iter().enumerate() {
        offset_block.insert(instrs[s_].offset, b);
    }
    // Functions without try opcodes have a height-only abstract state; a
    // cheaper driver (one state per block, no hash sets) covers them.
    let has_try = instrs.iter().any(|i| {
        matches!(
            i.op,
            IrOp::TryBegin | IrOp::TryEnd | IrOp::FinallyEnd | IrOp::FinallyDivert
        )
    });
    let height_cap = n as u32 + 1;
    let mut pending_fe: Vec<(usize, u32, usize)> = vec![];
    let mut exit: Option<(u32, u32)> = None;
    // Canonical (height, region stack) per block; the diverted flag may vary
    // between incoming states of one block. For try-free functions the region
    // stack is always empty.
    let mut block_state: Vec<Option<(u32, Vec<RegionSlot>)>> = vec![None; nb];

    if !has_try {
        // ---- Height-only exact propagation ----------------------------
        block_state[0] = Some((0, vec![]));
        let mut queued: Vec<bool> = vec![false; nb];
        queued[0] = true;
        let mut queue: VecDeque<usize> = VecDeque::new();
        queue.push_back(0);
        while let Some(b) = queue.pop_front() {
            let h = block_state[b].as_ref().unwrap().0;
            let res = simulate_block(
                &name,
                f.returns_value,
                module,
                &instrs,
                &blocks,
                &offset_block,
                &[],
                &block_state,
                b,
                h,
                &[],
                false,
                height_cap,
            );
            max_h = max_h.max(res.max_h);
            errors.extend(res.errors);
            if let Some(e) = res.exit {
                exit = Some(e);
            }
            for e in res.edges {
                let target_off = instrs[blocks[e.block].0].offset;
                match block_state[e.block] {
                    None => {
                        block_state[e.block] = Some((e.height, vec![]));
                        if !queued[e.block] {
                            queued[e.block] = true;
                            queue.push_back(e.block);
                        }
                    }
                    Some((h2, _)) if h2 != e.height => {
                        errors.push(FnError {
                            code: "V012",
                            offset: Some(target_off),
                            msg: format!(
                                "function '{}' inconsistent join at offset {}: incoming height {} from offset {} vs established height {}",
                                name, target_off, e.height, e.src, h2
                            ),
                        });
                    }
                    _ => {}
                }
            }
        }
    } else {
        // Map byte offset -> instruction index.
        let offset_idx: HashMap<u32, usize> = instrs
            .iter()
            .enumerate()
            .map(|(i, ins)| (ins.offset, i))
            .collect();

        // For each FinallyEnd, the innermost finally entry it belongs to:
        // the nearest TryBegin finally-target at or before it (finally
        // bodies are lexically nested and end with their FinallyEnd).
        let fin_entries: Vec<usize> = instrs
            .iter()
            .filter(|i| i.op == IrOp::TryBegin && i.args[1] != 0)
            .filter_map(|i| offset_idx.get(&i.args[1]).copied())
            .collect();
        let mut fe_entry: Vec<Option<usize>> = vec![None; n];
        for (i, ins) in instrs.iter().enumerate() {
            if ins.op == IrOp::FinallyEnd {
                fe_entry[i] = fin_entries.iter().copied().filter(|&e| e <= i).max();
            }
        }

        // ---- Exact state propagation (worklist) ------------------------
        let seed = StateKey {
            block: 0,
            height: 0,
            regions: vec![],
            diverted: false,
        };
        block_state[0] = Some((0, vec![]));
        let mut visited: HashSet<StateKey> = HashSet::new();
        visited.insert(seed.clone());
        let mut queue: VecDeque<StateKey> = VecDeque::new();
        queue.push_back(seed);
        // Termination: each distinct state is processed once; heights are
        // capped at n+1 and valid programs reach each block with a single
        // state, so the budget below is generous while still bounding
        // adversarial graphs.
        let budget = 8 * nb + 32;
        let mut processed = 0usize;
        while let Some(key) = queue.pop_front() {
            processed += 1;
            if processed > budget {
                errors.push(FnError {
                    code: "V014",
                    offset: None,
                    msg: format!(
                        "function '{}' exceeds the analysis state budget ({})",
                        name, budget
                    ),
                });
                break;
            }
            let res = simulate_block(
                &name,
                f.returns_value,
                module,
                &instrs,
                &blocks,
                &offset_block,
                &fe_entry,
                &block_state,
                key.block,
                key.height,
                &key.regions,
                key.diverted,
                height_cap,
            );
            max_h = max_h.max(res.max_h);
            errors.extend(res.errors);
            pending_fe.extend(res.pending_fe);
            if let Some(e) = res.exit {
                exit = Some(e);
            }
            for e in res.edges {
                let target_off = instrs[blocks[e.block].0].offset;
                match &block_state[e.block] {
                    None => {
                        block_state[e.block] = Some((e.height, e.regions.clone()));
                        let k = StateKey {
                            block: e.block,
                            height: e.height,
                            regions: e.regions,
                            diverted: e.diverted,
                        };
                        if visited.insert(k.clone()) {
                            queue.push_back(k);
                        }
                    }
                    Some((h2, r2)) if *h2 != e.height || *r2 != e.regions => {
                        let region_note = if r2.len() != e.regions.len() {
                            format!(
                                ", region depth {} vs established {}",
                                e.regions.len(),
                                r2.len()
                            )
                        } else {
                            String::new()
                        };
                        errors.push(FnError {
                            code: "V012",
                            offset: Some(target_off),
                            msg: format!(
                                "function '{}' inconsistent join at offset {}: incoming height {} from offset {} vs established height {}{}",
                                name, target_off, e.height, e.src, h2, region_note
                            ),
                        });
                    }
                    Some(_) => {
                        let k = StateKey {
                            block: e.block,
                            height: e.height,
                            regions: e.regions,
                            diverted: e.diverted,
                        };
                        if visited.insert(k.clone()) {
                            queue.push_back(k);
                        }
                    }
                }
            }
        }
    }

    // Resolve deferred FinallyEnd height checks against converged heights.
    for (fe_idx, h_fe, hb) in pending_fe {
        if let Some((entry_h, _)) = &block_state[hb] {
            if *entry_h != h_fe {
                errors.push(FnError {
                    code: "V013",
                    offset: Some(instrs[fe_idx].offset),
                    msg: format!(
                        "function '{}' finally body ending at offset {} leaves stack height {} (expected {}, the region-entry height)",
                        name, instrs[fe_idx].offset, h_fe, entry_h
                    ),
                });
            }
        }
    }
    // Unreachable blocks.
    for b in 1..nb {
        if block_state[b].is_none() {
            errors.push(FnError {
                code: "V003",
                offset: Some(instrs[blocks[b].0].offset),
                msg: format!(
                    "function '{}' has unreachable code at offset {}",
                    name, instrs[blocks[b].0].offset
                ),
            });
        }
    }
    // Implicit return at function end: void functions must exit empty;
    // value-returning functions must not fall off the end.
    if let Some((h, _)) = exit {
        if f.returns_value {
            errors.push(FnError {
                code: "V007",
                offset: None,
                msg: format!("function '{}' falls off the end without returning", name),
            });
        } else if h != 0 {
            errors.push(FnError {
                code: "V008",
                offset: None,
                msg: format!("function '{}' exits with stack height {}", name, h),
            });
        }
    }
    (finish_errors(errors), max_h)
}

/// Sort, deduplicate, and cap diagnostics so reporting is deterministic and
/// bounded on malicious graphs.
fn finish_errors(mut errors: Vec<FnError>) -> Vec<FnError> {
    errors.sort_by(|a, b| (a.offset, a.code, &a.msg).cmp(&(b.offset, b.code, &b.msg)));
    errors.dedup();
    errors.truncate(100);
    errors
}

/// Every possible dispatch target of one call must agree on parameter count
/// (including the receiver for virtual/interface/super calls) and return
/// shape, otherwise the caller's stack effect is ill-defined.
fn check_dispatch_targets(
    targets: &[u32],
    expected_params: i32,
    module: &CodeModule,
    errors: &mut Vec<FnError>,
    fname: &str,
    offset: u32,
) {
    let mut rets: Option<bool> = None;
    for &fid in targets {
        let f = match module.functions.get(fid as usize) {
            Some(f) => f,
            None => {
                errors.push(FnError {
                    code: "V011",
                    offset: Some(offset),
                    msg: format!(
                        "function '{}' at offset {}: dispatch target function id {} out of range",
                        fname, offset, fid
                    ),
                });
                continue;
            }
        };
        if f.params.len() as i32 != expected_params {
            errors.push(FnError {
                code: "V011",
                offset: Some(offset),
                msg: format!(
                    "function '{}' at offset {}: dispatch target '{}' takes {} params, call passes {}",
                    fname, offset, f.name, f.params.len(), expected_params
                ),
            });
        }
        match rets {
            None => rets = Some(f.returns_value),
            Some(r) if r != f.returns_value => {
                errors.push(FnError {
                    code: "V011",
                    offset: Some(offset),
                    msg: format!(
                        "function '{}' at offset {}: dispatch targets disagree on return shape at '{}'",
                        fname, offset, f.name
                    ),
                });
            }
            _ => {}
        }
    }
}

/// Verify a whole module; returns true when no errors were added.
pub fn verify(module: &CodeModule, diags: &mut Diagnostics) -> bool {
    verify_impl(module, diags, None)
}

/// Verify a whole module and, when verification succeeds, fill each
/// function's `max_stack` with the exact maximum operand-stack depth
/// computed by the same analysis as verification (see `docs/VERIFIER.md`).
/// On failure the fields are left untouched.
pub fn verify_with_max_stacks(module: &mut CodeModule, diags: &mut Diagnostics) -> bool {
    let mut max_stacks = vec![0u32; module.functions.len()];
    let ok = verify_impl(module, diags, Some(&mut max_stacks));
    if ok {
        for (f, m) in module.functions.iter_mut().zip(max_stacks) {
            f.max_stack = m.min(u16::MAX as u32) as u16;
        }
    }
    ok
}

fn verify_impl(
    module: &CodeModule,
    diags: &mut Diagnostics,
    max_out: Option<&mut Vec<u32>>,
) -> bool {
    let mut errors: Vec<FnError> = vec![];
    if let Some(entry) = module.entry {
        if entry as usize >= module.functions.len() {
            errors.push(FnError {
                code: "V010",
                offset: None,
                msg: "entry point function id out of range".into(),
            });
        } else if module.functions[entry as usize].params.len() != 1 {
            errors.push(FnError {
                code: "V011",
                offset: None,
                msg: format!(
                    "entry point '{}' must take exactly 1 parameter (the args list)",
                    module.functions[entry as usize].name
                ),
            });
        }
    }
    // Class hierarchy: parent ranges, cycles, and reverse edges.
    let nclass = module.classes.len();
    let mut children: Vec<Vec<usize>> = vec![Vec::new(); nclass];
    for (i, class) in module.classes.iter().enumerate() {
        if let Some(parent) = class.parent {
            if parent as usize >= nclass {
                errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("class {} parent id out of range", i),
                });
            } else {
                children[parent as usize].push(i);
            }
        }
        for fid in class
            .vtable
            .iter()
            .chain(class.statics.iter().map(|(_, fid)| fid))
        {
            if *fid as usize >= module.functions.len() {
                errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("class {} function id {} out of range", i, fid),
                });
            }
        }
        for (iid, fids) in &class.interfaces {
            match module.interfaces.get(*iid as usize) {
                Some(iface) if fids.len() == iface.slots.len() => {}
                Some(_) => errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("class {} interface {} dispatch length mismatch", i, iid),
                }),
                None => errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("class {} interface id {} out of range", i, iid),
                }),
            }
            for fid in fids {
                if *fid as usize >= module.functions.len() {
                    errors.push(FnError {
                        code: "V011",
                        offset: None,
                        msg: format!("class {} interface function id {} out of range", i, fid),
                    });
                }
            }
        }
    }
    // Cycle detection over parent pointers (runtime traversal assumes an
    // acyclic hierarchy).
    let mut state = vec![0u8; nclass]; // 0 unvisited, 1 on path, 2 done
    for root in 0..nclass {
        if state[root] != 0 {
            continue;
        }
        let mut path: Vec<usize> = vec![];
        let mut cur: Option<usize> = Some(root);
        while let Some(c) = cur {
            if state[c] == 1 {
                errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("class hierarchy cycle involving class {}", c),
                });
                break;
            }
            if state[c] == 2 {
                break;
            }
            state[c] = 1;
            path.push(c);
            cur = module.classes[c]
                .parent
                .map(|p| p as usize)
                .filter(|&p| p < nclass);
        }
        for c in path {
            state[c] = 2;
        }
    }
    // Transitive subclasses per class (for virtual dispatch consistency).
    let mut subclass_of: Vec<Vec<usize>> = vec![Vec::new(); nclass];
    for root in 0..nclass {
        let mut seen: HashSet<usize> = HashSet::new();
        let mut stack: Vec<usize> = children[root].clone();
        while let Some(c) = stack.pop() {
            if seen.insert(c) {
                subclass_of[root].push(c);
                stack.extend_from_slice(&children[c]);
            }
        }
        subclass_of[root].sort_unstable();
    }
    // Implementing classes per interface (for interface dispatch consistency).
    let niface = module.interfaces.len();
    let mut impls_of: Vec<Vec<usize>> = vec![Vec::new(); niface];
    for (ci, c) in module.classes.iter().enumerate() {
        for (iid, _) in &c.interfaces {
            if (*iid as usize) < niface {
                impls_of[*iid as usize].push(ci);
            }
        }
    }
    for list in impls_of.iter_mut() {
        list.sort_unstable();
    }
    for (i, iface) in module.interfaces.iter().enumerate() {
        if iface.defaults.len() != iface.slots.len() {
            errors.push(FnError {
                code: "V011",
                offset: None,
                msg: format!("interface {} default length mismatch", i),
            });
        }
        for fid in iface.defaults.iter().flatten() {
            if *fid as usize >= module.functions.len() {
                errors.push(FnError {
                    code: "V011",
                    offset: None,
                    msg: format!("interface {} default function id {} out of range", i, fid),
                });
            }
        }
    }
    for (i, function) in module.functions.iter().enumerate() {
        if function.params.len() > function.local_count as usize {
            errors.push(FnError {
                code: "V011",
                offset: None,
                msg: format!("function {} has more parameters than locals", i),
            });
        }
        if function.source_file as usize >= module.sources.len() && !module.sources.is_empty() {
            errors.push(FnError {
                code: "V011",
                offset: None,
                msg: format!("function {} source file id out of range", i),
            });
        }
    }
    let mut max_stacks = vec![0u32; module.functions.len()];
    for ((i, _), slot) in module
        .functions
        .iter()
        .enumerate()
        .zip(max_stacks.iter_mut())
    {
        let (ferrors, max_h) = verify_function(module, i, &subclass_of, &impls_of);
        *slot = max_h;
        errors.extend(ferrors);
    }
    let mut all = finish_errors(errors);
    let ok = all.is_empty();
    if ok {
        if let Some(out) = max_out {
            *out = max_stacks;
        }
    }
    for e in all.drain(..) {
        diags.err(e.code, e.msg);
    }
    ok
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::bytecode::{ClassMeta, CodeFunction, CodeModule};

    /// Encode one instruction (opcode + operands) to bytes.
    fn enc(op: IrOp, args: &[u32]) -> Vec<u8> {
        let mut v = vec![op.code()];
        for (i, a) in args.iter().enumerate().take(op.operand_count()) {
            let size = op.operand_size(i);
            v.extend_from_slice(&a.to_le_bytes()[..size]);
        }
        v
    }

    /// Byte offset of each instruction in a list of encoded instructions.
    fn offs(instrs: &[Vec<u8>]) -> Vec<usize> {
        let mut o = vec![0usize];
        for i in 1..instrs.len() {
            o.push(o[i - 1] + instrs[i - 1].len());
        }
        o
    }

    fn module_fns(functions: Vec<CodeFunction>, classes: Vec<ClassMeta>) -> CodeModule {
        CodeModule {
            version: CodeModule::FORMAT_VERSION,
            constants: vec![],
            functions,
            classes,
            interfaces: vec![],
            dyn_names: vec![],
            entry: None,
            sources: vec![],
        }
    }

    fn module(code: Vec<u8>, returns_value: bool, classes: Vec<ClassMeta>) -> CodeModule {
        module_fns(
            vec![CodeFunction {
                name: "test".into(),
                params: vec![],
                local_count: 0,
                max_stack: 0,
                returns_value,
                code,
                line_map: vec![],
                source_file: 0,
            }],
            classes,
        )
    }

    fn class(name: &str, parent: Option<u32>, field_count: u16, vtable: Vec<u32>) -> ClassMeta {
        ClassMeta {
            name: name.into(),
            parent,
            field_count,
            vtable_names: vec![],
            vtable,
            statics: vec![],
            interfaces: vec![],
        }
    }

    fn verify_ok(m: &CodeModule) -> bool {
        let mut diags = Diagnostics::default();
        verify(m, &mut diags)
    }

    fn verify_has_code(m: &CodeModule, code: &str) -> bool {
        let mut diags = Diagnostics::default();
        let ok = verify(m, &mut diags);
        !ok && diags.items.iter().any(|d| d.code == code)
    }

    #[test]
    fn rejects_unknown_native_ids() {
        let code = vec![
            IrOp::CallNative.code(),
            0xe7,
            0x03,
            0,
            0,
            IrOp::ReturnVoid.code(),
        ];
        let mut diags = Diagnostics::default();
        assert!(!verify(&module(code, false, vec![]), &mut diags));
        assert!(diags.items.iter().any(|d| d.code == "V002"));
    }

    #[test]
    fn stack_pop_and_peek_replace_the_receiver_with_a_value() {
        for op in [IrOp::StackPop, IrOp::StackPeek] {
            let m = module(
                vec![IrOp::NewStack.code(), op.code(), IrOp::Return.code()],
                true,
                vec![],
            );
            let mut diags = Diagnostics::default();
            assert!(verify(&m, &mut diags), "{op:?}: {:?}", diags.items);
        }
    }

    #[test]
    fn rejects_missing_operands_even_when_net_height_is_nonnegative() {
        for op in [IrOp::Dup, IrOp::NullCheck, IrOp::NegLong, IrOp::AddLong] {
            let code = if op == IrOp::AddLong {
                vec![
                    IrOp::LoadConst.code(),
                    0,
                    0,
                    0,
                    0,
                    op.code(),
                    IrOp::ReturnVoid.code(),
                ]
            } else {
                vec![op.code(), IrOp::ReturnVoid.code()]
            };
            let mut m = module(code, false, vec![]);
            m.constants.push(crate::bytecode::ConstVal::Long(1));
            let mut diags = Diagnostics::default();
            assert!(!verify(&m, &mut diags), "accepted {op:?}");
            assert!(diags.items.iter().any(|d| d.code == "V004"));
        }
    }

    #[test]
    fn rejects_wrong_native_arities() {
        let code = vec![
            IrOp::LoadConst.code(),
            0,
            0,
            0,
            0,
            IrOp::CallNative.code(),
            crate::stdlib::builtins::nat::STR_LEN as u8,
            0,
            1,
            0,
            IrOp::Return.code(),
        ];
        let mut m = module(code, true, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Str("x".into()));
        let mut diags = Diagnostics::default();
        assert!(!verify(&m, &mut diags));
        assert!(diags.items.iter().any(|d| d.code == "V002"));
    }

    #[test]
    fn rejects_invalid_construction_target_class() {
        let mut code = vec![IrOp::CallStatic.code()];
        code.extend_from_slice(&0u32.to_le_bytes());
        code.extend_from_slice(&0u16.to_le_bytes());
        code.extend_from_slice(&0u16.to_le_bytes());
        code.push(IrOp::ReturnVoid.code());
        let mut diags = Diagnostics::default();
        assert!(!verify(&module(code, false, vec![]), &mut diags));
        assert!(diags
            .items
            .iter()
            .any(|d| d.message.contains("target class")));
    }

    #[test]
    fn rejects_return_opcode_mismatches() {
        let mut diags = Diagnostics::default();
        assert!(!verify(
            &module(vec![IrOp::ReturnVoid.code()], true, vec![]),
            &mut diags
        ));
        assert!(diags.items.iter().any(|d| d.code == "V005"));
    }

    #[test]
    fn rejects_throw_fallthrough_as_unreachable() {
        // Throw is a terminator: code after it in the same straight-line
        // stretch is unreachable unless jumped to.
        let mut m = module(
            vec![
                IrOp::LoadConst.code(),
                0,
                0,
                0,
                0,
                IrOp::Throw.code(),
                IrOp::LoadConst.code(),
                0,
                0,
                0,
                0,
                IrOp::ReturnVoid.code(),
            ],
            false,
            vec![],
        );
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V003"));
    }

    #[test]
    fn accepts_match_style_throw_arm_join() {
        // The case-61 shape: both live arms jump to the merge point with
        // equal heights; the throw arm terminates.
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::Jump, &[0]),
            enc(IrOp::LoadConst, &[0]),
            vec![IrOp::Throw.code()],
            enc(IrOp::LoadConst, &[0]),
            vec![IrOp::Return.code()],
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[3] as u32]);
        ins[2] = enc(IrOp::Jump, &[o[5] as u32]);
        let mut m = module(ins.concat(), true, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_ok(&m), "match-style throw arm rejected");
    }

    #[test]
    fn rejects_diamond_join_height_mismatch() {
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::Jump, &[0]),
            enc(IrOp::Jump, &[0]),
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[4] as u32]);
        ins[3] = enc(IrOp::Jump, &[o[5] as u32]);
        ins[4] = enc(IrOp::Jump, &[o[5] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V012"));
    }

    #[test]
    fn accepts_balanced_loop() {
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::Jump, &[0]),
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[3] as u32]);
        ins[2] = enc(IrOp::Jump, &[o[0] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Bool(true));
        assert!(verify_ok(&m), "balanced loop rejected");
    }

    #[test]
    fn rejects_stack_growing_loop() {
        let mut ins = vec![enc(IrOp::LoadConst, &[0]), enc(IrOp::Jump, &[0])];
        let o = offs(&ins);
        ins[1] = enc(IrOp::Jump, &[o[0] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V012"));
    }

    #[test]
    fn rejects_try_end_without_region() {
        let m = module(
            vec![IrOp::TryEnd.code(), IrOp::ReturnVoid.code()],
            false,
            vec![],
        );
        assert!(verify_has_code(&m, "V013"));
    }

    #[test]
    fn rejects_finally_end_without_region() {
        let m = module(
            vec![IrOp::FinallyEnd.code(), IrOp::ReturnVoid.code()],
            false,
            vec![],
        );
        assert!(verify_has_code(&m, "V013"));
    }

    #[test]
    fn rejects_finally_body_leaving_extra_stack() {
        // Region base is 0; the finally body must restore height 0 at
        // FinallyEnd but leaves one extra value.
        let mut ins = vec![
            enc(IrOp::TryBegin, &[0, 0]),
            vec![IrOp::TryEnd.code()],
            enc(IrOp::Jump, &[0]),
            enc(IrOp::LoadConst, &[0]),
            vec![IrOp::FinallyEnd.code()],
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[0] = enc(IrOp::TryBegin, &[0, o[3] as u32]);
        ins[2] = enc(IrOp::Jump, &[o[3] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V013"));
    }

    #[test]
    fn accepts_try_catch_finally_shape() {
        // Full compiler layout: body region with catch+finally, catch-body
        // pure-finally region, shared finally with FinallyEnd.
        let mut ins = vec![
            enc(IrOp::TryBegin, &[0, 0]),  // 0
            enc(IrOp::LoadConst, &[0]),    // 1
            vec![IrOp::Dup.code()],        // 2
            enc(IrOp::JumpIfFalse, &[0]),  // 3
            vec![IrOp::Throw.code()],      // 4
            vec![IrOp::Pop.code()],        // 5
            vec![IrOp::TryEnd.code()],     // 6
            enc(IrOp::Jump, &[0]),         // 7 -> finally
            enc(IrOp::StoreLocal, &[0]),   // 8 catch: consume exception
            enc(IrOp::TryBegin, &[0, 0]),  // 9 catch-body region
            vec![IrOp::ReturnVoid.code()], // 10
            vec![IrOp::FinallyEnd.code()], // 11 finally
            vec![IrOp::ReturnVoid.code()], // 12
        ];
        let o = offs(&ins);
        ins[0] = enc(IrOp::TryBegin, &[o[8] as u32, o[11] as u32]);
        ins[3] = enc(IrOp::JumpIfFalse, &[o[5] as u32]);
        ins[7] = enc(IrOp::Jump, &[o[11] as u32]);
        ins[9] = enc(IrOp::TryBegin, &[o[11] as u32, o[11] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        m.functions[0].local_count = 1;
        assert!(verify_ok(&m), "try/catch/finally shape rejected");
    }

    #[test]
    fn accepts_break_divert_with_finally() {
        // Loop with a try/finally body that breaks: the resume point joins
        // the loop-back at the same state.
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),       // 0 condition
            enc(IrOp::JumpIfFalse, &[0]),     // 1 -> exit
            enc(IrOp::TryBegin, &[0, 0]),     // 2
            vec![IrOp::FinallyDivert.code()], // 3 break
            enc(IrOp::Jump, &[0]),            // 4 -> exit
            vec![IrOp::FinallyEnd.code()],    // 5
            enc(IrOp::Jump, &[0]),            // 6 -> loop head
            vec![IrOp::ReturnVoid.code()],    // 7 exit
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[7] as u32]);
        ins[2] = enc(IrOp::TryBegin, &[0, o[5] as u32]);
        ins[4] = enc(IrOp::Jump, &[o[7] as u32]);
        ins[6] = enc(IrOp::Jump, &[o[0] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Bool(true));
        assert!(verify_ok(&m), "break/finally loop rejected");
    }

    #[test]
    fn rejects_jump_bypassing_region_setup() {
        // One path reaches the merge with an active region, the other does
        // not: the region depth mismatch must be rejected.
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::TryBegin, &[0, 0]),
            enc(IrOp::Jump, &[0]),
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[4] as u32]);
        ins[3] = enc(IrOp::Jump, &[o[4] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V012"));
    }

    #[test]
    fn rejects_list_spread() {
        let m = module(
            vec![IrOp::ListSpread.code(), IrOp::ReturnVoid.code()],
            false,
            vec![],
        );
        assert!(verify_has_code(&m, "V015"));
    }

    #[test]
    fn rejects_virtual_dispatch_return_shape_mismatch() {
        // Subclass overrides a value-returning slot with a void method.
        let fns = vec![
            CodeFunction {
                name: "A.m".into(),
                params: vec!["self".into()],
                local_count: 1,
                max_stack: 0,
                returns_value: true,
                code: vec![IrOp::LoadConst.code(), 0, 0, 0, 0, IrOp::Return.code()],
                line_map: vec![],
                source_file: 0,
            },
            CodeFunction {
                name: "B.m".into(),
                params: vec!["self".into()],
                local_count: 1,
                max_stack: 0,
                returns_value: false,
                code: vec![IrOp::ReturnVoid.code()],
                line_map: vec![],
                source_file: 0,
            },
            CodeFunction {
                name: "call".into(),
                params: vec![],
                local_count: 0,
                max_stack: 0,
                returns_value: false,
                code: vec![
                    IrOp::LoadConst.code(),
                    0,
                    0,
                    0,
                    0,
                    IrOp::CallVirtual.code(),
                    0,
                    0, // class 0
                    0,
                    0, // slot 0
                    0,
                    0, // arity 0
                    IrOp::ReturnVoid.code(),
                ],
                line_map: vec![],
                source_file: 0,
            },
        ];
        let mut m = module_fns(
            fns,
            vec![
                class("A", None, 0, vec![0]),
                class("B", Some(0), 0, vec![1]),
            ],
        );
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V011"));
    }

    #[test]
    fn rejects_hierarchy_cycle() {
        let m = module(
            vec![IrOp::ReturnVoid.code()],
            false,
            vec![
                class("A", Some(1), 0, vec![]),
                class("B", Some(0), 0, vec![]),
            ],
        );
        assert!(verify_has_code(&m, "V011"));
    }

    #[test]
    fn rejects_entry_point_arity() {
        let mut m = module(vec![IrOp::Return.code()], true, vec![]);
        m.entry = Some(0);
        assert!(verify_has_code(&m, "V011"));
    }

    #[test]
    fn rejects_new_object_field_count_mismatch() {
        let code = vec![
            IrOp::NewObject.code(),
            0,
            0, // class 0
            3,
            0, // 3 fields
            IrOp::Pop.code(),
            IrOp::ReturnVoid.code(),
        ];
        let m = module(code, false, vec![class("A", None, 2, vec![])]);
        assert!(verify_has_code(&m, "V002"));
    }

    #[test]
    fn rejects_unknown_opcode() {
        let m = module(vec![0xff, IrOp::ReturnVoid.code()], false, vec![]);
        assert!(verify_has_code(&m, "V001"));
    }

    #[test]
    fn rejects_truncated_operands() {
        // LoadConst with only two of its four operand bytes.
        let m = module(vec![IrOp::LoadConst.code(), 0, 0], false, vec![]);
        assert!(verify_has_code(&m, "V001"));
    }

    #[test]
    fn rejects_jump_into_operand_bytes() {
        // Jump target lands inside LoadConst's operand bytes.
        let mut ins = vec![
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::LoadConst, &[0]),
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[0] = enc(IrOp::JumpIfFalse, &[(o[1] + 1) as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V002"));
    }

    #[test]
    fn rejects_out_of_range_constant_and_local() {
        let mut m = module(
            vec![
                IrOp::LoadConst.code(),
                9,
                0,
                0,
                0,
                IrOp::StoreLocal.code(),
                7,
                0,
                IrOp::ReturnVoid.code(),
            ],
            false,
            vec![],
        );
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        m.functions[0].local_count = 1;
        assert!(verify_has_code(&m, "V002"));
    }

    #[test]
    fn rejects_value_function_falling_off_the_end() {
        let mut m = module(vec![IrOp::LoadConst.code(), 0, 0, 0, 0], true, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V007"));
    }

    #[test]
    fn rejects_void_function_exiting_with_leftover_stack() {
        let mut m = module(
            vec![IrOp::LoadConst.code(), 0, 0, 0, 0, IrOp::ReturnVoid.code()],
            false,
            vec![],
        );
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        assert!(verify_has_code(&m, "V005"));
    }

    #[test]
    fn max_stack_tracks_straight_line_height() {
        // Three loads, one pop, one binary op: peak operand depth is 3.
        let mut m = module(
            vec![
                IrOp::LoadConst.code(),
                0,
                0,
                0,
                0,
                IrOp::LoadConst.code(),
                0,
                0,
                0,
                0,
                IrOp::LoadConst.code(),
                0,
                0,
                0,
                0,
                IrOp::Pop.code(),
                IrOp::AddLong.code(),
                IrOp::Return.code(),
            ],
            true,
            vec![],
        );
        m.constants.push(crate::bytecode::ConstVal::Long(1));
        let mut diags = Diagnostics::default();
        assert!(
            verify_with_max_stacks(&mut m, &mut diags),
            "{:?}",
            diags.items
        );
        assert_eq!(m.functions[0].max_stack, 3);
    }

    #[test]
    fn max_stack_of_balanced_loop_is_one() {
        // LoadConst; JumpIfFalse exit; Jump top; exit: ReturnVoid
        let mut ins = vec![
            enc(IrOp::LoadConst, &[0]),
            enc(IrOp::JumpIfFalse, &[0]),
            enc(IrOp::Jump, &[0]),
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[1] = enc(IrOp::JumpIfFalse, &[o[3] as u32]);
        ins[2] = enc(IrOp::Jump, &[o[0] as u32]);
        let mut m = module(ins.concat(), false, vec![]);
        m.constants.push(crate::bytecode::ConstVal::Bool(true));
        let mut diags = Diagnostics::default();
        assert!(
            verify_with_max_stacks(&mut m, &mut diags),
            "{:?}",
            diags.items
        );
        assert_eq!(m.functions[0].max_stack, 1);
    }

    #[test]
    fn max_stack_includes_catch_entry_exception_value() {
        // TryBegin(catch); ReturnVoid; catch: Pop; ReturnVoid.
        // The catch entry receives the exception value: peak depth is 1.
        let mut ins = vec![
            enc(IrOp::TryBegin, &[0, 0]),
            vec![IrOp::ReturnVoid.code()],
            vec![IrOp::Pop.code()],
            vec![IrOp::ReturnVoid.code()],
        ];
        let o = offs(&ins);
        ins[0] = enc(IrOp::TryBegin, &[o[2] as u32, 0]);
        let mut m = module(ins.concat(), false, vec![]);
        let mut diags = Diagnostics::default();
        assert!(
            verify_with_max_stacks(&mut m, &mut diags),
            "{:?}",
            diags.items
        );
        assert_eq!(m.functions[0].max_stack, 1);
    }

    #[test]
    fn max_stack_untouched_when_verification_fails() {
        // Value-returning function with a void return: rejected.
        let mut m = module(vec![IrOp::ReturnVoid.code()], true, vec![]);
        m.functions[0].max_stack = 7;
        let mut diags = Diagnostics::default();
        assert!(!verify_with_max_stacks(&mut m, &mut diags));
        assert_eq!(m.functions[0].max_stack, 7);
    }

    #[test]
    fn malformed_code_never_panics() {
        // Deterministic pseudo-random instruction streams: verification must
        // terminate and report (accept or reject) without panicking or
        // hanging on any decodable garbage.
        let mut seed: u64 = 0x9e37_79b9_7f4a_7c15;
        let mut next = move || {
            seed = seed
                .wrapping_mul(6364136223846793005)
                .wrapping_add(1442695040888963407);
            (seed >> 33) as u8
        };
        for _ in 0..300 {
            let len = next() as usize % 80;
            let code: Vec<u8> = (0..len).map(|_| next()).collect();
            let returns_value = next() % 2 == 0;
            let m = module(code, returns_value, vec![]);
            let mut diags = Diagnostics::default();
            let _ = verify(&m, &mut diags);
        }
    }
}
