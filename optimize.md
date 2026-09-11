# Optimize the Solvik Compiler and VM

## Objective

Perform a measured, repository-wide optimization pass on Solvik's Rust
compiler, bytecode pipeline, verifier, virtual machine, heap, and native
runtime. Implement improvements that produce repeatable gains while preserving
the language, diagnostics, bytecode invariants, object identity, aliasing,
exception behavior, and thread behavior.

Correctness is a hard requirement. Within that constraint, prioritize:

1. runtime execution time;
2. runtime allocation traffic and memory use;
3. compiler throughput;
4. bytecode and metadata size;
5. architectural clarity and maintainability.

Do not stop at a list of ideas. Establish a baseline, profile real workloads,
implement focused changes, validate them, measure them against the same
baseline, and retain only changes supported by evidence or a clearly documented
architectural benefit.

## Work from the current checkout

Use the current working tree as the source of truth. It may contain intentional
uncommitted work. Before editing:

- record `git rev-parse HEAD` and `git status --short`;
- read the implementation, tests, language documents, bytecode documents, and
  benchmark harness;
- preserve all pre-existing changes and do not reset, discard, overwrite, or
  reformat unrelated work;
- do not fetch, pull, rebase, or switch revisions unless explicitly requested;
- distinguish pre-existing changes from optimization changes in the final
  report.

Relevant sources include:

- `src/lexer.rs`, `src/parser.rs`, and `src/ast.rs`;
- `src/resolve.rs`, `src/types.rs`, and `src/check.rs`;
- `src/ir.rs`, `src/optimize.rs`, and `src/compiler.rs`;
- `src/bytecode/encode.rs` and `src/bytecode/decode.rs`;
- `src/verifier.rs`;
- `src/vm/mod.rs`, `src/vm/value.rs`, `src/vm/frames.rs`,
  `src/vm/heap.rs`, and `src/vm/natives.rs`;
- `benches/bench.rs` and `benches/support/`;
- `tests/optimization.rs`, `tests/invariants.rs`, and
  `tests/composition.rs`;
- `test/cases/` and `test/run.sh`;
- `LANGUAGE.md`, `SEMANTICS.md`, `BYTECODE.md`, `docs/OPCODES.md`,
  `docs/VERIFIER.md`, and `docs/PERFORMANCE.md`.

Resolve discrepancies by checking code and tests first. Update documentation
when the implementation or measured results change.

## Current architecture

The current pipeline is:

```text
UTF-8 source + indexed source lines
  -> lexer
  -> recursive-descent parser
  -> owned AST
  -> resolver
       names, nominal types, interface conformance, delegation,
       class-local field and method slots
  -> type checker + mandatory resolved stack IR emission
  -> IR optimizer
       block-local folding, branch simplification,
       unreachable-code removal, target and line remapping
  -> bytecode compiler
  -> binary encode/decode round trip
  -> exact bytecode verifier + max_stack computation
  -> per-function bytecode predecode
  -> stack-based Rust VM + native runtime + tracing heap
```

Do not introduce another IR merely for architectural symmetry. The existing
resolved stack IR is a useful optimization boundary. Replacing it, changing to
a register VM, or adding a second execution engine requires strong profile and
benchmark evidence that a smaller change cannot address the bottleneck.

## Established implementation facts

Treat these as the starting point. Verify that they still hold before relying
on them, but do not present them as new optimization work.

### Language and object model

- Solvik uses nominal classes and interfaces, private-only fields, interface
  defaults, and explicit interface delegation through composed fields.
- Class inheritance, `super`, `override`, protected fields, public fields, and
  inherited constructors or method tables are not supported.
- Composition does not create a subtype relationship.
- Delegation is lowered to ordinary synthetic forwarding methods. A forwarding
  method loads `self`, loads the delegate field, and performs normal interface
  dispatch. There is no delegation-specific opcode or runtime proxy object.
