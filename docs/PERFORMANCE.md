# Solvik performance

Measured optimization follow-up, 2026-09-09. The baseline is the working tree
at the start of this task (the implementation in `e254eac`), including its
existing constant interner, fixed-point folding, and borrowed dynamic names.
This report supersedes the earlier provisional performance notes; existing
slot/ID, frame, literal-cache, and typed-opcode work is not claimed as new.

## Architecture and compiler-to-VM contract

```text
UTF-8 source + indexed line starts
  -> lexer: tokens
  -> parser: owned AST
  -> resolver: names, hierarchy, field layout, vtables, interfaces
  -> checker: types, nullability, generics, resolved stack IR
  -> optimizer: block-local constant folding, branch simplification,
                unreachable-block removal, target/line remapping
  -> compiler: fixed-operand bytecode and dispatch metadata
  -> binary encode/decode round trip
  -> verifier: instruction/operand checks and exact stack/control-flow analysis
  -> VM: cached slice of predecoded instructions
       + contiguous value stack and call-frame vector
       + shared, mutex-protected tracing heap
```

The original pipeline was already a stack VM with a useful resolved IR.
The new work improves that architecture rather than adding a second IR.

- Locals and parameters use `u16` slots in the shared value stack. Function
  calls reuse argument slots; resizing only allocates when capacity runs out.
  Frames are 24 bytes on this machine. Returns unwind local slots and may
  defer through finally regions. One return value or void is supported.
- Function IDs and constant indices are `u32`. Bytecode class, field, native,
  interface, and method-slot operands generally use `u16`. Static methods
  resolve to function IDs; constructor calls retain the concrete target class.
- Instances contain a class ID and `Vec<Value>` fields. Virtual dispatch
  indexes a vtable; interface dispatch searches the class's interface-ID
  entries and indexes the selected method table. Dynamic `Object` dispatch
  still searches interned method names. Global slots are the three streams;
  this is not a user-defined global-variable namespace.
- `Value` is a 16-byte `Copy` enum. Primitive values are inline, objects use
  `u32` heap handles. Copying an object value preserves identity and aliasing.
  No per-value reference counting or boxing occurs.
- Heap slots are stored in a vector and reused from a free list. GC traces
  stack locals/operands, globals, literal handles, and pending exception/return
  values. Collection is gated by allocations and active thread count. Shared
  module/heap metadata uses `Arc`; object values do not.
- Strings own UTF-8 text; literal handles are cached per shared program.
  Lists, stacks, sets, and maps are vector-backed. Map/set equality can inspect
  string or enum contents through the heap. Mutating collection operations
  preserve references; result-producing string operations allocate new objects.
- Native calls receive a slice backed by a fixed 16-value temporary buffer for
  common arities, allocating a vector only for larger calls. The copy permits
  natives to mutate the VM without borrowing its operand stack.
- Diagnostics use source maps; runtime failures retain existing messages and
  locations. Dynamic type and bounds checks remain in the VM. Verification is
  not treated as proof permitting unchecked memory access.

## System and measurement method

AMD Ryzen 9 7900, 12 cores / 24 logical CPUs, x86-64 Linux.
Rust 1.93.1, LLVM 21.1.8. Release/bench profiles use optimization level 3,
thin LTO, and one codegen unit (bench inherits release settings). These settings
already existed and were retained; no unmeasured flag-change gain is claimed.

The uninstrumented harness uses three warmups and 10–15 measured runtime
iterations, reporting median/min/max; compiler workloads use two warmups and
50/20/5 samples. Workloads and machine were identical between comparisons.
A second full post-change run corroborated the runtime improvements; for example,
integer loops were 226.8–229.9 ms and fields 342.9–348.4 ms across runs.
CPU frequency was not pinned, so small differences and precise percentage
values are not portable guarantees.

Runtime program timings include binary decode, verification, VM setup,
predecode, execution, and teardown, but exclude source compilation.
Microbenchmarks verify raw modules before measurement and time VM setup plus
execution; their small setup cost is amortized by millions of instructions.
Results are checked across repetitions and passed through `black_box`.

