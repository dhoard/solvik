//! IR-level peephole optimizations.
//!
//! The checker emits straightforward three-address-style IR; this pass
//! rewrites constant expressions into single loads and removes jumps that
//! target the next instruction. It is deliberately conservative:
//!
//! * only folds when the compile-time result exactly matches runtime
//!   semantics (checked arithmetic is left unfolded when it would overflow
//!   or divide by zero, so the runtime error still occurs);
//! * never reorders instructions or removes code with side effects;
//! * `line_map` stays parallel to `instrs` (the checker emits one entry per
//!   instruction), so both are filtered together.
//!
//! Constants live in the module-level pool; folded results are interned
//! into it (duplicates reuse existing entries).

use crate::ir::{IrConst, IrInstr, IrModule};

/// Apply peephole optimizations to every function in the module.
pub fn optimize(ir: &mut IrModule) {
    for f in ir.functions.iter_mut() {
        let lines: Vec<u32> = f.line_map.iter().map(|(_, l)| *l).collect();
        let (instrs, kept) = fold_function(&f.instrs, &mut ir.constants);
        f.line_map = kept
            .into_iter()
            .enumerate()
            .map(|(new_idx, old_idx)| (new_idx as u32, lines[old_idx]))
            .collect();
        f.instrs = instrs;
    }
}

/// Per-instruction decision made during folding.
#[derive(Clone, Copy)]
enum Action {
    Keep,
    Fold(IrInstr),
    Drop,
}

/// Fold one function's instruction list.
/// Returns the new list plus, for each surviving instruction, its index in
/// the original list (for line-map filtering).
fn fold_function(instrs: &[IrInstr], consts: &mut Vec<IrConst>) -> (Vec<IrInstr>, Vec<usize>) {
    let n = instrs.len();
    // Decide per original index: keep as-is, replace with a folded load,
    // or drop. Decisions are local (adjacent patterns), so they do not
    // depend on index remapping.
    let mut actions = vec![Action::Keep; n];
    let mut i = 0usize;
    while i < n {
        if i + 2 < n {
            if let (IrInstr::LoadConst(a), IrInstr::LoadConst(b)) = (&instrs[i], &instrs[i + 1]) {
                if let Some(c) =
                    fold_binary(&instrs[i + 2], &consts[*a as usize], &consts[*b as usize])
                {
                    actions[i] = Action::Fold(IrInstr::LoadConst(intern(consts, c)));
                    actions[i + 1] = Action::Drop;
                    actions[i + 2] = Action::Drop;
                    i += 3;
                    continue;
                }
            }
        }
        if i + 1 < n {
            if let IrInstr::LoadConst(a) = &instrs[i] {
                if let Some(c) = fold_unary(&instrs[i + 1], &consts[*a as usize]) {
                    actions[i] = Action::Fold(IrInstr::LoadConst(intern(consts, c)));
                    actions[i + 1] = Action::Drop;
                    i += 2;
                    continue;
                }
            }
            // Jump to the next instruction is a no-op.
            if let (Some(target), Action::Keep) = (jump_target(&instrs[i]), &actions[i]) {
                if target == i as u32 + 1 {
                    actions[i] = match instrs[i] {
                        IrInstr::Jump(_) => Action::Drop,
                        // A conditional jump to the next instruction still
                        // consumes its condition.
                        _ => Action::Fold(IrInstr::Op(crate::ir::IrOp::Pop)),
                    };
                    i += 1;
                    continue;
                }
            }
        }
        i += 1;
    }

    // Build the surviving list and the old-index map.
    let dropped: Vec<bool> = actions.iter().map(|a| matches!(a, Action::Drop)).collect();
    let mut out: Vec<IrInstr> = Vec::with_capacity(n);
    let mut kept: Vec<usize> = Vec::with_capacity(n);
    for (idx, action) in actions.into_iter().enumerate() {
        match action {
            Action::Keep => out.push(instrs[idx]),
            Action::Fold(repl) => out.push(repl),
            Action::Drop => {}
        }
        if !matches!(action, Action::Drop) {
            kept.push(idx);
        }
    }

    // Remap jump/handler targets through the surviving indices.
    let mut old_to_new = vec![0usize; n];
    for (new_idx, &old_idx) in kept.iter().enumerate() {
        old_to_new[old_idx] = new_idx;
    }
    for ins in out.iter_mut() {
        match ins {
            IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => {
                remap_target(t, &dropped, &old_to_new);
            }
            IrInstr::TryBegin(c, f) => {
                remap_target(c, &dropped, &old_to_new);
                remap_target(f, &dropped, &old_to_new);
            }
            _ => {}
        }
    }
    (out, kept)
}