- Concrete calls use class-local method slots. Interface calls use an interface
  ID and slot. Calls through `Object` use an interned dynamic name and search
  the receiver class's public effective dynamic-method table.

Optimizations must preserve explicit-method precedence, interface-default
precedence, multiple independent delegates, interface inheritance, private
method visibility, exception propagation through forwarding methods, and GC
reachability of composed objects.

### IR, bytecode, and verifier

- `IrModule` owns a module constant pool and resolved functions, classes,
  interfaces, and dynamic names.
- Constants are deduplicated. Floating-point storage identity uses exact bits,
  including distinct positive and negative zero, while language equality keeps
  its normal floating-point semantics.
- The optimizer is enabled by default. `SOLVIK_NO_OPT=1` disables it for
  differential testing; `compile_with_optimization` provides an explicit API.
- Existing folding uses checked integer operations and does not erase runtime
  overflow or division-by-zero errors. Control-flow entry points, catch/finally
  handlers, zero handler sentinels, source lines, and jump targets are remapped
  conservatively.
- The current binary format is version 3. It contains a module constant pool,
  per-function bytecode and metadata, class-local dispatch metadata, interface
  tables, dynamic names, an entry function, and source names.
- Serialized instructions use one-byte opcodes with fixed-width little-endian
  operands. Local, field, class, interface, method-slot, native, and arity
  operands are generally `u16`; function IDs, constants, and branch targets are
  `u32`.
- Opcode 64 is unused after removal of `CallSuper`. `ListSpread` remains
  decodable for format stability but is rejected by the verifier and faults in
  the VM. Do not revive obsolete inheritance or variable-stack paths.
- The verifier performs exact worklist analysis over operand height, try-region
  state, and pending transfers. It validates structural operands and dispatch
  shapes, rejects inconsistent joins, and computes each function's `max_stack`.
  It is not an abstract type proof, so runtime type, null, receiver, and bounds
  checks remain required.

Any format or opcode change must update the encoder, decoder, compiler,
disassembler, verifier, VM, invariant tests, `BYTECODE.md`, and
`docs/OPCODES.md` together. Increment the format version for incompatible
changes. Preserve deterministic encoding and total, non-panicking rejection of
malformed input.

### VM and runtime

- The VM predecodes bytecode once into 16-byte `DecodedInstr` values containing
  an opcode and three inline `u32` operand slots. Branch byte offsets become
  instruction indices.
- The hot loop caches the current function's instruction slice and expands the
  shared `vm_dispatch!` match inline. The test-only `step` entry point uses the
  same macro so instruction semantics have one implementation.
- `Value` is a 16-byte `Copy` enum. Primitive values are inline and heap values
  use `u32` handles. Do not add per-value boxing or reference counting without
  measured justification.
- Locals, parameters, and operands share a contiguous `Vec<Value>`. Parameters
  occupy the first local slots. Calls reuse argument slots, frames live in a
  contiguous vector, and verified `max_stack` is used to reserve capacity.
- Instances hold a class ID and `Vec<Value>` fields. Field access is slot based.
- The tracing heap uses reusable vector slots and a free list. Shared program
  state and the heap are held through `Arc`; the heap is protected by a
  `Mutex` because Solvik threads share objects. GC traces stacks, globals,
  literal handles, collection contents, instance fields, and pending
  exception/return state.
- String literals have shared cached heap handles. Result-producing string
  operations move owned buffers into heap objects. Some native string inputs
  still clone text, and Unicode-scalar operations still traverse UTF-8.
- Lists, stacks, sets, and maps are vector backed. Map and set operations use
  Solvik content equality and preserve their current observable ordering and
  aliasing behavior. Common read operations already borrow collections instead
  of cloning snapshots.
- Native calls copy common argument lists into a fixed 16-value temporary
  buffer and allocate a vector only for larger calls. This permits natives to
  mutate the VM without borrowing its operand stack.
- Successful hot paths build source locations lazily; runtime error messages
  and locations are observable behavior.

