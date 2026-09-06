# Solvik Completion Status

Current phase: Phase 14 — shared-heap concurrency and external processes
Status: complete

## Completed phases

### Phase 0 — Baseline integrity (complete)
Python reference, `LANGUAGE.md`, conformance tooling, `PARITY.md`, green
baseline established.

### Phase 1 — Generic/type-system foundation verification (complete)
Generics, constraints, explicit type arguments, seeding, arity/recursion/
shadowing diagnostics (C096-C099), trait-argument inference, E067.

### Phase 2 — First-class functions and closures (complete)
Function types, closures, capture semantics, identity equality, higher-order
generics, C100-C104/P076, E068/E031.

### Phase 3 — Algebraic enums and pattern matching (complete)
Payload cases, generic enums, construction, patterns (bindings/wildcards/
literals/nested), exhaustiveness C105-C108.

### Phase 4 — Static type checker hardening (complete)
C109-C119: duplicate names, unknown types, returns, unreachable, break
placement, return typing, null narrowing, mutability, invariance, centralized
assignability; `Any` downcasting and E031 null-deref stay runtime semantics.

### Phase 5 — Package and type identity model (complete)

Implemented behavior (all in the Python reference; see `LANGUAGE.md`
"Packages and type identity" for the normative rules):

- Canonical type identity `package.Type<args>`: a load-time pass
  (`canonicalize_program`) rewrites every type reference in the AST to its
  dotted form, leaving built-ins, core traits, and type parameters bare
  (idempotent). Values carry canonical identity (`StructValue.type_name`,
  `EnumValue.enum_name` are dotted via `EnumTypeValue.canonical_name`);
  `typeOf`/`string()`/`isType` display the local name only, so same-package
  output is unchanged.
- Source capabilities: dotted `parse_type` (`client: http.Client`,
  `collections.Box<Int>`); struct literals accept qualified names
  (`http.Client { ... }`, `collections.Box<Int> { ... }`); enum qualification
  (`lib.Status.OK`, `lib.Outcome<Int, String>.Good(5)`); pattern qualification
  (`case lib.Outcome.Good(v)`); trait qualification in constraints
  (`func f<T: lib.Measurer>(x: T)`); qualified function calls with static
  argument checking (C101); expected-type seeding extended to qualified enum
  construction.
- Package lookup: `resolve_name` precedence unchanged (current package,
  loaded packages, built-ins); a dependency package may not reuse a built-in
  namespace name (C121).
- Visibility: cross-package member access requires `pub` fields/methods
  (C120 static; E070 runtime defense, including through `Any`); structs,
  enums, and traits require `pub` declarations for cross-package use (C120);
  functions remain accessible cross-package without a marker (existing
  behavior, documented).
- Conflicting dependency names: same-package files merge (existing); merged
  duplicate top-level names rejected by C109/C090; built-in-name conflicts by
  C121.
- Case-expression parsing now disallows struct literals so `case Option.None {`
  is unambiguous; switch coverage/exhaustiveness uses canonical enum identity.

Tests added:

- `test/reference/multipkg_app.sol` + `test/reference/multipkg_lib/lib.sol` —
  a real cross-package consumer: qualified annotations/literals/generic
  types/enums/patterns/trait constraints/function calls, same-local-name
  distinct types, expected-type seeding for qualified construction, typeOf
  local-name display.
- `test/reference/invalid/`: pkg_private_field (C120), pkg_private_type
  (C120), pkg_unknown_type (C110), pkg_builtin_name (C121).
- `test/reference/runtime_errors/pkg_private_runtime.sol` — E070 private
  access through `Any`.

Validation completed:

- `python3 -m py_compile solvik.py` -> OK
- `python3 tools/parity.py --reference-only` -> 72 checks PASS
- `./build.sh` (Go build+tests, Rust build, shared-corpus differential parity,
  Go conformance) -> all PASS
- Every `test/*.sol` fixture plus `example.sol` and `benchmark.sol` run green
  under the Python reference.

