//! Bytecode verifier: validates a compiled module before execution.
//!
//! Checks performed per function:
//!   * every instruction decodes cleanly and stays in bounds
//!   * jump targets land on instruction boundaries
//!   * constant/local/function/class/interface indices are in range
//!   * call arities match the callee's parameter count
//!   * the value stack never underflows and is empty at function exit

use crate::bytecode::CodeModule;
use crate::diagnostic::Diagnostics;
use crate::ir::IrOp;
use crate::stdlib::builtins;

struct Instr {
    offset: u32,
    op: IrOp,
    /// Decoded operands (u32-normalized).
    args: Vec<u32>,
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
    let mut args = Vec::new();
    let mut p = pos1;
    while args.len() < op.operand_count() {
        let size = op.operand_size(args.len());
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
        args.push(v);
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

/// Stack effect of an instruction: Some(delta) or None for terminators.
fn stack_effect(instr: &Instr, module: &CodeModule) -> Option<i32> {
    use IrOp::*;
    let delta: i32 = match instr.op {
        LoadConst | LoadLocal | LoadGlobal => 1,
        StoreLocal | StoreGlobal | Pop | ListAdd | ListRemove | StackPush => -1,
        AddInt | SubInt | MulInt | DivInt | ModInt | AddFloat | SubFloat | MulFloat | DivFloat
        | ModFloat | And | EqInt | EqFloat | EqBool | EqChar | EqString | EqObject | EqEnum
        | EqDyn | LtInt | LeInt | GtInt | GeInt | LtFloat | LeFloat | GtFloat | GeFloat
        | LtChar | LeChar | GtChar | GeChar | LtString | LeString | GtString | GeString | LtDyn
        | LeDyn | GtDyn | GeDyn | ListGet | ListContains | ListIndexOf | ListJoin | MapGet
        | MapRemove | MapContainsKey | StackPop | StackPeek | StackGet | StrContains
        | StrStartsWith | StrEndsWith | StrSplit | StrIndex | StrCharAt | IdentityEq
        | IdentityNe | Throw => -1,
        ListSet | MapPut | StrSubstr | StrReplace => -2,
        StrConcat => -1,
        // NewEnum pops the payload (if any) and pushes the enum value.
        NewEnum => {
            if instr.args.len() >= 3 && instr.args[2] != 0 {
                0
            } else {
                1
            }
        }
        NegInt | NegFloat | Not | IsNull | NullCheck | ToInt | ToFloat | ToByte | ToBool
        | ToChar | ToStringValue | ListLen | ListReverse | ListSort | ListClear | TryEnd
        | MapLen | MapKeys | MapValues | MapClear | StackLen | StackEmpty | StrLen | StrTrim
        | StrUpper | StrLower | EnumIndex | EnumPayload | Jump | GcHint | TryBegin | FinallyEnd
        | FinallyDivert => 0,
        // Conditional jumps consume the condition.
        JumpIfFalse | JumpIfTrue => -1,
        // Spread pops the list; element count is dynamic (assume 0).
        ListSpread | ListExtend => -1,
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
        Return | ReturnVoid => return None,
    };
    Some(delta)
}

/// Map a virtual/interface/super call to its concrete function id.
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
            // The receiver decides at runtime; for verification accept the
            // interface default or any implementing class's table entry.
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

fn verify_function(module: &CodeModule, fidx: usize, diags: &mut Diagnostics) {
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
                diags.err("V001", format!("function '{}': {}", name, msg));
                return;
            }
        }
    }
    if std::env::var("SOLVIK_DEBUG_VERIFY").is_ok() {
        for i in &instrs {
            eprintln!("  VDBG {} @{} {:?} {:?}", name, i.offset, i.op, i.args);
        }
    }
    let offsets: std::collections::HashSet<u32> = instrs.iter().map(|i| i.offset).collect();

    // Validate each instruction's operands.
    for instr in &instrs {
        use IrOp::*;
        let mut bad = |msg: String| {
            diags.err(
                "V002",
                format!("function '{}' at offset {}: {}", name, instr.offset, msg),
            )
        };
        match instr.op {
            LoadConst => {
                if instr.args[0] as usize >= module.constants.len() {
                    bad(format!("constant index {} out of range", instr.args[0]));
                }
            }
            LoadLocal | StoreLocal => {
                if instr.args[0] as usize >= f.local_count as usize {
                    bad(format!(
                        "local slot {} out of range ({} locals)",
                        instr.args[0], f.local_count
                    ));
                }
            }
            LoadGlobal | StoreGlobal => {
                if instr.args[0] > 2 {
                    bad(format!("global slot {} out of range", instr.args[0]));
                }
            }
            Jump | JumpIfFalse | JumpIfTrue => {
                if !offsets.contains(&instr.args[0]) {
                    bad(format!(
                        "jump target {} is not an instruction boundary",
                        instr.args[0]
                    ));
                }
            }
            TryBegin => {
                for t in &instr.args {
                    if !offsets.contains(t) {
                        bad(format!("try target {} is not an instruction boundary", t));
                    }
                }
            }
            CallFn | CallStatic => {
                let fid = instr.args[0] as usize;
                let arity = instr.args[1];
                match module.functions.get(fid) {
                    Some(callee) => {
                        if arity as usize != callee.params.len() {
                            bad(format!(
                                "call to '{}' passes {} args, expected {}",
                                callee.name,
                                arity,
                                callee.params.len()
                            ));
                        }
                    }
                    None => bad(format!("function id {} out of range", fid)),
                }
                if instr.op == CallStatic
                    && instr.args[2] != u16::MAX as u32
                    && instr.args[2] as usize >= module.classes.len()
                {
                    bad(format!("target class id {} out of range", instr.args[2]));
                }
            }
            CallVirtual => {
                let class = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                match module.classes.get(class) {
                    Some(c) if slot < c.vtable.len() => {}
                    Some(_) => bad(format!(
                        "vtable slot {} out of range in class {}",
                        slot, class
                    )),
                    None => bad(format!("class id {} out of range", class)),
                }
            }
            CallInterface => {
                let iface = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                match module.interfaces.get(iface) {
                    Some(i) if slot < i.slots.len() => {}
                    Some(_) => bad(format!("interface slot {} out of range", slot)),
                    None => bad(format!("interface id {} out of range", iface)),
                }
            }
            CallSuper => {
                let class = instr.args[0] as usize;
                let slot = instr.args[1] as usize;
                match module.classes.get(class) {
                    Some(c) if slot < c.vtable.len() => {}
                    Some(_) => bad(format!("vtable slot {} out of range", slot)),
                    None => bad(format!("class id {} out of range", class)),
                }
            }
            CallDynamic => {
                if instr.args[0] as usize >= module.dyn_names.len() {
                    bad(format!("dynamic name id {} out of range", instr.args[0]));
                }
            }
            CallNative => {
                if !builtins::native_known(instr.args[0] as u16) {
                    bad(format!("native id {} is not implemented", instr.args[0]));
                } else if let Some((min_args, max_args)) =
                    builtins::native_arity(instr.args[0] as u16)
                {
                    let actual = instr.args[1] as usize;
                    if actual < min_args || actual > max_args {
                        bad(format!(
                            "native id {} passes {} explicit args, expected {}..{}",
                            instr.args[0], actual, min_args, max_args
                        ));
                    }
                }
            }
            NewObject if instr.args[0] as usize >= module.classes.len() => {
                bad(format!("class id {} out of range", instr.args[0]));
            }
            _ => {}
        }
    }

    // Stack simulation (per basic block; heights carry across joins as an
    // approximation).
    let targets: std::collections::HashSet<u32> = instrs
        .iter()
        .flat_map(|i| match i.op {
            IrOp::Jump | IrOp::JumpIfFalse | IrOp::JumpIfTrue => Some(vec![i.args[0]]),
            IrOp::TryBegin => Some(i.args.to_vec()),
            _ => None,
        })
        .flatten()
        .collect();
    // ---- Basic-block construction ------------------------------------
    let n = instrs.len();
    let mut is_block_start = vec![false; n];
    if n > 0 {
        is_block_start[0] = true;
    }
    for i in 1..n {
        if targets.contains(&instrs[i].offset) {
            is_block_start[i] = true;
        }
        let prev_op = instrs[i - 1].op;
        if stack_effect(&instrs[i - 1], module).is_none() || matches!(prev_op, IrOp::Jump) {
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
    if n > 0 {
        blocks.push((bstart, n));
    }
    let nb = blocks.len();
    // Map instruction index -> block index.
    let mut instr_block = vec![0usize; n];
    for (b, &(s_, e_)) in blocks.iter().enumerate() {
        for slot in instr_block.iter_mut().take(e_).skip(s_) {
            *slot = b;
        }
    }
    // Map byte offset -> block index (for jump targets).
    let mut offset_block: std::collections::HashMap<u32, usize> = std::collections::HashMap::new();
    for (b, &(s_, _)) in blocks.iter().enumerate() {
        offset_block.insert(instrs[s_].offset, b);
    }

    // ---- Fixed-point height propagation --------------------------------
    #[derive(Default)]
    struct BlockResult {
        /// (successor block, height on arrival)
        edges: Vec<(usize, i32)>,
        underflows: Vec<u32>,
        term_errors: Vec<String>,
    }

    #[allow(clippy::too_many_arguments)]
    fn sim_block(
        fname: &str,
        returns_value: bool,
        module: &CodeModule,
        instrs: &[Instr],
        blocks: &[(usize, usize)],
        _instr_block: &[usize],
        offset_block: &std::collections::HashMap<u32, usize>,
        b: usize,
        h_in: i32,
    ) -> BlockResult {
        let mut r = BlockResult::default();
        let (s_, e_) = blocks[b];
        let mut h = h_in;
        for instr in instrs.iter().take(e_).skip(s_) {
            match stack_effect(instr, module) {
                Some(d) => {
                    h += d;
                    if h < 0 {
                        r.underflows.push(instr.offset);
                        return r;
                    }
                    // Forwarding edges from control-flow instructions.
                    match instr.op {
                        IrOp::Jump => {
                            if let Some(tb) = offset_block.get(&instr.args[0]) {
                                r.edges.push((*tb, h));
                            }
                            return r; // rest of block unreachable
                        }
                        IrOp::JumpIfFalse | IrOp::JumpIfTrue => {
                            if let Some(tb) = offset_block.get(&instr.args[0]) {
                                r.edges.push((*tb, h));
                            }
                            // Fall-through continues.
                        }
                        IrOp::TryBegin => {
                            // catch_ip == finally_ip encodes "no catch";
                            // only the finally edge applies then.
                            // finally_ip == 0 encodes "no finally".
                            if instr.args[0] != instr.args[1] && instr.args[0] != 0 {
                                if let Some(tb) = offset_block.get(&instr.args[0]) {
                                    r.edges.push((*tb, h + 1)); // exception lands
                                }
                            }
                            if instr.args[1] != 0 {
                                if let Some(tb) = offset_block.get(&instr.args[1]) {
                                    r.edges.push((*tb, h));
                                }
                            }
                        }
                        _ => {}
                    }
                }
                None => {
                    // Terminator: validate the pre-terminator height.
                    if instr.op == IrOp::Return && !returns_value {
                        r.term_errors.push(format!(
                            "function '{}' returns a value but is declared void",
                            fname
                        ));
                    }
                    if instr.op == IrOp::Return && h != 1 {
                        r.term_errors.push(format!(
                            "function '{}' returns with stack height {} (expected 1)",
                            fname, h
                        ));
                    }
                    if instr.op == IrOp::ReturnVoid && returns_value {
                        r.term_errors.push(format!(
                            "function '{}' returns void but is declared to return a value",
                            fname
                        ));
                    }
                    if instr.op == IrOp::ReturnVoid && h != 0 {
                        r.term_errors.push(format!(
                            "function '{}' returns void with stack height {} (expected 0)",
                            fname, h
                        ));
                    }
                    return r;
                }
            }
        }
        // Fell off the end of the block: continue into the next block, or
        // (for the last block) report the implicit function exit height.
        if b + 1 < blocks.len() {
            r.edges.push((b + 1, h));
        } else {
            r.edges.push((usize::MAX, h));
        }
        r
    }

    let mut heights: Vec<Option<i32>> = vec![None; nb];
    if nb > 0 {
        heights[0] = Some(0);
    }
    let mut underflow_offsets: std::collections::HashSet<u32> = std::collections::HashSet::new();
    let mut term_error_set: std::collections::HashSet<String> = std::collections::HashSet::new();
    let mut last_exit_height: Option<i32> = None;
    for _ in 0..=nb + 2 {
        let mut changed = false;
        for b in 0..nb {
            let Some(h_in) = heights[b] else { continue };
            let res = sim_block(
                &name,
                f.returns_value,
                module,
                &instrs,
                &blocks,
                &instr_block,
                &offset_block,
                b,
                h_in,
            );
            for off in res.underflows {
                underflow_offsets.insert(off);
            }
            for e in res.term_errors {
                term_error_set.insert(e);
            }
            for (tb, th) in res.edges {
                if tb == usize::MAX {
                    last_exit_height = Some(th);
                    continue;
                }
                match heights[tb] {
                    None => {
                        heights[tb] = Some(th);
                        changed = true;
                    }
                    Some(cur) if th > cur => {
                        heights[tb] = Some(th);
                        changed = true;
                    }
                    _ => {}
                }
            }
        }
        if !changed {
            break;
        }
    }
    for off in &underflow_offsets {
        diags.err(
            "V004",
            format!("function '{}' stack underflow at offset {}", name, off),
        );
    }
    for e in &term_error_set {
        diags.err("V005", e.clone());
    }
    // Unreachable blocks (excluding landing pads fed only by try edges is
    // intentionally conservative: they are reported).
    for (b, &(s_, _)) in blocks.iter().enumerate() {
        if b != 0 && heights[b].is_none() {
            diags.err(
                "V003",
                format!(
                    "function '{}' has unreachable code at offset {}",
                    name, instrs[s_].offset
                ),
            );
        }
    }
    // Implicit return at function end: void functions must exit empty;
    // value-returning functions must not fall off the end.
    if let Some(h) = last_exit_height {
        if f.returns_value {
            diags.err(
                "V007",
                format!("function '{}' falls off the end without returning", name),
            );
        } else if h != 0 {
            diags.err(
                "V008",
                format!("function '{}' exits with stack height {}", name, h),
            );
        }
    }
}

/// Verify a whole module; returns true when no errors were added.
pub fn verify(module: &CodeModule, diags: &mut Diagnostics) -> bool {
    if let Some(entry) = module.entry {
        if entry as usize >= module.functions.len() {
            diags.err("V010", "entry point function id out of range");
            return false;
        }
    }
    for (i, class) in module.classes.iter().enumerate() {
        if let Some(parent) = class.parent {
            if parent as usize >= module.classes.len() {
                diags.err("V011", format!("class {} parent id out of range", i));
            }
        }
        for fid in class
            .vtable
            .iter()
            .chain(class.statics.iter().map(|(_, fid)| fid))
        {
            if *fid as usize >= module.functions.len() {
                diags.err(
                    "V011",
                    format!("class {} function id {} out of range", i, fid),
                );
            }
        }
        for (iid, fids) in &class.interfaces {
            match module.interfaces.get(*iid as usize) {
                Some(iface) if fids.len() == iface.slots.len() => {}
                Some(_) => diags.err(
                    "V011",
                    format!("class {} interface {} dispatch length mismatch", i, iid),
                ),
                None => diags.err(
                    "V011",
                    format!("class {} interface id {} out of range", i, iid),
                ),
            }
            for fid in fids {
                if *fid as usize >= module.functions.len() {
                    diags.err(
                        "V011",
                        format!("class {} interface function id {} out of range", i, fid),
                    );
                }
            }
        }
    }
    for (i, iface) in module.interfaces.iter().enumerate() {
        if iface.defaults.len() != iface.slots.len() {
            diags.err("V011", format!("interface {} default length mismatch", i));
        }
        for fid in iface.defaults.iter().flatten() {
            if *fid as usize >= module.functions.len() {
                diags.err(
                    "V011",
                    format!("interface {} default function id {} out of range", i, fid),
                );
            }
        }
    }
    for (i, function) in module.functions.iter().enumerate() {
        if function.params.len() > function.local_count as usize {
            diags.err(
                "V011",
                format!("function {} has more parameters than locals", i),
            );
        }
        if function.source_file as usize >= module.sources.len() && !module.sources.is_empty() {
            diags.err(
                "V011",
                format!("function {} source file id out of range", i),
            );
        }
    }
    for i in 0..module.functions.len() {
        verify_function(module, i, diags);
    }
    diags.is_empty()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::bytecode::{ClassMeta, CodeFunction, CodeModule};

    fn module(code: Vec<u8>, returns_value: bool, classes: Vec<ClassMeta>) -> CodeModule {
        CodeModule {
            version: CodeModule::FORMAT_VERSION,
            constants: vec![],
            functions: vec![CodeFunction {
                name: "test".into(),
                params: vec![],
                local_count: 0,
                returns_value,
                code,
                line_map: vec![],
                source_file: 0,
            }],
            classes,
            interfaces: vec![],
            dyn_names: vec![],
            entry: None,
            sources: vec![],
        }
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
}