## Existing performance work

`docs/PERFORMANCE.md` is the measurement record. It already documents:

- indexed source-line lookup;
- cached instruction slices and compact predecoded instructions;
- fused VM dispatch;
- verified `max_stack` reservation;
- lazy construction of runtime diagnostics;
- moved string-result ownership;
- borrowed native collection reads;
- exact verifier state and inline verifier operands;
- block-local folding, constant-branch simplification, and unreachable-block
  removal;
- existing numeric slots and IDs, constant interning, typed arithmetic and
  comparisons, literal caching, contiguous stack frames, and native small-arg
  storage.

The existing report records substantial gains from these changes. Reproduce a
fresh baseline from the current checkout instead of reusing its historical
numbers as the comparison for new work. Do not claim an existing mechanism as
a new result.

## Measurement rules

### Establish the baseline

Before changing production code:

1. Run the complete correctness suite.
2. Build release-quality artifacts.
3. Run the full benchmark suite at least twice after warmup.
4. Record machine, operating system, CPU, Rust, and LLVM versions.
5. Record elapsed-time medians and ranges, allocation requests and requested
   bytes where supported, generated module sizes, compiler-stage times, and
   verifier times.
6. Profile representative compiler and runtime workloads.

Use the repository's custom harness rather than replacing it with Criterion
without evidence that the harness is inadequate. Its runtime cases compile
once and repeatedly execute decoded and verified modules. Its raw-module
microbenchmarks isolate dispatch and selected opcodes. The `bench-alloc`
feature counts cumulative allocation traffic; it does not measure live memory,
peak RSS, or production timing.

Useful commands are:

```bash
cargo bench --bench bench
cargo bench --bench bench -- --filter stages
cargo bench --bench bench -- --filter sizes
cargo bench --bench bench -- --filter verify
cargo bench --bench bench --features bench-alloc
```

Use filters for focused iteration, then rerun the full suite before drawing a
conclusion. Compare the same source, iteration count, build profile, feature
set, environment, and machine. Do not mix allocation-instrumented timings with
normal timings.

For noisy wall-clock results:

- run enough samples to report a median and spread;
- repeat the complete before/after comparison;
- interleave or restore the exact baseline when drift is plausible;
- treat small changes within run-to-run noise as inconclusive;
- use profiler instruction counts to locate work, not as a substitute for
  native elapsed time.

Never add timing assertions to normal tests. Never shrink a workload, remove a
runtime check, skip work, constant-fold the benchmark's intended hot loop, or
change its observable result merely to improve a number. Validate benchmark
results with `black_box` or an equivalent barrier where appropriate.

### Benchmark coverage

The harness already covers integer and floating-point loops, locals, branches,
zero-argument and argument-bearing calls, recursion, concrete methods,
interfaces, object fields, strings, collections, maps, stack operations,
exceptions, finally blocks, GC allocation, threads, dynamic calls, mixed code,
compiler stages, module sizes, verification, and raw dispatch/call/field/
interface microbenchmarks.

Before optimizing an unrepresented path, add a benchmark that verifies its
result. In particular, assess whether the composition-first work needs cases
for:

- direct calls to generated delegation wrappers;
- delegated calls through an interface receiver;
- delegated calls through an `Object` receiver;
- multiple delegates and interface-default fallback;
- forwarding methods with arguments and return values;
- allocation and GC pressure from composed object graphs.

Measure both the full program effect and a focused microbenchmark when setup
cost would otherwise hide the hot operation.

## Investigation priorities

The following are hypotheses based on the current code and the latest recorded
profiles. They are not preapproved implementations. Profile each one and retain
a change only when its benefit justifies its complexity.

### 1. Dispatch after the fused-loop work

The dispatch loop and opcode bodies remain the largest runtime region. Determine
whether the remaining cost is instruction fetch, match dispatch, stack access,
numeric extraction, heap locking, or the work performed by specific opcodes.

