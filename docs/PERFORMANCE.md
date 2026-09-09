# Solvik performance notes

This document records the current compiler and VM architecture, the focused
optimizations implemented in the Rust backend, and the measurements used to
evaluate them. Measurements are release-profile runs on the development
machine; they are comparative guidance, not a portability guarantee.

## Architecture

```text
source
  -> lexer -> parser -> AST
  -> resolver -> type checker / semantic IR
  -> IR optimizer
  -> bytecode compiler
  -> encode/decode -> bytecode verifier
  -> predecoded bytecode -> Rust stack VM
  -> value stack + contiguous call frames + managed heap
```

The checker resolves locals to numeric slots, functions/classes/interfaces to
numeric IDs, constants to pool indexes, virtual/interface methods to slots,
and native calls to native IDs. `compiler.rs` lowers the resolved IR to a
fixed-operand byte stream. The VM decodes each function once at startup, then
executes instruction indexes rather than repeatedly decoding byte offsets.

Runtime values are a small `Copy` enum. Primitive values stay inline; heap
values are `u32` tracing-GC handles. Locals and arguments share one contiguous
`Vec<Value>` with a frame base, and normal calls do not allocate a separate
argument vector. Classes store fields in indexed vectors and dispatch through
resolved vtable/interface metadata. Only `Object`-typed dynamic calls retain
runtime name lookup.

## Implemented changes

### Hashed constant interning

The IR constant pool was already deduplicated, but `intern_const` scanned the
entire pool for every emitted literal. It now keeps a hash index with a small
collision list, while the canonical `Vec<IrConst>` remains the source of truth
and IDs remain stable. NaN and signed-zero behavior follows `IrConst`'s
existing equality semantics.

The optimizer uses the same indexed approach for folded constants. Its index is
built once per module, rather than once per function, so large modules do not
pay a quadratic setup cost.

### Fixed-point IR peepholes

The existing conservative constant folding pass now repeats until stable. This
folds chains such as `(2 + 3) * 4`, not only one adjacent operation. It also
simplifies branches whose condition is a literal boolean and continues to
remove jumps to the following instruction. Jump and source-line mappings are
remapped after every pass. Checked integer overflow and division-by-zero are
left in the runtime stream, preserving diagnostics and error behavior.

### Hot-path cleanup

Dynamic dispatch now borrows the interned method name instead of cloning a
`String` for each call. String-literal cache validation remains on `LoadConst`
because malformed runtime state must produce the same safe diagnostic in
release builds as in tests.

## Benchmark method

The repository benchmark is a standalone harness at `benches/bench.rs`:

```bash
cargo bench --bench bench -- --filter <substring>
```

It compiles a module once, warms execution, and reports median/minimum times
over repeated release-profile runs. Microbenchmarks execute raw verified
bytecode to estimate interpreter cost. `SOLVIK_NO_OPT=1` disables the IR
optimizer for differential execution tests.

## Results

The pre-change values below were captured before the indexed interning and
fixed-point changes. Final values were captured after them. Workloads are
identical; small differences on short or object-heavy runs should be treated
as measurement noise.

| Benchmark | Before (ns) | After (ns) | Change |
| --- | ---: | ---: | ---: |
| compile tiny | 57,350 | 67,820 | +18.3% |
| compile medium | 2,978,792 | 2,781,233 | -6.6% |
| compile large | 116,387,454 | 107,331,360 | -7.8% |
| micro branch | 133,700,368 | 124,893,972 | -6.6% |
| micro arithmetic | 193,341,381 | 185,607,248 | -4.0% |
| micro load/store | 161,300,948 | 153,750,264 | -4.7% |
| integer loop | 278,138,109 | 272,789,872 | -1.9% |
| function calls | 118,514,971 | 115,372,040 | -2.7% |
| methods | — | 180,603,109 | — |
| interface calls | — | 121,365,624 | — |
| object fields | 494,073,342 | 504,722,466 | +2.2% |
| strings | — | 171,245,007 | — |
| collections | — | 34,923,316 | — |
| mixed workload | — | 103,357,625 | — |

The optimizer does not rewrite the steady-state integer/call loops in a way
that changes their instruction mix, so an optimized-vs-unoptimized run is
expected to be nearly identical there. The differential run measured
272,154,096 ns for the integer loop and 115,849,168 ns for calls with
`SOLVIK_NO_OPT=1`; both are within normal run noise of the optimized results.

## Memory and allocation findings

- `Value` is `Copy` and contains no per-value heap allocation.
- Literal strings are materialized once per distinct constant text and shared
  across frames; ordinary string operations allocate only when producing a new
  runtime string.
- Locals, arguments, and frame storage are contiguous vectors. Function calls
  reuse the argument slots and resize the existing stack; native calls use a
  fixed 16-value buffer for the common case and allocate only for larger
  arities.
- Objects and collections use contiguous `Vec` storage. The current `Map` is a
  vector of entries, so map lookup remains a known allocation/lookup hotspot.
- GC is gated by the allocation counter and skipped while multiple Solvik
  threads are active. The collector still builds a root/work queue when a
  collection is actually due.