The old microbenchmark denominator undercounted instructions. It is corrected
to 9/13/11 instructions per loop iteration, plus setup and the final branch.
Before/after **elapsed times**, not the old printed ns/instruction, are compared.

## Results

| Workload | Before (ns) | After (ns) | Difference (ns) | Change |
| --- | ---: | ---: | ---: | ---: |
| compile tiny | 64,130 | 53,400 | -10,730 | -16.7% |
| compile medium | 4,435,912 | 1,717,429 | -2,718,483 | -61.3% |
| compile large | 120,653,717 | 21,050,051 | -99,603,666 | -82.6% |
| integer loop | 274,501,832 | 226,128,920 | -48,372,912 | -17.6% |
| float loop | 253,406,572 | 216,616,468 | -36,790,104 | -14.5% |
| locals | 945,357,346 | 767,830,278 | -177,527,068 | -18.8% |
| branching | 372,932,937 | 317,545,277 | -55,387,660 | -14.9% |
| function/static calls | 115,791,228 | 95,650,292 | -20,140,936 | -17.4% |
| recursion | 253,273,762 | 208,026,410 | -45,247,352 | -17.9% |
| methods | 181,148,496 | 137,057,387 | -44,091,109 | -24.3% |
| interfaces | 122,205,168 | 101,722,434 | -20,482,734 | -16.8% |
| object fields | 492,959,969 | 348,415,928 | -144,544,041 | -29.3% |
| strings | 174,870,945 | 155,641,153 | -19,229,792 | -11.0% |
| collections | 34,320,179 | 27,074,843 | -7,245,336 | -21.1% |
| mixed | 104,071,010 | 85,115,136 | -18,955,874 | -18.2% |
| micro branch | 125,547,638 | 106,822,333 | -18,725,305 | -14.9% |
| micro arithmetic | 184,728,004 | 155,865,821 | -28,862,183 | -15.6% |
| micro load/store | 153,990,123 | 126,927,339 | -27,062,784 | -17.6% |
| map membership (later baseline) | 36,755,814 | 25,711,312 | -11,044,502 | -30.0% |

Map membership was added after the earlier VM changes. Its before/after pair
isolates removal of map snapshots using the same new workload; it is not a
comparison to the initial task baseline. The zero-argument call benchmark
measured 71,224,822 ns and global-slot microbenchmark 134,820,201 ns after
optimization; no initial-baseline claim is made for these added workloads.

## Profiles and changes retained

Callgrind 3.26 supplied userspace instruction-count profiles. `perf stat`
hardware counters were unsupported, and `perf record -e cpu-clock` was denied
by `perf_event_paranoid=4`. No host security setting was changed. The profiler
was extracted under a temporary directory, not installed into the repository.
Callgrind timings are **not** used as native execution-time measurements.

1. **Eager runtime diagnostics.** In the baseline field workload,
   `current_location` accounted for 7.21% of instruction references, with
   `malloc` and `free` another 6.89%. Field/list accesses allocated filenames
   even on success. Error closures now build locations only on failure.
   The isolated field timing fell from 493.0 to 408.4 ms before dispatch changes.
2. **Instruction fetch.** Baseline `execute` accounted for 33.39% of field
   instruction references, separately from `step` (42.46%). The loop now holds
   an independent shared-code reference and caches the active instruction slice,
   avoiding repeated function indexing and `Result` unwrapping. The redundant
   successor operand is gone: decoded instructions shrink from 20 to 16 bytes.
   Safe slice access and error handling remain. Broad gains in the table,
   including arithmetic and call workloads, justify retaining this change.
3. **Compiler source lookup.** Compiler profiles attributed 84.43% of self
   instruction references to inlined `check_expr` paths, dominated by repeatedly
   counting newlines from the start of the file. SourceManager now builds line
   starts once and binary-searches them; the checker asks only for a line number
   and does not allocate a diagnostic filename. Large compilation fell from
   120.7 ms to 20–26 ms across post-change runs.