Evaluate concrete, interface, delegated, and dynamic calls separately.
Concrete calls already use slots and should not need name lookup. Interface
dispatch currently searches a class's interface entries. Dynamic dispatch
searches a per-class vector of `(String, function ID)` pairs even though call
sites already carry interned dynamic-name IDs. Possible experiments include a
dense ID-indexed table, a sorted table, or a small cache, but account for module
size, construction cost, sparse IDs, diagnostics, and thread-safe shared state.

Also investigate two remnants of the former inheritance model:

- `CallStatic` still encodes and verifies a third `target_class` operand even
  though static methods are no longer inherited and the VM ignores that operand.
- `CallClass` encodes a statically resolved class and slot, but the VM reads the
  receiver's class and indexes its method table. With no class subtyping, test
  whether a concrete method call can safely carry a direct function ID and
  avoid receiver-class lookup. Account for calls on `Self`, erased generics,
  synthetic delegation wrappers, malformed modules, arity verification, and
  error behavior before changing the call contract.

These may be bytecode-format cleanups as well as runtime optimizations. Measure
them independently and increment the format version if either encoding changes.

Do not add inline caches, superinstructions, computed-goto dispatch, or unsafe
unchecked indexing merely because they are common in other VMs. First show the
actual call-site distribution and cost, then benchmark the smallest viable
change.

### 2. Heap locking and object access

The shared `Mutex<Heap>` is acquired by field, string, collection, equality,
and native operations, including single-threaded programs. Measure lock
frequency and cost in field-heavy, string-heavy, collection-heavy, GC-heavy,
and threaded workloads.

Explore reducing redundant lock/unlock cycles or doing more work under one
well-scoped guard. Preserve shared-heap thread safety, avoid holding the heap
lock across blocking I/O or thread joins, and do not create references that
outlive a guard. Any single-thread fast path must have a clear transition and
aliasing model and must be proven against thread spawning and GC.

### 3. Strings and Unicode

Profile native input cloning, concatenation, comparison, substring, indexing,
length, split, replace, conversion, and diagnostics separately. Prefer
borrowing while a heap guard is held and moving owned results into the heap.
Avoid keeping a guard while allocating into the same heap.

Solvik string indexes and lengths operate on Unicode scalar values. Byte-based
caches or indexes must preserve those semantics for arbitrary valid UTF-8.
Account for the extra object metadata and invalidation rules before retaining a
cache.

### 4. Collections and equality

Maps and sets intentionally use content equality and vector-backed storage.
Measure small and large collections, successful and unsuccessful lookups,
updates, removals, iteration/materialization, strings as keys, enums, object
handles, and aliasing.

A hash table is not automatically valid: hashing must exactly match Solvik
equality, stored order may be observable through `keys` and `values`, and keys
may refer to mutable heap objects. Consider narrower indexing or small-vector
strategies only after characterizing real collection sizes. Preserve the
existing result and mutation conventions of opcodes and native methods.

### 5. Allocation and garbage collection

Separate cumulative allocation traffic, live heap size, and collection pause
cost. Determine which object kinds dominate allocations and whether temporary
roots or repeated buffers force avoidable work. Inspect GC trigger frequency,
free-list reuse, mark-set allocation, traversal queues, roots held by threads,
and pending control-flow values.

Inspect capacity calculations as well as object allocation. `decode_code`
currently reserves `code.len()` elements for vectors whose final length is the
instruction count, even though `code.len()` is a byte count and each decoded
instruction is 16 bytes. `call_stack` calls `Vec::reserve` after resizing; verify
that it requests the intended additional capacity rather than treating an
absolute target capacity as an additional amount. GC currently materializes a
new roots vector from the stack and other root sets before each collection.
Measure each case before changing it and preserve the verifier's `max_stack`
contract and the collector's complete root set.