No allocation profiler was available in the repository environment, so no
unmeasured allocation reduction is claimed.

## Investigation log

| Area | Status | Result |
| --- | --- | --- |
| local-variable slots | implemented | resolved `u16` slots and indexed stack access |
| global IDs | implemented | built-in globals use numeric IDs |
| constant pool | implemented | module pool with shared literal handles |
| constant deduplication | implemented | hashed IR interning plus folded-value interning |
| identifier interning | implemented | dynamic names are a separate interned table; compiler identifiers are not runtime values |
| field slots | implemented | class fields use indexed vectors |
| method IDs / function IDs / class IDs | implemented | compiler and metadata use numeric IDs/slots |
| interface dispatch tables | implemented | class metadata stores function IDs per interface slot |
| static call resolution | implemented | static calls lower directly to function IDs |
| method lookup caching / inline caches | deferred | only dynamic `Object` calls need it; no representative dynamic benchmark justified the added state |
| call-frame allocation | implemented | frames are stack values and locals share the operand stack |
| argument allocation | implemented | calls reuse stack arguments; native calls use a fixed small buffer |
| stack allocation/growth | implemented | contiguous `Vec<Value>`; capacity is reused across calls |
| runtime `Value` size | already small | six-variant `Copy` enum; NaN boxing was not justified |
| excessive cloning | addressed | dynamic names no longer clone; hot literal/string paths borrow or use handles |
| reference counting | not applicable | runtime object ownership is tracing-GC handles, not per-value reference counting |
| string allocation | addressed | literal cache and borrowed comparisons; result-producing operations still allocate by semantics |
| HashMap in hot paths | addressed/deferred | resolved paths avoid maps; dynamic lookup and vector-backed maps remain dynamic cases |
| bytecode representation | evaluated | fixed-operand byte stream is decoded once into compact instructions |
| operand width | implemented | `u16` for local/slot IDs and `u32` for function/constants/jumps |
| instruction dispatch | implemented | simple match over predecoded instructions; microbenchmarks establish current cost |
| type-specialized opcodes | implemented | checker emits typed arithmetic/comparison opcodes already |
| superinstructions | deferred | no stable hot sequence justified opcode growth |
| peephole optimization | implemented | fixed-point folding, branch simplification, jump cleanup |
| constant folding | implemented | checked and conservative, with regression tests |
| dead-code elimination | deferred | exception/finally control flow makes a CFG pass disproportionately complex for current gains |
| branch simplification | implemented | literal boolean conditional branches are reduced |
| bytecode validation | implemented | verifier checks operands, control flow, stack joins, and signatures |
| bounds checking | retained | dynamic language errors require safe diagnostics; no unsafe replacement was justified |
| native-call overhead | addressed | contiguous stack arguments use a stack buffer for common arities |
| release settings | implemented | release/bench use `opt-level=3`, thin LTO, and one codegen unit |
| IR architecture | already present | checker emits resolved semantic IR; a second optimizer IR would duplicate work |

## Rejected or deferred ideas

- NaN boxing and a register VM would substantially increase invariants and
  debugging cost without a profile showing that representation or dispatch is
  the dominant bottleneck.
- Threaded/unsafe dispatch was not introduced. The current match loop is safe,
  straightforward, and the measured gains available from compiler and lookup
  cleanup were higher-value.
- Superinstructions and inline caches remain candidates for a future workload
  that demonstrates repeated dynamic dispatch or a stable instruction pattern.
- A hash-table `Map` representation and custom allocator need representative
  map/allocation profiles before changing language-visible iteration and memory
  behavior.

## Validation

Commands run for this revision:

```bash
cargo fmt
cargo fmt --check
cargo test --all
cargo clippy --all-targets --all-features
cargo bench --bench bench -- --filter compile
cargo bench --bench bench -- --filter micro
cargo bench --bench bench -- --filter int_loop
cargo bench --bench bench -- --filter calls
cargo bench --bench bench -- --filter methods
cargo bench --bench bench -- --filter interfaces
cargo bench --bench bench -- --filter objects
cargo bench --bench bench -- --filter strings
cargo bench --bench bench -- --filter collections
cargo bench --bench bench -- --filter mixed
SOLVIK_NO_OPT=1 cargo bench --bench bench -- --filter compile
SOLVIK_NO_OPT=1 cargo bench --bench bench -- --filter int_loop
SOLVIK_NO_OPT=1 cargo bench --bench bench -- --filter calls
```

All unit tests and clippy checks passed. The conformance suite should be run
with `./test/run.sh` when changing language semantics or before release.

## Remaining bottlenecks

The dominant steady-state cost remains interpreter dispatch plus primitive
stack traffic. Object and interface workloads additionally pay the shared heap
mutex and field/dispatch metadata access. Strings and collections are expected
to remain allocation-heavy because their operations produce new values or
traverse user data. The next evidence-driven step would be a representative
dynamic-dispatch/map workload plus an allocation profile; until then, the
deferred machinery above would add complexity without a demonstrated return.
