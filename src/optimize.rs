//! IR-level peephole optimizations.
//!
//! The checker emits resolved stack instructions; this pass
//! rewrites constant expressions into single loads and removes jumps that
//! target the next instruction. It is deliberately conservative:
//!
//! * only folds when the compile-time result exactly matches runtime
//!   semantics (checked arithmetic is left unfolded when it would overflow
//!   or divide by zero, so the runtime error still occurs);
//! * never reorders instructions or removes reachable side effects;
//! * folds only within basic blocks and removes unreachable blocks while
//!   retaining exception handlers and finally continuation instructions;
//! * `line_map` stays parallel to `instrs` (the checker emits one entry per
//!   instruction), so both are filtered together.
//!
//! Constants live in the module-level pool; folded results are interned
//! into it (duplicates reuse existing entries).

use crate::ir::{const_equal, const_hash, IrConst, IrInstr, IrModule};
use std::collections::HashMap;

/// Apply peephole optimizations to every function in the module.
pub fn optimize(ir: &mut IrModule) {
    let mut index = ConstIndex::new(&ir.constants);
    for f in ir.functions.iter_mut() {
        let mut lines: Vec<u32> = f.line_map.iter().map(|(_, l)| *l).collect();
        let mut instrs = f.instrs.clone();

        // Folding one expression can expose another expression immediately
        // after it (`2 + 3 + 4`). Iterate until the local peephole pass is at
        // a fixed point; each pass also remaps control-flow targets.
        loop {
            let (next, kept) = fold_function(&instrs, &mut ir.constants, &mut index);
            let next_lines: Vec<u32> = kept.iter().map(|&old| lines[old]).collect();
            if next == instrs {
                f.line_map = next_lines
                    .into_iter()
                    .enumerate()
                    .map(|(ip, line)| (ip as u32, line))
                    .collect();
                f.instrs = next;
                break;
            }
            instrs = next;
            lines = next_lines;
        }
    }
}

/// Constant-pool lookup used by the optimizer. The checker normally interns
/// constants already, but folded values are added after checking and should
/// have the same near-constant-time lookup behavior.
struct ConstIndex {
    by_hash: HashMap<u64, Vec<u32>>,
}