Do not introduce arenas, a custom allocator, generations, moving collection,
or handle compaction without measuring the current collector and specifying
object-identity, stale-handle, thread, and root invariants. Any GC change needs
stress tests for cycles, shared children, worker lifetimes, literal caches,
delegated/composed objects, exceptions, and collection reuse.

### 6. Compiler allocation and ownership

The previous compiler profile removed repeated source scanning; allocation,
lexing, string copying/hashing, and AST/resolver ownership then became the main
areas to investigate. Measure lexer, parser, resolver, checker/IR emission,
optimizer, bytecode lowering, encode/decode, and verifier stages separately on
tiny, medium, and 1,000-class programs.

Look for repeated identifier ownership, transient vectors/maps, repeated
generic substitution, interface-closure work, delegation-wrapper construction,
and metadata cloning. Broader identifier interning may help, but include
interner construction, lifetime complexity, diagnostics, deterministic output,
and tiny-program regressions in the decision.

Runtime speed remains the primary goal. Do not redesign the AST or resolver for
a compiler-only improvement that is small or noisy.

### 7. IR optimization and generated code

Inspect actual IR before and after optimization using `SOLVIK_DUMP_IR=1`, and
inspect bytecode with `SOLVIK_DUMP_BC=1`. Count common instruction sequences in
representative programs before proposing peepholes or superinstructions.

Potential experiments include additional local peepholes, redundant stack
traffic removal, safe propagation of immutable scalar constants, and simpler
branch layouts. Every transformation must define:

- its exact input pattern and required control-flow conditions;
- stack effect before and after;
- behavior at jump, catch, and finally entry points;
- overflow, division-by-zero, null, and floating-point behavior;
- source-line and target remapping;
- why it cannot change an observable runtime error or its location.

Do not perform arithmetic reassociation, erase checks, merge positive and
negative zero, assume NaNs compare equal, or propagate mutable/reference values
without a sound dataflow and alias model. Run optimized/unoptimized differential
execution after every optimizer change.

### 8. Bytecode and metadata size

Runtime speed takes precedence over file size. Measure code, constants,
function metadata, class/interface dispatch metadata, dynamic tables, source
maps, and total encoded bytes separately. The current predecoder already avoids
repeated operand decoding, so short-form opcodes or variable-length operands may
reduce files without improving execution.

Consider metadata deduplication or narrower representations only when limits,
overflow diagnostics, deterministic encoding, decode validation, and runtime
conversion costs are explicit. Do not optimize generated benchmark modules by
removing information required for diagnostics or verification.

The `NewList` and `NewMap` instructions carry compiler-supplied `u16` capacity
operands, but their VM implementations currently allocate empty vectors without
using those values. Measure literal-heavy and spread-heavy cases to decide
whether honoring the hint reduces reallocations, or whether removing the unused
operands is the better format cleanup. Validate allocation-failure behavior and
avoid allowing untrusted bytecode to request unreasonable eager allocation.

## Experimental discipline

For each significant candidate:

1. State the hypothesis and the code path it affects.
2. Select or add a correctness-checking benchmark that exercises that path.
3. Capture the focused and whole-suite baseline.
4. Profile before editing when a profiler can distinguish likely causes.
5. Implement one coherent change.
6. Run focused correctness tests and benchmarks.
7. Repeat measurements and check unrelated workloads for regressions.
8. Inspect allocation and size changes when relevant.
9. Keep the change only if the evidence or enabling architectural value
   justifies its maintenance cost.
10. Record retained and rejected experiments in `docs/PERFORMANCE.md`.

Revert experimental changes that are slower, inconclusive, redundant, or too
complex. Revert only the experiment's own edits; preserve all other working-tree
changes. Do not report an experiment as implemented after reverting it.

An enabling change without an immediate speedup may remain when it clearly
simplifies a hot path or unlocks a measured follow-up. Document that rationale
and its cost.

## Correctness requirements