4. **String result ownership.** VM and native string factories copied an already
   owned result into another String. Moving the buffer into its heap object
   preserves identity rules while eliminating one allocation per nonempty result.
   The string workload eliminates about one million allocation requests.
5. **Native collection snapshots.** A map membership benchmark exposed 960 MB of
   requested temporary memory: every lookup cloned all entries. Map get/contains,
   list contains/indexOf, and set contains now borrow entries while holding one
   heap lock. No general-purpose hash table or changed key semantics is needed.
   The measured map case drops from 36.8 to 25.7 ms and from 30,120 to 120 requests.
   List/set changes share this architecture; their individual timing benefit was
   not measured separately.
6. **Verifier representation and correctness.** Decoded verifier operands use a
   fixed `[u32; 3]` rather than allocating a vector per instruction.
   StackPop/StackPeek now have their actual zero net stack effect. Explicit
   operand-count checks reject missing inputs even when a net stack delta would
   remain nonnegative. These changes are retained for allocation reduction and
   correctness; isolated verifier speedup is not claimed.
7. **Optimizer correctness.** Folding respects jump/handler entry points.
   Removed instructions map to their next survivor; absent-handler sentinels
   remain zero. A reachability walk removes blocks exposed by constant branches
   while conservatively retaining handlers and finally continuations.
   Storage equality compares floating-point bits so folding negative zero cannot
   reuse positive zero. Language equality still follows normal floating-point
   comparisons. No arithmetic reassociation or unchecked overflow was introduced.

## Allocations and memory

The opt-in benchmark-only allocator forwards directly to System and counts
allocation/reallocation calls and requested bytes. Counts below include module
loading, startup, execution, and teardown. Requested bytes are cumulative
allocation traffic, **not** live memory or RSS. Instrumented timings are not
mixed with the uninstrumented table. Production code adds no unsafe blocks;
the benchmark allocator's unsafe implementation only forwards allocator
arguments and updates atomic counters.

| Workload | Calls before | Calls after | Requested bytes before | Requested bytes after |
| --- | ---: | ---: | ---: | ---: |
| integer loop | 115 | 97 | 13,459 | 12,131 |
| calls | 141 | 120 | 15,279 | 13,695 |
| methods | 3,000,177 | 144 | 27,018,812 | 16,627 |
| object fields | 12,000,160 | 123 | 108,019,935 | 17,179 |
| strings | 5,005,286 | 4,005,254 | 148,451,282 | 133,448,723 |
| collections | 200,156 | 124 | 10,207,427 | 8,404,895 |
| mixed | 2,502,320 | 1,002,072 | 37,293,877 | 25,585,943 |
| compile large | 730,698 | 722,260 | 58,916,730 | 58,461,531 |
| map membership, later baseline | 30,120 | 120 | 960,083,041 | 83,041 |

Arithmetic and ordinary calls do not allocate per iteration; the remaining
requests are startup/metadata/stack-capacity costs. Source indexing adds one
offset per source line. Decoded instruction elements use 20% less memory;
Value and CallFrame remain 16 and 24 bytes. No peak-RSS reduction is claimed.

## Compiler stages and bytecode sizes

Post-change median stage measurements in microseconds, two warmups and nine
samples. Checking and IR emission are one pass and cannot be meaningfully
timed separately. Source registration, inter-stage destruction, and final
teardown explain why these medians do not sum to total compilation time.

| Stage | Tiny | Medium | Large |
| --- | ---: | ---: | ---: |
| lexer | 7.88 | 189.75 | 1,789.53 |
| parser | 8.04 | 242.19 | 2,491.87 |
| resolver | 10.53 | 305.24 | 4,341.57 |
| checker + IR | 8.99 | 323.72 | 3,082.30 |
| optimizer | 1.96 | 67.01 | 663.72 |
| bytecode lowering | 1.95 | 38.96 | 380.04 |
| encode/decode + verifier | 9.14 | 209.99 | 1,928.11 |

The generated compile cases' code bytes and constant counts are unchanged:

| Program | Instructions after | Code bytes before/after | Constants before/after | Constant bytes after | Metadata bytes after | Total bytes after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| tiny | 66 | 182 / 182 | 9 / 9 | 81 | 1,253 | 1,516 |
| medium | 2,790 | 7,282 / 7,282 | 401 / 401 | 3,609 | 40,155 | 51,046 |
| large | 24,390 | 57,682 / 57,682 | 4,001 / 4,001 | 36,009 | 374,955 | 468,646 |

For the mixed program the final module contains 100 instructions, 312 code
bytes, 11 constants / 90 bytes, 1,385 metadata/header bytes, 1,787 bytes total.
The serialization format and opcode set are unchanged. Constant branches can
reduce code size in other programs. See [OPCODES.md](OPCODES.md) for every
opcode, operand, stack effect, and typical use.

## Investigation and decision log

“Existing” below distinguishes retained architecture from changes in this task.

| Area | Status | Decision / evidence |
| --- | --- | --- |
| local-variable slots | implemented | existing direct u16 indexing |
| global IDs | implemented | existing three stream slots; new global microbenchmark |
| constant pool | implemented | existing module pool and shared literal handles |
| constant deduplication | implemented | existing hash index; bit-exact float storage fixed |
| identifier interning | implemented | existing dynamic-name table; broader compiler interning deferred |
| field slots | implemented | existing vectors; removed per-access diagnostics allocation |
| method IDs | implemented | existing vtable slots |
| function IDs | implemented | existing direct u32 references |
| class IDs | implemented | existing numeric metadata references |
| interface dispatch tables | implemented | existing per-class interface-ID entries and method slots |
| static call resolution | implemented | existing direct function ID with constructor target |
| method lookup caching | deferred | resolved paths already avoid names; dynamic workload not profiled |
| inline caches | deferred | added mutable call-site state lacks measured justification |
| call-frame allocation | implemented | existing contiguous frames; no per-call object allocation |
| argument allocation | implemented | existing stack reuse and small native buffer |
| stack allocation/growth | implemented | contiguous capacity reuse; counts independent of loop iterations |
| runtime Value size | implemented | retained measured 16-byte Copy enum |
| excessive cloning | implemented | moved strings and borrowed collection reads |
| reference counting | not applicable | no per-Value RC; shared program state still uses Arc |
| string allocation | implemented | result copies removed; native input copies remain a candidate |
| HashMap use in hot paths | implemented | common access resolves to IDs; map snapshot removal avoids a representation rewrite |
| bytecode representation | implemented | retained fixed-operand serialization; compact cached predecode |
| opcode operand width | implemented | retained u16/u32 widths; no variable-length decoder |
| instruction dispatch | implemented | cached active slice; safe match-based dispatch |
| type-specialized opcodes | implemented | existing checked Long/Double and typed comparisons |
| superinstructions | deferred | dispatch is material, but fusion adds opcode/compiler/diagnostic complexity |
| peephole optimization | implemented | control-flow boundaries and remapping protected by tests |
| constant folding | implemented | checked arithmetic; signed-zero storage corrected |
| dead code elimination | implemented | conservative reachability with handler edges |
| branch simplification | implemented | known Bool branches plus unreachable-block removal |
| bytecode validation | implemented | inline operand storage; input-count and stack-op fixes; exact worklist join analysis (see [VERIFIER.md](VERIFIER.md)) |
| bounds checking | implemented | retained; verifier is not a typed proof of runtime state |
| native-call overhead | implemented | removed eager error construction and collection snapshots |
| release compiler settings | implemented | existing release/bench settings retained, not independently retuned |
| IR architecture | implemented | existing resolved stack IR; no duplicate IR introduced |

Other evaluated decisions:

- **Strict stack-join rejection:** resolved. The historical failure of an
  experimental strict-join rule on unoptimized `61-continuation-ops`
  (reported heights 1 and 0) was traced to the old verifier modeling
  `Throw` as a non-terminator and emitting a phantom fallthrough edge into
  the match merge point; the compiler output was correct. The verifier now
  runs exact worklist propagation over a finite abstract state (height,
  try-region stack, pending-transfer flag), rejects inconsistent joins and
  region violations deterministically, rejects the obsolete `ListSpread`
  opcode, and verifies all 114 conformance cases in both compilation modes.
  See [VERIFIER.md](VERIFIER.md) for the contract, guarantees, and measured
  cost. Runtime checks remain necessary: verification is a stack-shape and
  control-flow proof, not abstract type interpretation.
- **NaN boxing, custom allocators, arenas, register VM, threaded/unsafe
  dispatch:** deferred. The simple safe changes produced substantial gains.
  These alternatives were assessed architecturally, not implemented or
  benchmarked; no fabricated before/after figures are assigned to them.
- **Short-form loads and extra numeric specialization:** deferred; predecode
  already eliminates repeated operand decoding and arithmetic is already typed.
- **General constant propagation and arithmetic reassociation:** deferred to
  avoid introducing alias/dataflow analysis or changing overflow/float semantics.
- **Tail calls:** not applicable as a transparent optimization without deciding
  their interaction with frames, diagnostics, and finally behavior.
- **Hash-table maps:** deferred. Borrowing eliminated nearly all measured
  allocation traffic without changing content equality or stored order.
- **Obsolete paths:** the redundant next-instruction field, eager location
  wrapper, and invalid remapping assertion were removed. No compatibility
  layer or alternate VM was added. Legacy/raw collection opcodes remain part
  of the existing executable instruction set; removal was not justified by
  these measurements.

## Remaining profile

In the post-change field profile, step accounts for 55.25% of instruction
references and execute 33.21%; heap locking and numeric extraction remain.
The eliminated location/allocation paths are absent from the major costs.
The two runtime profiles were capped at 40 seconds of instrumentation, so
their instruction totals represent different amounts of completed work;
percentages identify hotspots, not speedups.

The complete compiler benchmark profile falls from 22.23 billion to 3.14
billion instruction references. The former dominant source scanning disappears.
Allocator routines now dominate, with lexer work, string copying/hashing,
and AST/resolver ownership spread across many functions. Runtime speed remains
the priority; redesigning AST ownership is deferred. Strings still perform
native input copies and Unicode traversal; maps retain linear content lookup.
No wall-clock ranking is inferred directly from Callgrind instruction counts.

## Reproduction and validation

```bash
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all
bash build.sh all
bash test/run.sh target/release/solvik
cargo bench --bench bench
cargo bench --bench bench -- --filter maps
cargo bench --bench bench --features bench-alloc
cargo bench --bench bench --features bench-alloc -- --filter maps
cargo bench --bench bench -- --filter stages
cargo bench --bench bench -- --filter sizes
cargo bench --bench bench -- --filter verify
```

The benchmark feature only instruments the benchmark executable, and normal
tests never depend on timing. The production build remains safe Rust.

Unit tests cover folding, target remapping, handler sentinels, float bits,
source offsets, malformed operands, collection aliasing, and GC/threads.
The integration suite compares stdout, stderr, and exit status for all 115
conformance programs with optimization off/on, including stdin and arguments.
It also runs generated constant branches and overflow/division-error comparisons.
Existing deep recursion, many locals, large collections/strings, and the
1,000-class / 4,001-function compiler workload supply stress coverage.

The original baseline passed 64 unit tests (one timing test ignored) and all
114 conformance cases. Final checks pass: 93 unit tests, three integration
tests (including the 114-case differential comparison), and all 114 release
conformance cases. The one informational timing test remains intentionally
ignored. Formatting, clippy with warnings denied, release build, and
`git diff --check` pass. No timing assertions were added to normal tests.

## Stack-VM improvement round (baseline `3c512c3`)

Measured on the current machine (24 logical CPUs, Rust 1.93.1), release
profile, same harness. Baseline numbers are from the unmodified tree at
`3c512c3`; final numbers are two consecutive full runs after all changes
(values below are from the second run; run-to-run spread is a few percent).

### Changes

