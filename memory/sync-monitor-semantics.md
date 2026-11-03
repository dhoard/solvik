# `sync` blocks: the monitor is Java's, not generated

Recorded 2026-11 while implementing `plans/sync-block.design.md`.

## The invariant

`sync monitor { ... }` is a statement (`Ast.SyncStmt`) that lowers to exactly one typed-IR
`JStmt.Synchronized`, rendered as one Java `synchronized (monitor) { ... }` statement. Enter and
release are the JVM's own, so `return`, `break`, `continue`, `raise`, a `SOL-Rxxx` abort, and
`exit()` cannot skip the release. Do **not** replace it with a `try { ... } finally { unlock }`
desugaring, a lock object, or a support member: that reintroduces the leak the feature removes and
breaks `GeneratedOutputOptimizationTest`, which asserts the sync-free program differs from the
guarded one only by that statement.

Related rules worth knowing before touching it:

- The monitor must be a non-null `USER` or `ARRAY` value. Everything else — numeric carriers,
  `String`, `Boolean`, big numbers, `Null`, `Any`, `Number`, and `Exception` — is `SOL-T019`
  (`TypeChecker.MONITOR_TYPE`), because a boxed or interned identity may be shared. A nullable
  monitor reports `SOL-T015` only; `TypeChecker.checkSync` skips the kind check when the value is
  nullable so one mistake yields one diagnostic.
- The body is checked with `checkBlock`, so it is its own scope and `loopDepth`/`finallyDepth`/
  `return` rules are unchanged. `sync` is **not** a loop: `break` inside it is `SOL-T007`.
- `TypeChecker.terminates` and `terminatorName` have a `sync` arm: a block that always leaves makes
  the following statement `SOL-T004`. Without it, the generated Java contains unreachable statements
  and fails to compile. A bare `Ast.BlockStmt` still lacks the same arm (pre-existing gap).
- The monitor expression is lowered once, into the statement, so a call monitor runs once.

## Adding a typed-IR statement means five seams

`JStmt` (record), `JavaIrValidator.validateStatement`, `JavaSourceRenderer.collectStatement`
(reachability — a missed arm silently prunes support code), `JavaSourceRenderer.renderStatement`,
and `GeneratedIntrinsics.collectStatement` (intrinsic selection). The test helper
`JavaLowererIrTest.collectExpressions` is also exhaustive, so it fails to compile until updated.

## Language surface

`sync` is a reserved keyword (`TokenKind.SYNC`, last in the contiguous keyword range, so
`isKeyword()`'s upper bound is `SYNC`). A member name after `.` may still be a keyword, so
`value.sync()` stays legal, as `value.type()` already was. `LANGUAGE.md` §7.5 is the contract; §11.5
cross-references it and §13 assigns `SOL-T019`.

## Tests and corpus

`parser/SyncSyntaxTest` (shape, reserved word), `check/SyncTest` (accepted kinds, each rejected
kind, nullable, nesting, scope, reachability), `transpile/SyncExecutionTest` (2,000-update
contention, reentrancy, array monitor, release after `return`/`break`/`continue`/`raise`, abort
code, `exit` status), and two active `GeneratedOutputOptimizationTest` cases. `example.sol` pins
`sync guarded: 1000`, `sync nested: 1001`, `sync returned: 1002`, `sync raise: [4009] ...`,
`sync after raise: 1003` in `cli/ExampleCorpusTest`.

**Trap:** most methods in `GeneratedOutputOptimizationTest` carry `@DisplayName` but no `@Test`
(8 at the time of writing, pre-existing), so they never run. Only add shape assertions to methods
that actually execute, and do not assume the rest of that file is enforced.