Before relying on benchmark results, establish that the checkout is green. If
pre-existing failures exist, document them and determine whether they invalidate
the affected measurements. Do not silently fix unrelated defects as part of an
optimization. If an optimization exposes a correctness bug in its path, add a
regression test and fix the root cause before continuing.

Every retained optimization must preserve:

- evaluation order, return shape, overflow and division errors;
- nullability, narrowing, conversions, and dynamic checks;
- object identity, content equality, reference aliasing, and mutation;
- private field/method visibility and the composition/delegation semantics;
- interface defaults, inherited interfaces, and all dispatch precedence rules;
- exception unwinding, rethrow, deferred return, and break/continue through
  `finally`;
- GC roots and reachability across stacks, globals, literals, collections,
  composed fields, threads, and pending transfers;
- source locations, diagnostic codes/messages, stack traces, and exit codes;
- deterministic bytecode and rejection of malformed modules without panic or
  nontermination.

Add focused unit or integration tests for changed invariants. Avoid tests that
merely copy the implementation. Continue using the optimized/unoptimized
comparison in `tests/optimization.rs` for optimizer work. Add conformance cases
under the next free case number documented in `CONFORMANCE.md` when observable
language behavior needs coverage.

## Validation

Run the relevant focused tests during iteration. Before completion, run:

```bash
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all
bash build.sh all
bash test/run.sh target/release/solvik
cargo bench --bench bench
cargo bench --bench bench --features bench-alloc
cargo bench --bench bench -- --filter stages
cargo bench --bench bench -- --filter sizes
cargo bench --bench bench -- --filter verify
git diff --check
```

`build.sh all` already overlaps several commands; the explicit list defines the
required coverage and makes individual failures easier to report. Do not weaken,
skip, or alter tests to obtain a green result. Do not modify expected output
unless the old expectation is demonstrably inconsistent with the documented
language.

Review the final diff for accidental changes, stale comments, duplicated opcode
semantics, outdated format-version text, benchmark artifacts, temporary profile
files, and generated binaries. Do not commit large profiler outputs or machine-
specific artifacts.

## Performance report

Update `docs/PERFORMANCE.md` so another developer can reproduce and assess the
work. Include:

- starting HEAD and a note that the baseline included any relevant pre-existing
  working-tree changes;
- system, CPU, Rust/LLVM versions, build profiles, profiler constraints, warmup,
  samples, and aggregation method;
- exact benchmark commands and workload definitions or changes;
- before/after elapsed medians and ranges for the full runtime suite;
- compiler-stage, verifier, allocation, and bytecode-size results;
- profiles identifying the bottlenecks addressed;
- each retained change, why it helps, and its measured effect;
- rejected or reverted experiments and the evidence against them;
- correctness fixes discovered during optimization, with regression tests;
- remaining measured bottlenecks and promising next experiments;
- the complete final validation result.

Use absolute elapsed times and percentages. Clearly separate historical results,
the new baseline, and later-baseline experiments. Do not attribute combined
gains to individual changes unless they were isolated. Label cumulative
requested bytes correctly and do not call them live memory or RSS. State when
hardware counters are unavailable instead of substituting unsupported claims.

## Completion criteria

The task is complete when:

- the current architecture and semantics have been verified from the checkout;
- a reproducible correctness and performance baseline has been recorded;
- the major current runtime and compiler paths have been profiled;
- measured, maintainable improvements have been implemented where justified;
- inconclusive or harmful experiments have been removed and documented;
- optimizer, bytecode, verifier, VM, runtime, and composition/delegation
  invariants remain covered;
- all required validation passes, or every pre-existing/environmental failure is
  precisely documented;
- `docs/PERFORMANCE.md` accurately reports methods, results, rejected ideas, and
  remaining bottlenecks;
- the final diff contains no unrelated edits or temporary artifacts.

The final response should lead with the concrete outcome, summarize retained
changes and their measured effects, list validation performed, identify material
risks or limitations, and point to the updated performance report. Do not claim
that Solvik is fully optimized; state what was measured and what remains.