/// Rewrite a target index through the fold mapping.
///
/// Targets may point at a kept instruction or at the first instruction of a
/// folded group (the replacement). They must never point into the middle of
/// a folded group: the checker only emits jumps to statement boundaries, so
/// this is asserted rather than handled.
fn remap_target(t: &mut u32, dropped: &[bool], old_to_new: &[usize]) {
    let old = *t as usize;
    debug_assert!(
        !dropped[old],
        "jump target {old} points into a folded group"
    );
    *t = old_to_new[old] as u32;
}

fn jump_target(ins: &IrInstr) -> Option<u32> {
    match ins {
        IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => Some(*t),
        _ => None,
    }
}

/// Add a constant to the pool, reusing an existing entry when present.
fn intern(consts: &mut Vec<IrConst>, c: IrConst) -> u32 {
    if let Some(idx) = consts.iter().position(|e| e == &c) {
        return idx as u32;
    }
    consts.push(c);
    (consts.len() - 1) as u32
}

/// Try to fold `LoadConst(a); LoadConst(b); op` into one constant.
fn fold_binary(op: &IrInstr, a: &IrConst, b: &IrConst) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::*;
    match (op, a, b) {
        // Long arithmetic: only fold when the checked operation succeeds,
        // preserving runtime overflow/divide-by-zero errors otherwise.
        (IrInstr::Op(AddLong), Long(x), Long(y)) => x.checked_add(*y).map(Long),
        (IrInstr::Op(SubLong), Long(x), Long(y)) => x.checked_sub(*y).map(Long),
        (IrInstr::Op(MulLong), Long(x), Long(y)) => x.checked_mul(*y).map(Long),
        (IrInstr::Op(DivLong), Long(x), Long(y)) => x.checked_div(*y).map(Long),
        (IrInstr::Op(ModLong), Long(x), Long(y)) => x.checked_rem(*y).map(Long),
        // Double arithmetic: the VM accepts Long operands via conversion;
        // floating point never traps, so folding always matches runtime.
        (IrInstr::Op(op @ (AddDouble | SubDouble | MulDouble | DivDouble | ModDouble)), x, y) => {
            let (x, y) = (as_double(x)?, as_double(y)?);
            Some(Double(match op {
                AddDouble => x + y,
                SubDouble => x - y,
                MulDouble => x * y,
                DivDouble => x / y,
                ModDouble => x % y,
                _ => unreachable!(),
            }))
        }
        // Equality.
        (IrInstr::Op(EqLong), Long(x), Long(y)) => Some(Bool(*x == *y)),
        (IrInstr::Op(EqDouble), x, y) => Some(Bool(as_double(x)? == as_double(y)?)),
        (IrInstr::Op(EqBool), Bool(x), Bool(y)) => Some(Bool(*x == *y)),
        (IrInstr::Op(EqChar), Char(x), Char(y)) => Some(Bool(*x == *y)),
        (IrInstr::Op(EqString), Str(x), Str(y)) => Some(Bool(x == y)),
        // Ordering.
        (IrInstr::Op(op @ (LtLong | LeLong | GtLong | GeLong)), Long(x), Long(y)) => {
            cmp_long(*op, *x, *y)
        }
        (IrInstr::Op(op @ (LtDouble | LeDouble | GtDouble | GeDouble)), x, y) => {
            cmp_double(*op, as_double(x)?, as_double(y)?)
        }
        (IrInstr::Op(op @ (LtChar | LeChar | GtChar | GeChar)), Char(x), Char(y)) => {
            cmp_char(*op, *x, *y)
        }
        (IrInstr::Op(op @ (LtString | LeString | GtString | GeString)), Str(x), Str(y)) => {
            cmp_str(*op, x, y)
        }
        // Logic.
        (IrInstr::Op(And), Bool(x), Bool(y)) => Some(Bool(*x && *y)),
        _ => None,
    }
}