impl ConstIndex {
    fn new(constants: &[IrConst]) -> Self {
        let mut index = Self {
            by_hash: HashMap::new(),
        };
        for (i, c) in constants.iter().enumerate() {
            index
                .by_hash
                .entry(const_hash(c))
                .or_default()
                .push(i as u32);
        }
        index
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
fn fold_function(
    instrs: &[IrInstr],
    consts: &mut Vec<IrConst>,
    index: &mut ConstIndex,
) -> (Vec<IrInstr>, Vec<usize>) {
    let n = instrs.len();
    // A peephole must stay inside a basic block. Expressions can contain
    // control flow (short-circuit logic), so statement boundaries alone do
    // not establish this invariant.
    let mut entries = vec![false; n + 1];
    for ins in instrs {
        match ins {
            IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => {
                entries[*t as usize] = true;
            }
            IrInstr::TryBegin(c, f) => {
                if *c != 0 {
                    entries[*c as usize] = true;
                }
                if *f != 0 {
                    entries[*f as usize] = true;
                }
            }
            _ => {}
        }
    }
    // Decide per original index: keep as-is, replace with a folded load,
    // or drop. Decisions are local (adjacent patterns), so they do not
    // depend on index remapping.
    let mut actions = vec![Action::Keep; n];
    let mut i = 0usize;
    while i < n {
        if i + 2 < n && !entries[i + 1] && !entries[i + 2] {
            if let (IrInstr::LoadConst(a), IrInstr::LoadConst(b)) = (&instrs[i], &instrs[i + 1]) {
                if let Some(c) =
                    fold_binary(&instrs[i + 2], &consts[*a as usize], &consts[*b as usize])
                {
                    actions[i] = Action::Fold(IrInstr::LoadConst(intern(consts, index, c)));
                    actions[i + 1] = Action::Drop;
                    actions[i + 2] = Action::Drop;
                    i += 3;
                    continue;
                }
            }
        }
        if i + 1 < n && !entries[i + 1] {
            if let IrInstr::LoadConst(a) = &instrs[i] {
                if let Some(c) = fold_unary(&instrs[i + 1], &consts[*a as usize]) {
                    actions[i] = Action::Fold(IrInstr::LoadConst(intern(consts, index, c)));
                    actions[i + 1] = Action::Drop;
                    i += 2;
                    continue;
                }
            }
            // Statically known boolean branches can be reduced without
            // evaluating or reordering any user expression.
            if let IrInstr::LoadConst(c) = instrs[i] {
                if let IrConst::Bool(value) = consts[c as usize] {
                    match instrs[i + 1] {
                        IrInstr::JumpIfFalse(target) => {
                            actions[i] = if value {
                                Action::Drop
                            } else {
                                Action::Fold(IrInstr::Jump(target))
                            };
                            actions[i + 1] = Action::Drop;
                            i += 2;
                            continue;
                        }
                        IrInstr::JumpIfTrue(target) => {
                            actions[i] = if value {
                                Action::Fold(IrInstr::Jump(target))
                            } else {
                                Action::Drop
                            };
                            actions[i + 1] = Action::Drop;
                            i += 2;
                            continue;
                        }
                        _ => {}
                    }
                }
            }
        }
        if i + 1 < n {
            // Jump to the next instruction is a no-op, including when the
            // successor starts another basic block.
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
    // Known branches can make whole blocks unreachable. Remove those blocks
    // before verification, retaining exception landing pads via TryBegin edges.
    // FinallyDivert falls through conservatively: its resume instruction and
    // all possible finally handlers must remain available.
    let mut reachable = vec![false; n];
    let mut work = vec![0usize];
    while let Some(ip) = work.pop() {
        if ip >= n || reachable[ip] {
            continue;
        }
        reachable[ip] = true;
        let ins = match actions[ip] {
            Action::Keep => instrs[ip],
            Action::Fold(ins) => ins,
            Action::Drop => {
                work.push(ip + 1);
                continue;
            }
        };
        match ins {
            IrInstr::Jump(t) => work.push(t as usize),
            IrInstr::JumpIfTrue(t) | IrInstr::JumpIfFalse(t) => {
                work.push(t as usize);
                work.push(ip + 1);
            }
            IrInstr::TryBegin(c, f) => {
                if c != 0 {
                    work.push(c as usize);
                }
                if f != 0 {
                    work.push(f as usize);
                }
                work.push(ip + 1);
            }
            IrInstr::Return | IrInstr::ReturnVoid | IrInstr::Op(crate::ir::IrOp::Throw) => {}
            _ => work.push(ip + 1),
        }
    }
    let mut out: Vec<IrInstr> = Vec::with_capacity(n);
    let mut kept: Vec<usize> = Vec::with_capacity(n);
    for (idx, action) in actions.into_iter().enumerate() {
        if !reachable[idx] {
            continue;
        }
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
    let mut old_to_new = vec![out.len(); n + 1];
    let mut new_idx = kept.len();
    for old_idx in (0..n).rev() {
        if new_idx > 0 && kept[new_idx - 1] == old_idx {
            new_idx -= 1;
        }
        old_to_new[old_idx] = new_idx;
    }
    for ins in out.iter_mut() {
        match ins {
            IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => {
                *t = old_to_new[*t as usize] as u32;
            }
            IrInstr::TryBegin(c, f) => {
                // Zero means no handler, not an instruction index.
                if *c != 0 {
                    *c = old_to_new[*c as usize] as u32;
                }
                if *f != 0 {
                    *f = old_to_new[*f as usize] as u32;
                }
            }
            _ => {}
        }
    }
    (out, kept)
}

fn jump_target(ins: &IrInstr) -> Option<u32> {
    match ins {
        IrInstr::Jump(t) | IrInstr::JumpIfFalse(t) | IrInstr::JumpIfTrue(t) => Some(*t),
        _ => None,
    }
}

/// Add a constant to the pool, reusing an existing entry when present.
fn intern(consts: &mut Vec<IrConst>, index: &mut ConstIndex, c: IrConst) -> u32 {
    let hash = const_hash(&c);
    if let Some(candidates) = index.by_hash.get(&hash) {
        if let Some(&idx) = candidates
            .iter()
            .find(|&&idx| const_equal(&consts[idx as usize], &c))
        {
            return idx;
        }
    }
    consts.push(c);
    let index_value = (consts.len() - 1) as u32;
    index.by_hash.entry(hash).or_default().push(index_value);
    index_value
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

    #[test]
    fn preserves_entries_into_peephole_patterns() {
        for target in [2, 3] {
            let input = vec![IrInstr::JumpIfTrue(target), lc(0), lc(1), op(IrOp::AddLong)];
            let m = run_module(vec![IrConst::Long(2), IrConst::Long(3)], input.clone());
            assert_eq!(m.functions[0].instrs, input);
        }
        let input = vec![IrInstr::JumpIfTrue(2), lc(0), op(IrOp::Not)];
        let m = run_module(vec![IrConst::Bool(true)], input.clone());
        assert_eq!(m.functions[0].instrs, input);
    }

    #[test]
    fn remaps_entries_to_removed_branches_and_preserves_handler_sentinel() {
        let m = run_module(
            vec![IrConst::Bool(true)],
            vec![
                IrInstr::TryBegin(5, 0),
                IrInstr::Jump(3),
                op(IrOp::Pop),
                lc(0),
                IrInstr::JumpIfFalse(5),
                IrInstr::ReturnVoid,
            ],
        );
        assert_eq!(
            m.functions[0].instrs,
            vec![IrInstr::TryBegin(1, 0), IrInstr::ReturnVoid,]
        );
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
    fn module_interner_handles_an_inspected_pool() {
        let mut m = IrModule::new();
        m.constants.push(IrConst::Long(7));
        assert_eq!(m.intern_const(IrConst::Long(7)), 0);
        assert_eq!(m.intern_const(IrConst::Long(8)), 1);
        m.constants.clear();
        assert_eq!(m.intern_const(IrConst::Long(9)), 0);
    }

    #[test]
    fn constant_storage_preserves_signed_zero_and_nan_bits() {
        let mut m = IrModule::new();
        assert_eq!(m.intern_const(IrConst::Double(0.0)), 0);
        assert_eq!(m.intern_const(IrConst::Double(-0.0)), 1);
        let nan = f64::from_bits(0x7ff8000000000001);
        let id = m.intern_const(IrConst::Double(nan));
        assert_eq!(m.intern_const(IrConst::Double(nan)), id);
        let m = run_module(vec![IrConst::Double(0.0)], vec![lc(0), op(IrOp::NegDouble)]);
        assert_eq!(m.functions[0].instrs, vec![lc(1)]);
        let IrConst::Double(value) = m.constants[1] else {
            panic!("expected double")
        };
        assert_eq!(value.to_bits(), (-0.0f64).to_bits());
    }

    #[test]
    fn folds_chained_expressions_to_a_fixed_point() {
        let m = run_module(
            vec![IrConst::Long(2), IrConst::Long(3), IrConst::Long(4)],
            vec![lc(0), lc(1), op(IrOp::AddLong), lc(2), op(IrOp::MulLong)],
        );
        assert_eq!(m.functions[0].instrs, vec![lc(4)]);
        assert_eq!(m.constants[4], IrConst::Long(20));
    }

    #[test]
    fn simplifies_known_boolean_branches() {
        let m = run_module(
            vec![IrConst::Bool(false)],
            vec![lc(0), IrInstr::JumpIfFalse(3), op(IrOp::Pop), op(IrOp::Pop)],
        );
        assert_eq!(m.functions[0].instrs, vec![op(IrOp::Pop)]);

        let m = run_module(
            vec![IrConst::Bool(true)],
            vec![lc(0), IrInstr::JumpIfTrue(3), op(IrOp::Pop), op(IrOp::Pop)],
        );
        assert_eq!(m.functions[0].instrs, vec![op(IrOp::Pop)]);
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
        assert_eq!(m.functions[0].instrs, vec![lc(2), op(IrOp::Pop)]);
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
