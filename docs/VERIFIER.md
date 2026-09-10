# Solvik bytecode verifier: design and guarantees

This document describes the exact operand-stack verifier in `src/verifier.rs`,
the accepted bytecode contract it enforces, and its measured cost. It
replaces the earlier maximum-height approximation described in
[BYTECODE.md](../BYTECODE.md) and [docs/PERFORMANCE.md](PERFORMANCE.md).

## Root cause of the historical strict-join failure

An earlier experiment replaced maximum-height merging with strict equality
and rejected unoptimized `test/cases/61-continuation-ops` with incoming
heights 1 and 0 at the match-expression merge point. Tracing the bytecode
showed the compiler output was correct: the "no matching pattern" arm ends
in `Throw`, and the old verifier modeled `Throw` as an ordinary instruction
with net delta −1. The simulation continued past the throw and emitted a
phantom fallthrough edge into the merge block at height 0, while the two
real jump predecessors arrived at height 1. The VM never falls through
`Throw`; it unwinds. The defect was in the verifier's transfer function, not
in the compiler.

Two further modeling gaps were found while building the exact analysis:

- `FinallyEnd` falls through to the next instruction only when its finally
  block was entered by normal completion. Exceptional and diverted entries
  rethrow, complete a deferred return, or resume a break/continue instead.
- The VM's `rethrow` slot is sticky: it survives nested catches and
  suppresses the fallthrough of every subsequent `FinallyEnd` until consumed.
  A static per-instruction rule cannot capture this; it is a property of the
  execution path.

## Abstract state

The verifier simulates each function over the abstract state

```
(height: u32, regions: Vec<RegionSlot>, diverted: bool)
```

at basic-block entries, where:

- `height` is the operand-stack height above the frame's local slots;
- `regions` is the stack of active try regions (innermost last), each packed
  as `(base height at TryBegin, has_finally)`;
- `diverted` records that a non-normal transfer (passed-through exception,
  deferred return, or diverted break/continue) is pending. It is set by
  exceptional entry into a finally body and by break/continue diversion, and
  is consumed (and cleared) by the next `FinallyEnd`.

Basic blocks start at offset 0, at every jump/handler target, and after
every terminator (`Jump`, `Return`, `ReturnVoid`, `Throw`).

## Transfer function