/// Try to fold `LoadConst(a); op` into one constant.
fn fold_unary(op: &IrInstr, a: &IrConst) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::*;
    match (op, a) {
        (IrInstr::Op(NegLong), Long(x)) => x.checked_neg().map(Long),
        (IrInstr::Op(NegDouble), x) => as_double(x).map(|v| Double(-v)),
        (IrInstr::Op(Not), Bool(x)) => Some(Bool(!*x)),
        (IrInstr::Op(IsNull), c) => Some(Bool(matches!(c, Null))),
        _ => None,
    }
}

/// The VM's `double_of`: Double directly, Long converted.
fn as_double(c: &IrConst) -> Option<f64> {
    use IrConst::*;
    match c {
        Double(d) => Some(*d),
        Long(i) => Some(*i as f64),
        _ => None,
    }
}

fn cmp_long(op: crate::ir::IrOp, x: i64, y: i64) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::Bool;
    Some(Bool(match op {
        LtLong => x < y,
        LeLong => x <= y,
        GtLong => x > y,
        GeLong => x >= y,
        _ => unreachable!(),
    }))
}

fn cmp_double(op: crate::ir::IrOp, x: f64, y: f64) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::Bool;
    Some(Bool(match op {
        LtDouble => x < y,
        LeDouble => x <= y,
        GtDouble => x > y,
        GeDouble => x >= y,
        _ => unreachable!(),
    }))
}

fn cmp_char(op: crate::ir::IrOp, x: char, y: char) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::Bool;
    Some(Bool(match op {
        LtChar => x < y,
        LeChar => x <= y,
        GtChar => x > y,
        GeChar => x >= y,
        _ => unreachable!(),
    }))
}