- **Fused dispatch loop.** The per-instruction `step()` function call and its
  slice allocation were eliminated by inlining the opcode match directly into
  the `execute()` loop via a shared `vm_dispatch!` macro. `step()` remains
  (test-only) as a thin wrapper over the same macro, so there is one source
  of truth for instruction semantics. This is the dominant gain: micro rates
  fell from ~4.0–4.2 to ~3.5–3.6 ns/instr and every multi-instruction
  workload improved 15–35%.
- **Verified `max_stack` metadata (format v2).** The verifier now records each
  function's exact maximum operand height. The VM reserves per-frame stack
  capacity from it (`call_stack`), so hot loops no longer pay geometric
  vector growth, and the dispatch loop asserts the bound in debug builds.
  `FORMAT_VERSION` is 2; v1 modules are rejected.
- **Benchmark coverage.** Added source-level workloads (`stacks`,
  `exceptions`, `finally`, `gc_alloc`, `threads`, `dyn_calls`) and raw-module
  microbenchmarks (`micro_call`, `micro_field`, `micro_iface`).
- **Correctness fixes found while extending coverage** (no semantic change
  for previously valid programs):
  - dynamic calls on `Object` receivers never evaluated their arguments
    (every such call failed verification); arguments are now evaluated
    left-to-right, and spread in dynamic calls is a clear error (C189);
  - void dynamic calls corrupted the operand stack (the null result
    placeholder was truncated with the callee frame); it is now inserted
    below the callee frame base;
  - `TO_STRING`'s declared native arity was 1 although it is only ever
    emitted as a receiver-consumed method call (arity 0), which rejected
    every `.toString()` on class/interface/enum/object receivers.
- **Invariant tests** (`tests/invariants.rs`): `Value` size/Copy, params ≤
  locals, deterministic encoding, stable `max_stack` across round trips,
  total decoder/verifier behavior on corrupted modules, entry/line-map shape.

### Results

| workload | baseline median | final median | delta |
| --- | ---: | ---: | ---: |
| compile_tiny | 51 730 ns | 52 030 ns | +0.6% |
| compile_medium | 1 695 020 ns | 1 659 430 ns | −2% |
| compile_large | 20 529 523 ns | 18 156 128 ns | −12% |
| int_loop | 238 592 963 ns | 162 268 112 ns | −32% |
| float_loop | 228 113 867 ns | 148 612 656 ns | −35% |
| locals | 783 055 405 ns | 573 006 416 ns | −27% |
| branching | 322 560 256 ns | 236 973 732 ns | −27% |
| calls | 99 077 130 ns | 72 321 875 ns | −27% |
| zero_calls | 73 219 710 ns | 55 415 109 ns | −24% |
| recursion | 219 153 982 ns | 169 303 968 ns | −23% |
| methods | 141 115 403 ns | 100 701 441 ns | −29% |
| interfaces | 104 029 351 ns | 75 628 125 ns | −27% |
| objects | 350 589 656 ns | 270 803 695 ns | −23% |
| strings | 159 248 581 ns | 138 775 206 ns | −13% |
| collections | 27 643 260 ns | 20 339 515 ns | −26% |
| maps | 31 578 666 ns | 25 137 476 ns | −20% |
| mixed | 88 846 203 ns | 75 553 795 ns | −15% |
| micro_branch | 4.14 ns/instr | 3.58 ns/instr | −13% |
| micro_arith | 4.17 ns/instr | 3.60 ns/instr | −14% |
| micro_loadstore | 4.00 ns/instr | 3.60 ns/instr | −10% |
| micro_globals | 3.82 ns/instr | 3.45 ns/instr | −10% |

Newly covered workloads (final medians): stacks 13.0 ms, exceptions 15.6 ms,
finally 14.6 ms, gc_alloc 21.4 ms, threads 53.0 ms, dyn_calls 25.6 ms;
micro_call 7.11, micro_field 2.22, micro_iface 4.64 ns/instr.

Validation: 98 unit tests, 8 invariant tests, 3 integration tests (including
the differential comparison), and all 115 conformance cases pass; clippy with
warnings denied and `cargo fmt --check` are clean.