| Instruction | Effect |
| --- | --- |
| ordinary op | require `height ≥ required inputs`; `height += net delta` |
| `Jump(t)` | edge to `t`; terminates the block |
| `JumpIfFalse/True(t)` | edge to `t` with `height−1`; fallthrough with `height−1` |
| `Return` / `ReturnVoid` | terminator; checks return shape and height 1 / 0 |
| `Throw` | terminator; handler continuations are summarized by `TryBegin` edges |
| `TryBegin(c, f)` | push region `(height, f≠0)`; catch edge to `c` with `height+1` and the pre-push regions (the unwinder pops the region); finally edge to `f` with `height`, pre-push regions, and `diverted=true` |
| `TryEnd` | pop the innermost region; error if none is active |
| `FinallyDivert` | if the innermost region has a finally: pop it and clear `diverted` (the model state is what survives at the resume point, which the finally's `FinallyEnd` re-enters); otherwise plain fallthrough |
| `FinallyEnd` | check `height == height at the finally block's entry` (the resume path continues without a stack reset); if `diverted`: no fallthrough (rethrow/return/resume); otherwise fallthrough with `diverted=false` |

The finally-entry height check uses the converged entry height of the block
holding the finally label; all entries into that block must agree (enforced
by the join rule), which is exactly the compiler invariant that every
finally entry happens at the region base.

## Join rule and termination

Propagation is a worklist. The first state to reach a block establishes the
block's canonical `(height, regions)`; any later incoming state must match
it exactly or is rejected with `V012`, reporting the target offset, both
heights, and the source instruction of the conflicting edge. The `diverted`
flag may differ between incoming states of one block (a finally continuation
can be entered normally and exceptionally); those are distinct worklist
states.

Termination follows from the finite domain: heights are capped at
`instruction count + 1` (exceeding the cap rejects with `V014`), each
distinct state is processed once, and the full-state driver additionally
bounds total processed states by `8 × blocks + 32` (exceeding the budget
rejects with `V014`). Functions without try opcodes use a height-only driver
in which each block is processed at most once, so no budget is needed. There
is no iteration cutoff that accepts an unresolved graph.

## max_stack output

`verify_with_max_stacks` records, for every function, the maximum operand
height observed at any instruction boundary — including catch-handler entry
heights (`base+1`) — into `CodeFunction::max_stack`. It is a verified upper
bound on `stack.len() − base − local_count` while that function executes:

- the VM reserves per-frame operand capacity from it (no geometric vector
growth in hot loops);
- the dispatch loop asserts the bound in debug builds on every instruction;
- the value is serialized in format v3 and must be stable across
  encode/decode round trips (pinned by `tests/invariants.rs`).

The plain `verify()` entry point performs the same analysis without filling
the field.

## Accepted bytecode contract

In addition to decoding, index ranges, jump/handler boundaries, and call
arity (all pre-existing), verification now requires:

- **Exact joins.** Every basic block receives one consistent
  `(height, regions)` pair. Stack-growing/shrinking loops, diamond joins
  with different heights, and jumps that bypass region setup are rejected.
- **Terminators.** `Throw` never falls through; code after it is reachable
  only via jumps.
- **Region discipline.** `TryEnd` requires an active region; `FinallyEnd`
  requires a matching finally region and a restored stack height; handler
  entry heights are fixed (catch: base+1, finally: base).
- **Dispatch consistency.** Every possible target of a `CallClass` or
  `CallInterface` agrees on parameter count (receiver included) and
  value/void return shape. A `CallClass` has exactly one target (the
  class's own method table); a `CallInterface` is checked against the
  default and every implementing class's effective implementation. There
  is no class hierarchy, so no hierarchy-acyclicity check exists.
- **Construction shape.** `NewObject`'s field count matches the class.
- **Entry point.** The entry function takes exactly one parameter.
- **`ListSpread` is rejected (`V015`).** Variable stack expansion is not part
  of the accepted contract; source-level variadic spread compiles to the
  fixed-stack `NewList`/`ListAdd`/`ListExtend` convention. The opcode byte
  remains decodable for format stability but faults in the VM.

Diagnostics are sorted, deduplicated, and capped (100 per function) so
reporting is deterministic and bounded on malicious graphs. Verification
does not depend on debug metadata.

## What this does and does not guarantee

Established:

- Optimized and unoptimized compiler output verifies independently under
  exact joins (the full conformance suite passes in both modes, and the
  differential test compares their outputs).
- Malformed stack shapes, region violations, inconsistent dispatch targets,
  and decoder-level malformations are rejected deterministically without
  panic or hang (unit tests in `src/verifier.rs`).
- The analysis always terminates: finite state domain plus explicit
  rejecting resource limits.

Not established (separate projects):

- No abstract type interpretation: values are still checked dynamically at
  runtime (types, bounds, nulls, receiver classes). Verification is a
  stack-shape and control-flow proof, not a type safety proof.
- Not a security sandbox: the raw `CodeModule`/VM APIs execute whatever they
  are given; only the CLI and `compile()` pipeline verify first.
- A few deliberately conservative corners remain: a `FinallyEnd` fallthrough
  may be modeled where the VM would rethrow only for exotic external
  bytecode (compiler output never triggers this), and `Dup` on an empty
  stack is rejected by the contract although the VM tolerates it.

## Performance

Measured with `cargo bench --bench bench -- --filter verify` (fresh
diagnostics per sample, prepared decoded modules, release profile, same
machine before/after):

| module | baseline median | final median | delta |
| --- | ---: | ---: | ---: |
| int_loop (1 fn) | 1210 ns | 1120 ns | −7% |
| locals (1 fn) | 3700 ns | 3130 ns | −15% |
| mixed (4 fns) | 4189 ns | 4000 ns | −5% |
| tiny (9 fns) | 2640 ns | 3009 ns | +14% |
| medium (401 fns) | 110720 ns | 108709 ns | −2% |
| large (4001 fns) | 1027803 ns | 1005204 ns | −2% |

The tiny-module increase is sub-microsecond (~0.4 µs) and within run-to-run
noise on that scale; medium/large modules verify slightly faster than the
old maximum-height scan because the worklist processes each block once
instead of re-scanning all blocks per pass. Total compile times are
unchanged within noise (see [PERFORMANCE.md](PERFORMANCE.md)).