fn cmp_str(op: crate::ir::IrOp, x: &str, y: &str) -> Option<IrConst> {
    use crate::ir::IrOp::*;
    use IrConst::Bool;
    Some(Bool(match op {
        LtString => x < y,
        LeString => x <= y,
        GtString => x > y,
        GeString => x >= y,
        _ => unreachable!(),
    }))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ir::{IrFunction, IrOp};

    /// Run the optimizer on a module with the given constants and function.
    fn run_module(consts: Vec<IrConst>, instrs: Vec<IrInstr>) -> IrModule {
        let line_map: Vec<(u32, u32)> = instrs
            .iter()
            .enumerate()
            .map(|(i, _)| (i as u32, 1))
            .collect();
        let mut m = IrModule::new();
        m.constants = consts;
        m.functions.push(IrFunction {
            name: "t".into(),
            params: vec![],
            param_tys: vec![],
            local_count: 0,
            returns_value: false,
            instrs,
            source_file: 0,
            line_map,
        });
        optimize(&mut m);
        m
    }

    fn lc(i: u32) -> IrInstr {
        IrInstr::LoadConst(i)
    }

    fn op(o: IrOp) -> IrInstr {
        IrInstr::Op(o)
    }

    #[test]
    fn folds_constant_binary_expressions() {
        let m = run_module(
            vec![IrConst::Long(2), IrConst::Long(3)],
            vec![lc(0), lc(1), op(IrOp::AddLong)],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(2)]);
        assert_eq!(m.constants[2], IrConst::Long(5));

        // String equality folds to a Bool constant.
        let m = run_module(
            vec![IrConst::Str("ab".into()), IrConst::Str("cd".into())],
            vec![lc(0), lc(1), op(IrOp::EqString)],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(2)]);
        assert_eq!(m.constants[2], IrConst::Bool(false));

        // Long operands fold through double ops exactly like the VM does.
        let m = run_module(
            vec![IrConst::Long(3), IrConst::Long(4)],
            vec![lc(0), lc(1), op(IrOp::AddDouble)],
        );
        assert_eq!(m.constants[2], IrConst::Double(7.0));
    }

    #[test]
    fn interns_duplicate_results() {
        // Two identical folds must reuse one pool entry.
        let m = run_module(
            vec![IrConst::Long(2), IrConst::Long(3)],
            vec![
                lc(0),
                lc(1),
                op(IrOp::AddLong),
                lc(0),
                lc(1),
                op(IrOp::AddLong),
            ],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(2), lc(2)]);
        assert_eq!(m.constants.len(), 3);
    }

    #[test]
    fn preserves_runtime_errors() {
        // Division by zero must not be folded away.
        let m = run_module(
            vec![IrConst::Long(1), IrConst::Long(0)],
            vec![lc(0), lc(1), op(IrOp::DivLong)],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(0), lc(1), op(IrOp::DivLong)]);
        assert_eq!(m.constants.len(), 2);

        // Overflowing multiplication must not be folded away.
        let big = i64::MAX;
        let m = run_module(
            vec![IrConst::Long(big), IrConst::Long(2)],
            vec![lc(0), lc(1), op(IrOp::MulLong)],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(0), lc(1), op(IrOp::MulLong)]);
    }

    #[test]
    fn folds_unary_and_null_checks() {
        let m = run_module(vec![IrConst::Bool(true)], vec![lc(0), op(IrOp::Not)]);
        assert_eq!(m.functions[0].instrs, vec![lc(1)]);
        assert_eq!(m.constants[1], IrConst::Bool(false));

        let m = run_module(vec![IrConst::Null], vec![lc(0), op(IrOp::IsNull)]);
        assert_eq!(m.constants[1], IrConst::Bool(true));

        let m = run_module(vec![IrConst::Long(5)], vec![lc(0), op(IrOp::NegLong)]);
        assert_eq!(m.constants[1], IrConst::Long(-5));
    }

    #[test]
    fn removes_jumps_to_next_instruction() {
        // Jump(2) at index 1 targets the instruction after itself: drop it.
        let m = run_module(vec![], vec![op(IrOp::Pop), IrInstr::Jump(2), op(IrOp::Pop)]);
        assert_eq!(m.functions[0].instrs, vec![op(IrOp::Pop), op(IrOp::Pop)]);

        // Conditional jump to next keeps consuming its condition.
        let m = run_module(vec![], vec![IrInstr::JumpIfFalse(1), op(IrOp::Pop)]);
        assert_eq!(m.functions[0].instrs, vec![op(IrOp::Pop), op(IrOp::Pop)]);
    }

    #[test]
    fn remaps_jump_targets_after_removals() {
        // Jump(3) points at the final Pop; folding the first three
        // instructions shifts it to index 1.
        let m = run_module(
            vec![IrConst::Long(1), IrConst::Long(2)],
            vec![
                lc(0),
                lc(1),
                op(IrOp::AddLong),
                IrInstr::Jump(5),
                op(IrOp::Pop),
                op(IrOp::Pop),
            ],
        );
        assert_eq!(
            m.functions[0].instrs,
            vec![lc(2), IrInstr::Jump(3), op(IrOp::Pop), op(IrOp::Pop)]
        );
    }

    #[test]
    fn line_map_stays_parallel_to_instrs() {
        let mut m = IrModule::new();
        m.constants = vec![IrConst::Long(1), IrConst::Long(2)];
        m.functions.push(IrFunction {
            name: "t".into(),
            params: vec![],
            param_tys: vec![],
            local_count: 0,
            returns_value: false,
            instrs: vec![lc(0), lc(1), op(IrOp::AddLong), op(IrOp::Pop)],
            source_file: 0,
            line_map: vec![(0, 10), (1, 10), (2, 11), (3, 12)],
        });
        optimize(&mut m);
        assert_eq!(m.functions[0].instrs, vec![lc(2), op(IrOp::Pop)]);
        assert_eq!(m.functions[0].line_map, vec![(0, 10), (1, 12)]);
    }
}