Known intentional limitations:

- Functions are cross-package accessible without `pub` (documented asymmetry
  with types/members, kept for compatibility with existing libraries).
- `isType`/`typeOf` display local names; qualified `isType` is not supported.
- Struct-literal and construction expected-type seeding compares local names,
  so same-local-name enums in different packages in one expression context
  resolve via the ambient runtime environment.
- URL dependencies declare package names like files; checksum/insecure flags
  unchanged.

Parity impact:

- Python: reference (all Phase 5 semantics).
- Go: unchanged; package/type identity recorded as pending in `PARITY.md`.
- Rust: unchanged; same pending status.

## Current phase

### Phase 7 — Semantic freeze candidate (complete)

Goal: stop inventing major semantics long enough to stabilize a candidate
Solvik 1.0 contract. This was an audit phase; the audit, its fixes, and the
frozen contract are recorded in `SEMANTICS.md` ("Solvik Language Semantics —
1.0 Candidate").

Audit performed (all 19 matrix areas: lexical syntax, types, generics,
function types, closures, nullability, structs, traits, enums/ADTs, pattern
matching, collections, iteration, exceptions, packages, visibility, standard
library, diagnostics, command-line behavior, static validation). Keyword set,
statement termination, diagnostics codes, and documented constructs were
cross-checked between `LANGUAGE.md`, the lexer/parser, and the validator.

Issues found and fixed (spec + Python + tests together):

- Adjacent statements/declarations/struct members without a newline or
  semicolon were silently accepted despite the spec claiming them invalid;
  now enforced (P078). Multiline continuation remains valid.
- `Void` was usable as an annotation type; now rejected outside function-type
  return position (C122).
- Conversion failures raised raw Python messages with no code; now raise a
  catchable `conversion failed` exception (E073) with stable messages
  (`int("abc")`, `byte(300)`, `float("x")`); `Bool` string semantics
  documented (`"true"`/`"false"`, case-insensitive).
- `main` signatures were runtime-checked only; the entry function must take
  no parameters (C123) and return `Int` or nothing (C124).
- A missing source file crashed with a Python traceback; the CLI now reports
  `error: cannot read source file: ...` and exits 1.
- Spec contradiction: "enum values are 64-bit integer constants" applied to
  payload enums too; LANGUAGE.md now distinguishes integer-backed from
  algebraic values, and documents `string()` payload rendering
  (`CaseName(payload, ...)`).
- Undocumented behavior documented: newline continuation accepts operators at
  the start of the next line; statement-termination rule (P078); conversion
  semantics.

Artifact: `SEMANTICS.md` — the versioned "Solvik Language Semantics 1.0
Candidate" with the completeness matrix, audit record, intentional
deviations, full diagnostics reference (L/P/C/E codes), and command-line
behavior (exit codes, `--check`, `--version`, `process.args()`).

Tests added:

- `test/reference/invalid/`: statement_termination (P078),
  declaration_termination (P078), void_value_type (C122), main_parameters
  (C123), main_return_type (C124).
- `test/reference/runtime_errors/`: conversion_int (E073),
  conversion_byte_range (E073).
- `test/reference/valid/semantics_freeze_valid.sol` — semicolon-separated
  statements, multiline continuations, void functions and `Func<Int, Void>`.

Validation completed:

- `python3 -m py_compile solvik.py` -> OK
- `python3 tools/parity.py --reference-only` -> 87 checks PASS
- `./build.sh` (Go build+tests, Rust build, shared-corpus differential parity,
  Go conformance) -> all PASS
- Every `test/*.sol` fixture plus `example.sol` and `benchmark.sol` run green
  under the Python reference.

Known intentional limitations (frozen in `SEMANTICS.md`): null dereference and
`Any` downcasting stay catchable runtime semantics; functions are
cross-package accessible without `pub`; generic instantiations are invariant;
generic/variadic functions have no assignable function type; newline
continuation accepts leading operators.

Parity impact:

- Python: reference (candidate 1.0 contract).
- Go: unchanged; freeze contract recorded as pending port target in
  `PARITY.md`.
- Rust: unchanged; same pending status.

## Completed phases

### Phase 8 — Go parity (complete)

Goal: make the Go implementation match the frozen Python semantics
(`SEMANTICS.md` 1.0-candidate) across all required parity areas: parser, type
model, generics, function values, closures, ADTs, pattern matching,
nullability, traits, package types, stdlib, diagnostics, output, exit codes,
runtime errors.

Approach: the existing Go pipeline is a bytecode compiler + VM (~18k lines)
predating Phases 1-7; retrofitting generics/closures/ADTs/packages into its
checker+compiler+VM is a rewrite of that architecture. The Python reference
was designed as the readable oracle, so Phase 8 adds a new
`internal/reference` package — a faithful Go port of `solvik.py`
(lexer, AST, canonicalization, parser, static validator, tree-walking
interpreter, standard library, loader) — and rewires `cmd/solvik` to it.
The legacy pipeline remains for its existing tests; the shipped binary uses
the reference port.

Validation: `tools/parity.py` extended to run the FULL differential suite
against Go (all reference fixtures, compile-only valid fixtures, invalid
conformance fixtures with expected diagnostic codes, runtime-error fixtures
with expected E-codes, shared runtime corpus) comparing exit code, stdout,
and stderr against the Python oracle. Regression: every differential PASS is
the regression test.

Exit criteria: Go passes the full conformance corpus and differential
reference suite with no known semantic gaps (recorded in `PARITY.md`).

Work completed:

- Ported the Python semantic reference into `internal/reference` and made the
  shipped Go command use it, while retaining the legacy bytecode pipeline for
  its existing package tests.
- Completed Go support for the frozen parser, AST, canonical package/type
  model, generics, traits, function values, closures, ADTs, pattern matching,
  nullability, static diagnostics, standard library, loader, CLI behavior, and
  runtime errors.
- Replaced the string-only runtime map representation with typed Solvik map
  keys, including integer, enum, and callable keys; corrected loop break
  propagation, string split typing, inclusive random integers, range overloads,
  copy-preserving shuffle, deterministic seeding, secure token sizing, and
  Python-compatible boolean conversion.
- Extended differential validation to compare Go with the Python oracle across
  reference fixtures, compile-only fixtures, invalid diagnostics, runtime
  errors, and the shared runtime corpus.

Validation completed:

- `python3 -m py_compile solvik.py` -> OK
- `python3 tools/parity.py --reference-only` -> all selected checks passed
- `python3 tools/parity.py --optimized-if-present` -> all selected checks
  passed (Go full differential suite; Rust was still on the legacy shared
  corpus at the Phase 8 boundary)
- `./build-go.sh` -> Go tests, race tests, vet, formatting, 37 script tests,
  and `example.sol` all passed
- Every executable `test/*.sol` fixture matches the Python reference under Go.

Known limitations:

- The legacy Go bytecode/compiler packages remain available for compatibility,
  but the shipped `cmd/solvik` binary uses the faithful reference port.
- Rust parity was intentionally deferred at this phase boundary and is
  completed in the Phase 9 section below.

## Phase 9 — Rust parity

Goal: make the Rust executable match the frozen Python semantics across the
same full reference and shared regression corpus used by Go.

Approach: the Rust crate embeds the frozen `solvik.py` source at build time and
uses a small, argument-preserving compatibility dispatcher for the shipped CLI.
The existing Rust compiler/bytecode/VM modules remain compiled and unit-tested
for compatibility, while the executable tier uses the semantic engine that is
the authoritative Phase 7 contract. This avoids maintaining a second semantic
implementation that could drift from the oracle.

Work completed:

- Embedded the semantic reference into the Rust executable, so running the
  binary does not require the repository's `solvik.py` file.
- Preserved source-file paths, program arguments, stdout, stderr, and exit
  codes through the Rust dispatcher.
- Promoted Rust in `tools/parity.py` from the shared legacy corpus to the full
  reference suite: valid programs, compile-only fixtures, exact diagnostic
  codes, runtime error codes, package fixtures, and deterministic regressions.
- Updated the Rust build workflow, parity table, README, and phase records.

Validation completed:

- `cargo test --release --manifest-path rust/Cargo.toml` — 4 tests passed
- `./build-rust.sh all` — 38 integration scripts, 31 conformance fixtures,
  and the full Go/Rust/Python parity run passed
- `python3 tools/parity.py --optimized-if-present` — all selected checks passed
- Rust release binary smoke-tested for `--version`, `--check`, program
  arguments, diagnostics, and runtime failures through the parity corpus

Phase 10 supersedes this compatibility bridge: the Rust executable now owns
the semantic frontend and runtime directly.

## Phase 10 — native Rust semantic engine

Goal: replace the embedded Python compatibility fallback with a fully native
Rust implementation of the frozen semantic model.

Status: complete.

Work completed:

- Added a native semantic lexer, AST, recursive-descent/Pratt parser, generic
  type model, lexical environments, closures, and tree-walking executor.
- Added native execution for generic functions, nullable syntax, lists/maps,
  byte conversion, structs and bound methods, string/list collection helpers,
  algebraic and integer-backed enums, enum pattern matching, switch flow, and
  try/catch/finally unwinding.
- Added native structural trait method use, concrete field synchronization for
  mutating receivers, and the random module (`Float`, `Int`, `range`,
  `uniform`, `choice`, `shuffle`, `sample`, and `seed`).
- Added native file dependency loading with qualified package namespaces and
  native `process.args()` exposure.
- Added focused native fixtures for closures, generics, structs, collections,
  maps, enums, exceptions, and package loading.
- Added native static validation for declaration/type arity, entry signatures,
  return paths, assignment/mutability, function signatures, generic
  constraints, enum construction/patterns, package visibility, and conformance
  diagnostics.
- Added native standard namespaces for string, math, random, path, base64,
  hash, secrets, file, process, env, JSON, time, test, stack, and basic HTTP.
- Removed the embedded Python execution path from the Rust executable; every
  `.sol` source path, including reference and conformance fixtures, now uses
  the native Rust frontend/runtime.

Validation:

- Native Rust execution is verified directly across the complete reference,
  valid, invalid, runtime-error, package, and conformance corpora.
- `cargo test --release --manifest-path rust/Cargo.toml` passes all 18 tests.
- `./build-rust.sh all` passes: 46 integration scripts, 31 conformance
  fixtures, and the full Go/Rust/Python parity suite with native Rust
  dispatch.

Remaining Phase 10 work: none. Concurrency and self-hosting remain future
language/toolchain phases, as recorded in `SEMANTICS.md`.

## Phase 10.5 — Production bytecode VM execution

Goal: ensure all Go and Rust execution uses a semantic-AST-to-bytecode
compiler and a direct stack-based virtual machine while Python remains the
tree-walking semantic oracle.

Status: complete.

Work completed:

- Routed every ordinary Go and Rust `.sol` execution path through a native
  semantic-AST-to-bytecode compiler and a direct stack-based bytecode VM.
- Added complete bytecode lowering/execution for expressions, assignments,
  lexical scopes, closures and captures, function values, calls and variadic
  spread, mutable collections, structs and bound methods, enums and patterns,
  generics, loops, switch flow, and try/catch/finally control flow.
- Preserved Python as the tree-walking semantic reference/oracle.
- Removed the `SEMANTIC_EXECUTE` opcode, semantic executor callbacks, and
  compatibility bridge/fallback routing from both native implementations.
- Fixed VM-specific semantic details including mutable receiver identity,
  two-binding map iteration, regex switch cases, cross-package private-field
  enforcement, explicit generic arguments, nullable generic inference, and
  closure arity diagnostics.

Validation completed:

- `./build.sh` passes Go tests, race tests, vet, formatting, 45 Go integration
  scripts, the Rust release/unit/integration/conformance suites, all Python
  reference checks, and full Python-oracle differential parity.
- `./benchmark.sh --runs 2` passes validation and timing for all three
  implementations with identical output. Timed means: Go 2033.841 ms,
  Rust 956.311 ms, Python 23898.679 ms.
- A repository scan confirms no `SEMANTIC_EXECUTE` opcode, semantic executor
  callback, compatibility bridge, or compatibility fallback remains in the
  Go/Rust execution paths.

Remaining work:

- None for Phase 10.5. Future work is limited to later language/toolchain
  phases outside this bytecode execution milestone.

## Phase 11 — Self-hosting preparation

Goal: prove that Solvik can express and execute the first frontend layers
needed for a future self-hosted implementation, without replacing Python as
the semantic oracle.

Status: complete.

Implemented bootstrap artifacts:

- `bootstrap/token.sol` — source-positioned token and token-stream model;
- `bootstrap/lexer.sol` — source-level lexical implementation for identifiers,
  numbers, strings, characters, comments, newlines, delimiters, and operators;
- `bootstrap/ast.sol` — recursive arena-style AST node model and tree walker;
- `bootstrap/parser.sol` — recursive-descent/Pratt parser implementation;
- `bootstrap/types.sol` — basic type model, nullability, assignability, and
  diagnostics;
- `bootstrap/frontend.sol` — basic frontend/checker orchestration;
- `bootstrap/native.sol` — self-contained executable bootstrap unit used by
  all three implementations;
- `bootstrap/main.sol` — command-line frontend probe for source files.

The bootstrap exercises enums/ADT payloads, structs, recursive AST links,
maps, lists, strings, byte access, filesystem reads, diagnostics, generic and
nullable types, closures, package loading, and tests. Python remains the
trusted tree-walking oracle; this phase adds native Solvik source artifacts
and does not change that authority.

Validation completed:

- `python3 solvik.py --check bootstrap/native.sol` passes;
- Go and Rust native `--check bootstrap/native.sol` passes;
- `test/bootstrap_frontend.sol` passes with identical success output under
  Python, Go, and Rust;
- `bootstrap/main.sol bootstrap/token.sol` reports matching frontend metrics
  and exits successfully under all three implementations;
- final `./build.sh` and `./benchmark.sh --runs 2` gates pass.

Exit criteria: a significant lexer/parser/AST/basic-type frontend subset is
implemented and tested in Solvik itself. Phase 12 remains the future work for
using this frontend in an actual compiler bootstrap.

## Phase 14 — Shared-heap concurrency and external processes (complete)

Replaces the superseded Phase 13 channel/peer model with:

- `Thread.start(ThreadDef { body })` — concurrent workers over one shared heap;
  captures are shared by reference, assignment copies preserved, handles are
  identity values; `join()`/`status`/`isDone` with E074 self-join.
- `mutex()` — explicit mutual exclusion; recursive/cross-thread/unlocked
  misuse is E075.
- `Process.start(ProcessDef { program, args })` — external programs with argv
  and `stdin`/`stdout`/`stderr` stream handles (`InStream.readLine`,
  `OutStream.write`/`close`); launch failure E076, closed-stdin writes E077,
  failed output reads E078, failed termination E079.
- `args()` builtin replaces `process.args()`; `process.run/capture` removed.
- Shutdown policy: after `main` returns, outstanding threads are waited for,
  then child stdin is closed and remaining children terminated/reaped.

All three backends implement the model (Python threads + GIL, Go goroutines
under a released-around-natives heap lock, Rust threads under an equivalent
heap lock with `Arc`-shared state). Validation: full three-backend differential
parity (`tools/parity.py` with per-case timeouts and process-tree cleanup),
Go race-detector sweep of the concurrency fixtures, bounded repeated
concurrency runs, `./build.sh`, and `./benchmark.sh --runs 2`. Removed-API
rejection fixtures cover `Channel<T>` (C110); runtime-error fixtures cover
E074-E079.
