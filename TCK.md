# Solvik Technology Compatibility Kit Implementation Brief

Status: repository-grounded implementation plan; no TCK is implemented yet.

Audit date: 2026-09-26.

This document defines the work required to add an implementation-independent Solvik Technology
Compatibility Kit (TCK) to the current repository. It is not a language specification and does not
change Solvik semantics. The implementation must follow, in descending order of authority:

1. `AGENTS.md`;
2. `docs/LANGUAGE_SPEC.md`;
3. `docs/ARCHITECTURE.md`;
4. this implementation brief.

If this document conflicts with a higher-authority source, the higher-authority source wins. If the
normative specification leaves behavior unresolved, record and isolate the ambiguity; do not infer
normative behavior from the GraalVM implementation, existing golden output, README prose, Java
behavior, or another language.

## 1. Mission

Implement a production-quality, versioned TCK that verifies observable Solvik language semantics
without depending on a particular parser, compiler, interpreter, backend, host language, or runtime.
The same portable corpus and expectations must be usable without modification against:

- the current GraalVM/Truffle JVM distribution;
- the current native-image distribution;
- future JVM bytecode, LLVM, native, or interpreter implementations; and
- independently developed third-party implementations.

The TCK must distinguish specification conformance from implementation regression testing. Agreement
between two implementations is useful differential evidence, but it is not proof of conformance.

### 1.1 Terms and trust boundaries

Use these terms consistently throughout the TCK:

- **normative specification**: the versioned language semantics in `docs/LANGUAGE_SPEC.md`;
- **requirement**: one stable, testable normative obligation extracted without changing its meaning;
- **oracle**: an expected observable result derived from the normative specification;
- **implementation under test (IUT)**: the compiler/interpreter/runtime being evaluated;
- **adapter**: implementation-specific process integration that translates the common protocol to an
  IUT; it is not part of the oracle;
- **infrastructure error**: a failure of the runner, manifest, adapter protocol, host setup, or test
  fixture that prevents a conformance judgment; and
- **conformance result**: a machine-readable conclusion for an exact TCK release, specification
  version, profile, implementation identity, and platform. It is not a permanent or legal
  certification of an implementation family.

The portable runner, manifests, requirements inventory, and expected results are trusted TCK
infrastructure. The IUT and its adapter are untrusted result producers. No claim made by an adapter
is accepted without schema, state, and expectation validation by the runner.

## 2. Audited Repository Baseline

The following facts describe the repository at the audit date and must be rechecked before TCK
implementation begins. Counts are inventory aids, not acceptance targets.

| Area | Current repository state |
|---|---|
| Project status | Experimental and under active development |
| Build | Maven reactor: `language`, `launcher`, `standalone`, `coverage` |
| Toolchain | GraalVM for JDK 25; Maven wrapper; ANTLR 4.13.2; JUnit 6.1.3 |
| Language pipeline | Lexer -> semicolon insertion -> parser -> syntax AST -> include/module resolution -> semantic analysis -> typed lowering -> Truffle AST |
| Backend | One Truffle AST backend; no second Solvik backend |
| Distributions | `standalone/target/solvik` and `standalone/target/solvik-native` |
| CLI input | First non-option argument is a source file; otherwise source is read from stdin |
| CLI options | `--key` and `--key=value` are passed as Polyglot options |
| CLI result | Program stdout is separate from diagnostics; success is `0`; compile and runtime failures currently both normally return `1`; guest `exit(n)` returns `n` |
| Compile-only CLI | Not present |
| Structured diagnostics/results CLI | Not present; diagnostics are human-readable stderr text containing stable `SOLV-*` codes |
| Stable diagnostics | 115 implementation enum entries across `LEX`, `PARS`, `RESOL`, `TYPE`, and `SEM` families; only specification-required codes are automatically normative TCK expectations |
| Example corpus | 21 `.sol` programs, each with a sibling `.output` |
| Regression corpus | 76 `.sol` programs: 51 with `.output`, 25 expected rejections, and only 3 rejection fixtures with `.error` code files |
| Diagnostic fixtures | 12 `.sol` fixtures with expected stable codes in source comments |
| Java tests | 130 language test classes and 2 launcher test classes; many tests are implementation-layer tests and are not portable TCK candidates |
| Existing external corpus runner | `test-corpus.sh`; exact stdout for successful programs, nonzero plus empty stdout for rejection cases |
| Current TCK | No `tck/` directory, runner, schema, adapter protocol, report format, or TCK build integration exists |
| Python | No Python source or Python dependency is currently present |

### 2.1 Current language surface

The normative baseline is the complete non-deferred surface in `docs/LANGUAGE_SPEC.md`, currently
including:

- lexical rules, comments, normal strings, Rust-style raw strings, character and numeric literals;
- Go-style lexical semicolon insertion and leading-dot member chains;
- immutable and mutable bindings, lexical scopes, definite initialization, functions, implicit
  top-level `main`, exact arity, `print`, `println`, and `exit`;
- nominal static typing, `Any`, `Nothing`, `Unit`, nullability, safe access, coalescing, checked casts,
  type tests, and flow-sensitive narrowing;
- `Byte`, `Short`, `Integer`, `Long`, `Float`, and `Double`, lossless implicit numeric widening,
  explicit numeric conversion, checked integral arithmetic, and IEEE floating-point behavior;
- semantic equality, universal `equals`, universal `hashCode`, the equals/hash pairing rule, and
  reference identity;
- classes, constructors, final-by-default inheritance, interfaces, default methods, delegation,
  universal `toString`, static members, static storage, and lazy class initialization;
- invariant generics and built-in `List`, `Set`, `Map`, and `Stack` operations;
- value-carrying enums, abstract classes, exhaustive `match` over a closed variant set, and
  non-fallthrough statement and expression `switch`;
- block expressions, `if` expressions, shared result-type joining, and abrupt-completion rules;
- range loops, three-clause loops, `break`, `continue`, and `return`;
- the portable regular-expression dialect and `RegexMatch` values;
- compile-time includes, modules, aliases, namespace qualification, canonical file identity,
  duplicate suppression, and cycle detection;
- unchecked guest exceptions with `throw`, typed `catch`, `finally`, synthesized messages, and the
  defined program-boundary failure; and
- synthesized operations and `?` propagation for a structurally recognized two-parameter `Result`
  enum.

Features explicitly marked deferred in the specification are outside conformance until the
specification defines them. Examples include string interpolation, input APIs, command-line argument
binding, default and variadic parameters, overloading, collection iteration,
safe casts, user-defined operator overloading beyond `equals`, and regex backreferences/lookaround.
First-class function values were deferred through `2026.09-draft` and are defined as of
`2026.10-draft` (LANGUAGE_SPEC.md section 6); the constructs that revision keeps deferred are
listed there.

### 2.2 Existing tests are evidence, not the oracle

Existing tests have several useful but different roles:

- `SolvikProgramTest` and `test-corpus.sh` verify successful examples using golden stdout.
- `SolvikRegressionProgramTest` preserves a historical accept/reject baseline. Most rejection cases
  do not identify a diagnostic, compilation phase, or runtime phase.
- `SolvikDiagnosticProgramTest` checks stable diagnostic codes and source spans for a small fixture
  set.
- Parser, semantic, lowering, runtime, instrumentation, interop, and launcher JUnit tests validate
  the current implementation through internal Java APIs.
- JVM and native builds already run the same source corpus, but that corpus is not a portable TCK:
  it has no manifests, requirement traceability, protocol, structured results, or strict error-phase
  classification.

Do not mechanically promote a regression expectation into a normative TCK expectation. Review each
candidate against the current specification first.

### 2.3 Known documentation and interface hazards

- `docs/LANGUAGE_SPEC.md` declares itself a normative implementation baseline but contains no
  explicit language-specification version. The Maven version `1.0.0-SNAPSHOT` is not a language
  specification version and must not be treated as one.
- Normative requirements generally do not have stable identifiers.
- README text saying there is no implicit numeric widening is stale relative to the normative
  specification, which now defines a precise lossless widening relation. The TCK must follow the
  specification.
- The current launcher does not expose compile-only validation. Evaluating a valid source executes
  its top-level statements.
- Exit status `1` alone cannot distinguish compile error, language runtime error, adapter failure,
  launcher failure, or implementation crash.
- `test-corpus.sh` intentionally ignores stderr for successful cases and checks neither a diagnostic
  code nor a failure phase for most rejected programs.
- `println` uses the platform line separator. Exact-byte tests involving it must either declare the
  expected platform dependency or use an explicit, narrowly defined line-ending normalization.
- Include semantics contain host-dependent behavior (`~/`, current working directory for non-file
  roots, file access permissions, canonical paths). Tests must declare their fixtures and execution
  environment rather than relying on the repository checkout.

These are TCK design inputs. They are not permission to change language semantics.

## 3. Scope and Non-Goals

The implementation may add the TCK runner, schemas, corpus, fake adapters, current-distribution
adapters, documentation, tests, and necessary build/launcher integration. It must preserve all
existing tests and functionality.

The portable TCK must not:

- import Solvik compiler, AST, semantic, lowering, Truffle, or launcher Java classes;
- use the current implementation to compute expected results;
- require GraalVM, Java, Maven, or Solvik to run the runner's own self-tests;
- encode JVM object identity, Java exception text, Java regex extensions, host hash values, Truffle
  node shapes, or other implementation details as language requirements;
- introduce a SimpleLanguage compatibility mode or a second execution backend;
- weaken static typing or modify normative semantics to make the current implementation pass; or
- claim certification while required tests are skipped, unsupported, ambiguous, or not executed.

Python 3 standard-library facilities are preferred for the portable runner. If a third-party Python
package is proposed, justify why the same correctness and validation cannot reasonably be achieved
with the standard library and keep runner self-tests independently executable.

## 4. Required Deliverables

Create a self-contained `tck/` tree with, at minimum, these conceptual components:

```text
tck/
  README.md                     architecture, usage, and third-party guide
  IMPLEMENTATION_PLAN.md        factual progress and verification log
  VERSION                       TCK release identifier
  requirements/                 versioned machine-readable requirement inventory
  schemas/                      versioned JSON schemas
  protocol/                     adapter protocol specification and examples
  corpus/<spec-version>/        portable .sol sources, fixtures, and manifests
  profiles/                     required/optional profile definitions
  adapters/                     current JVM/native configurations and example adapter
  runner/                       implementation-independent runner
  tests/                        runner, schema, protocol, and fake-adapter self-tests
  reports/                      documentation or ignored output location, not committed results
```

Exact names may change when repository conventions justify it, but the boundaries must remain clear:
normative requirements, portable tests, runner infrastructure, implementation adapters, and
implementation-specific regressions are separate concerns.

Do not create placeholders that appear complete. A declared mandatory component must be functional,
tested, and documented in the same coherent slice.

## 5. Versioning Prerequisite

Before a compliance release can exist, establish explicit, independent identifiers for:

- the language specification revision;
- the TCK release;
- the test-manifest schema; and
- the adapter protocol.

The current repository has no language-specification version. Resolve that as an explicit project
versioning decision without calling the current language "1.0" merely because Maven uses
`1.0.0-SNAPSHOT`. Until that decision is made, development reports must say that certification is
blocked by an unversioned normative baseline. A source-control commit hash may identify audit input,
but it is not by itself a semantic version or a durable compatibility promise.

Document compatibility rules. Breaking schema or protocol changes require new versions. A runner
must reject unsupported versions rather than silently reinterpret them.

Released versioned inputs are immutable. Correcting a released requirement, manifest, schema, or
oracle requires a new TCK release and an auditable change record; never edit a released corpus in
place. Reports must record content digests for the requirements inventory, selected manifests, and
adapter configuration so a result can be tied to the exact inputs that produced it.

### 5.1 Compliance profiles and capabilities

Profiles cannot be an escape hatch from normative language behavior. Define one mandatory
full-language profile containing every portable, non-deferred requirement in the selected
specification version. Subprofiles may support development or constrained hosts, but passing one
must never be reported as full-language conformance.

Every requirement must belong to exactly one of these states for a specification version:

- required by the full-language profile;
- required by a named platform profile because the observable depends on a declared host facility;
- explicitly optional because the normative specification itself says it is optional; or
- untestable/ambiguous with a recorded reason that blocks affected full-profile conformance.

The current specification does not make an implemented feature optional merely because an IUT lacks
it. File inclusion, modules, compile-time validation, and runtime failures are normative features.
Adapter capabilities describe whether the IUT can be tested; they do not downgrade requirements.
Deferred features are excluded rather than advertised as optional capabilities.

Full-language conformance is the conjunction of the portable full-language profile and every
platform profile applicable to the reported host. If a normative requirement cannot be exercised on
that host, the report must name it and withhold full conformance rather than silently selecting a
smaller profile.

A profile definition must be versioned, list requirement IDs rather than directory globs, and have a
deterministic closure. Unknown capabilities, cyclic profile inclusion, an empty required profile, or
a requirement silently belonging to no profile are infrastructure errors.

## 6. Normative Requirement Inventory

Create a machine-readable inventory derived from every normative `must`, `must not`, defined
algorithm, required diagnostic, and observable runtime rule in `docs/LANGUAGE_SPEC.md`. Assign stable
identifiers without rewriting semantics. Each entry must include:

- stable requirement identifier;
- specification version and section anchor;
- concise normative summary;
- requirement kind (lexical, syntax, compile-time, runtime, library, module, or process boundary);
- compliance profile;
- whether portable automated testing is possible;
- associated test identifiers; and
- status and rationale when no automated test exists;
- oracle derivation notes identifying the exact normative text; and
- lifecycle state (`active`, `superseded`, or `withdrawn`) without reusing an identifier for a new
  meaning.

Automatically reject duplicate requirement identifiers, nonexistent references, incompatible spec
versions, required tests without requirements, and invalid coverage states. Report requirements with
no tests. A linked test means "addressed by a test," not "verified"; verification occurs only when
that test executes and passes against an implementation.

Use section-based identifiers only if their stability under editorial reordering is documented.
Prefer semantic slugs or allocated numbers whose meanings never change.

The inventory must distinguish specification-required diagnostic codes from codes that are merely
stable in the current Java implementation. An implementation enum entry, JUnit assertion, or error
message does not make a code normative. Conversely, every diagnostic code explicitly required by the
specification must be represented and tested where the triggering program is portable.

### 6.1 Oracle independence

Expected results must be authored from the normative requirement and independently reviewed. The
current IUT may be run to discover a discrepancy, but its output must never generate, approve, or
silently update an oracle. Store a review record for each migrated expectation containing the
requirement ID, reviewer rationale, and source-test digest.

Differential agreement, snapshots captured from the current implementation, and metamorphic tests
may supplement direct oracles; none may replace one when the specification defines an exact result.
If the specification does not determine an observable, the TCK must not choose one. If it should be
normative, resolve the specification first and issue a new specification/TCK version.

## 7. Portable Test Manifest Contract

Use UTF-8 `.sol` programs and JSON manifests validated against a versioned JSON Schema. Use JSON
Schema Draft 2020-12, publish its canonical `$id`, and pin it for the
schema version. The Python runner must enforce the same constraints even when no third-party schema
library is installed; self-tests must prove that its validation is not weaker than the published
schema for every keyword the TCK schemas use. Validate the entire selected corpus before invoking an
adapter.

Every test manifest must declare:

- unique stable test identifier;
- manifest schema version;
- applicable language-specification version;
- one or more normative requirement identifiers;
- category and compliance profile;
- required or optional status, where `optional` is legal only for a specification-defined optional
  requirement;
- source entry point and fixture root;
- expected outcome;
- compile and execution timeouts where applicable;
- stdin bytes or text where applicable;
- environment, locale, timezone, and working-directory requirements;
- explicitly permitted output normalization; and
- expected observable result.

JSON parsing must reject duplicate object keys, non-standard numbers (`NaN`, infinities), invalid
Unicode, and trailing data. Schemas must close objects with `additionalProperties: false` (or the
selected draft's equivalent), constrain integers and string lengths, and require an explicit value
for every semantic default. Do not let parser defaults silently change an old manifest's meaning.

Supported outcomes:

| Outcome | Required phase behavior |
|---|---|
| `SUCCESS` | Full static validation succeeds, execution terminates normally (including an expected explicit `exit(n)`), and all declared observables match |
| `COMPILE_SUCCESS` | Full-program static validation succeeds and no application code executes |
| `COMPILE_ERROR` | Compilation/static validation rejects the source before application code executes |
| `RUNTIME_ERROR` | Compilation succeeds, execution begins, and the specified language runtime failure occurs |

Expectations must support, where relevant:

- exact stdout bytes or an explicitly selected normalization;
- exact stderr only when the specification makes it normative;
- language exit status separately from adapter/process status;
- stable diagnostic code or diagnostic family;
- diagnostic source file and source span when normative;
- runtime error category and source location when normative; and
- required fixture files for multifile/include tests.

Human-readable diagnostic wording is not normative unless the specification explicitly says it is.
Do not normalize whitespace, line endings, paths, ordering, or messages by default.

An expected explicit `exit(n)` is a normal language completion with a declared language exit status,
even when `n` is nonzero. It is not a runtime error or adapter process failure. `SUCCESS` manifests
must declare the expected language exit status; omission must mean exactly `0`, never "ignore it."
`COMPILE_SUCCESS` and `COMPILE_ERROR` must forbid execution expectations. `RUNTIME_ERROR` must require
a structured runtime category and must forbid accepting normal exit status alone.

Define output normalization as a closed enum with precisely documented transformations. Each
transformation applies only to a named field and is recorded in the report. Arbitrary regex
replacement, trimming, path elision, unordered-line comparison, locale folding, and adapter-defined
normalization are forbidden because they can conceal semantic differences. Exact bytes remain the
default; binary data must be represented losslessly, such as by canonical base64.

Reject malformed JSON, unknown fields where the schema forbids them, duplicate IDs, unsupported
versions, unknown outcomes, impossible expectation combinations, missing sources, missing fixtures,
invalid requirement references, absolute fixture escapes, symlink/path traversal outside the test
root, and ambiguous expected results. A manifest defect is an infrastructure error, never an
implementation failure.

Cross-file validation must also prove that manifest profile/status declarations agree with the
requirements inventory and selected profile; a manifest cannot relabel a required requirement as
optional or place it only in a weaker profile.

Discovery and execution order must be deterministic.

## 8. Versioned Adapter Protocol

Define a language-independent, subprocess-based JSON protocol. Use one isolated adapter process per
test. The runner and adapter exchange one compact UTF-8 JSON object per line: the runner writes one
request to adapter stdin and requires exactly one response line on adapter stdout before sending the
next request. Literal line breaks inside JSON strings are escaped. After the final response, the
runner closes stdin and requires stdout EOF plus adapter exit `0`. Adapter stderr is non-protocol
diagnostic logging captured by the runner. Guest stdout and stderr are encoded fields inside a
response, never inherited process streams, so guest output cannot forge protocol data. Commands must
be argument arrays, not shell command strings.

Every request and response must carry the protocol version, request ID, operation name, and schema
version. The response request ID and operation must match exactly. Reject duplicate JSON keys,
blank protocol lines, multiple JSON values on a line, unsolicited responses, invalid encoding,
unsupported versions, unknown fields, oversized messages, and premature process exit.

The protocol plus runner-controlled process integration must provide:

- implementation name and version;
- supported specification versions and profiles;
- supported optional capabilities;
- protocol version;
- complete static validation / compile-only operation;
- compilation and artifact identity where the implementation produces artifacts;
- execution of the exact compiled artifact;
- guest stdout and stderr as bytes or losslessly encoded data;
- structured compile diagnostics;
- structured language runtime failures;
- language exit status;
- adapter process status captured independently of the response; and
- runner-measured timing and timeout classification.

The minimum operations are:

1. `describe`: return immutable implementation identity, supported specification/profile versions,
   capabilities, and limits;
2. `compile`: consume a staged source/fixture tree, perform complete static validation without
   application execution, and return either structured compile diagnostics or an artifact handle;
3. `execute`: execute the artifact handle returned by the successful `compile` request and return
   structured guest observables.

`compile` and `execute` occur in the same adapter session. This allows an interpreter or JIT to keep
validated/compiled state in memory without serialization or recompilation while still requiring a
real phase boundary. An AOT adapter may materialize files in the workspace. The adapter must not
create persistent test artifacts outside that workspace.

The runner owns a fresh workspace per test and passes its canonical path explicitly. An artifact
handle is an opaque protocol token scoped to that workspace and adapter identity, not a runner-trusted
path. Use SHA-256 over a specified canonical tree serialization for every input/artifact digest. The
runner records the staged input-tree digest after staging and verifies it again before execution. For
materialized output, `compile` must return a sorted artifact manifest of workspace-relative regular
files and their digests; the runner verifies it before and after execution. Keep immutable compiled
artifacts and mutable execution scratch in separate workspace subtrees. The adapter must bind every
artifact handle to the input digest and compilation request. Source or fixture mutation, an artifact
outside the workspace, an undeclared artifact file, a reused handle from another test, or changed
artifact content/identity is an infrastructure/protocol error.
A rejected compilation must return no artifact handle and leave no executable artifact.

The result model must distinguish at least:

- accepted compilation;
- rejected compilation;
- language runtime failure;
- normal language exit, including explicit `exit(n)`;
- caught implementation crash/internal failure, distinct from a language error;
- adapter/protocol error;
- implementation crash or internal error;
- operating-system process failure; and
- compilation or execution timeout.

A compile error may never satisfy `RUNTIME_ERROR`. A crash, timeout, malformed protocol message, or
OS failure may never satisfy a language error expectation.

Adapter process exit `0` means only that the protocol session ended cleanly; it does not mean the
guest program passed. Any nonzero adapter process exit is an infrastructure error even if an earlier
response claimed a language result. Signals, native faults, host stack traces, and resource
termination are implementation/adapter failures, never guest compile or runtime errors. The guest
language exit status exists only inside a valid `execute` response.

When an adapter supervises a separate IUT process, it must report a caught IUT crash/internal failure
as a structured implementation failure, which produces test `FAIL`. Failure to launch the adapter,
adapter death, or loss of the protocol channel produces `INFRASTRUCTURE_ERROR` because no trustworthy
IUT result exists. The report must preserve this distinction even though neither outcome can pass a
conformance test.

For ahead-of-time compilers, compile once and execute the returned artifact without silent
recompilation. For interpreters and JIT implementations, complete static validation must finish
before execution. If an implementation cannot provide a genuine compile-only boundary, it must
declare the capability unavailable and cannot pass a profile that requires it.

`execute` is legal only after a successful `compile` in the same adapter session. The runner must reject
an adapter that returns execution data from `compile`, changes its identity/capabilities during a
run, executes after rejected compilation, reports a compile diagnostic from `execute`, or accepts an
unknown/expired artifact handle. No mutable IUT execution context may be shared across tests unless
the specification explicitly requires shared process state; exact process topology is otherwise an
implementation choice. Adapter caching must be semantically invisible and keyed by content plus all
compile-affecting options.

Document numeric units and hard limits for request/response bytes, captured output, source trees,
diagnostic counts, artifacts, compilation/execution duration, and cancellation grace. Exceeding a
limit is an infrastructure result, not output truncation followed by comparison. Also document
encoding, message framing, source-location units, working directories, environment inheritance,
artifact lifetime, cancellation, and forward/backward compatibility.

Source locations in the protocol must use one portable convention (UTF-8 byte offsets, or Unicode
line/column pairs with a defined scalar/tab/newline model) and state whether intervals are half-open.
Adapters translate their internal representation to that convention. Never compare JVM UTF-16
offsets directly with an implementation using Unicode scalar offsets.

### 8.1 Outcome-matching state machine

The runner, not the adapter, applies the manifest oracle using this exact phase order:

1. validate all TCK inputs and use a describe-only preflight session to select compatible tests;
2. start the per-test session, obtain `describe` metadata again, and require it to match the frozen
   preflight identity, versions, capabilities, and limits;
3. stage and hash the fixture tree;
4. invoke `compile` under the compilation timeout;
5. for `COMPILE_SUCCESS` or `COMPILE_ERROR`, stop without `execute` and compare the compile result;
6. for `SUCCESS` or `RUNTIME_ERROR`, require successful compilation, then invoke `execute` under a
   separate execution timeout; and
7. compare the structured execution result and declared observables.

Any invalid transition yields `INFRASTRUCTURE_ERROR`. `COMPILE_ERROR` passes only on a structured
source rejection from `compile`; `RUNTIME_ERROR` passes only on a structured language failure from
`execute`; `SUCCESS` passes only on structured normal completion; and `COMPILE_SUCCESS` passes only
on successful compile-only validation with zero application observables. Extra phases or
observables are failures, not ignored data. A structured implementation crash/internal failure is
`FAIL`; loss or corruption of the adapter session is `INFRASTRUCTURE_ERROR`.

## 9. Adapters for the Current Distributions

Provide separate adapter configurations for:

- `standalone/target/solvik`; and
- `standalone/target/solvik-native`.

Both must consume the identical portable corpus and expectations. Share adapter code where practical;
do not fork goldens to hide JVM/native differences.

Adapter configuration must identify executables by canonical path and capture an implementation
fingerprint before testing (at minimum executable digest plus version output or build metadata).
Changing the executable, adapter configuration, or declared implementation identity during a run
invalidates the report. The JVM adapter and native adapter are separate IUT identities even when
built from the same source revision.

The existing launchers can execute sources and preserve guest stdout, stderr, and exit status, but
they cannot currently provide genuine compile-only validation or structured phase classification.
Before implementing adapters, design the smallest clean addition to the compiler/launcher boundary
that works in both distributions and cannot run top-level application code in compile-only mode.
Do not duplicate parsing or semantic analysis in the TCK adapter.

Any launcher/API change is implementation work and must receive its own positive and negative tests,
including proof that compile-only validation does not execute `println`, mutation, `exit`, static
initializers, constructors, or other application behavior.

Do not classify current stderr text with regular expressions as the permanent adapter protocol.
Stable `SOLV-*` codes and source locations must cross the implementation boundary as structured
fields. Human-readable stderr may be retained for users, but parsing it is at most a temporary audit
tool and cannot support a conformance result.

## 10. Corpus Strategy

Keep `language/tests/` as the existing implementation regression and example corpus unless a later,
explicit migration demonstrates that moving files preserves all build behavior. Build the normative
portable corpus under `tck/` and avoid source duplication only where a stable, implementation-neutral
reference is possible.

For every existing candidate program:

1. identify the exact normative requirement;
2. classify it as portable conformance, implementation regression, performance/coverage, or
   implementation integration;
3. independently review the expected result against the specification;
4. add a portable manifest only when the oracle is normative;
5. preserve the original regression test unless removal is separately justified; and
6. record the migration/link in a machine-readable inventory.

The initial audit must explicitly classify all 21 examples, 76 regression programs, 12 diagnostic
fixtures, and relevant runtime-failure JUnit cases. Generated deep-nesting regressions and tests of
compiler resource limits are normally implementation robustness tests, not language conformance
requirements, unless the specification defines the limit.

Prefer one primary obligation per test. A test may cite multiple requirements only when their
interaction is the purpose of the test, and its failure report must identify the combined oracle.
Avoid large showcase programs as sole coverage for individual rules. Negative programs must include
sentinel application behavior that would be observable if an invalid program were executed, while
the compile-only protocol independently proves that execution did not occur.

Portable sources must not inspect adapter variables, implementation names, repository paths, host
language classes, wall-clock time, random state, process IDs, network state, or unspecified
iteration/hash order. A platform-profile test may observe only the host property that its requirement
and manifest explicitly declare.

## 11. Required Conformance Coverage

Cover every current non-deferred normative requirement with focused positive, negative, boundary,
evaluation-order, and feature-interaction tests where meaningful. At minimum, organize coverage for:

1. Lexing and syntax: identifiers, keywords, comments, literals, escapes, raw-string delimiters,
   lexical errors, semicolon insertion, precedence, associativity, and permitted trailing commas.
2. Types and names: lexical scope, shadowing, declaration order, definite initialization, nominal
   assignability, `Any`, `Nothing`, `Unit`, nullability, flow invalidation, casts, and type tests.
3. Numerics: every type, literal boundary, lossless widening edge, forbidden conversion, mixed
   operator least-common-widening rules, checked integral overflow, truncating division, division by
   zero, IEEE NaN/infinity/signed-zero behavior, and constant versus runtime conversion failure.
4. Evaluation: left-to-right and exactly-once rules, short circuiting, `..`, calls, assignments as
   statements, block/`if`/`switch` expression results, joins, and abrupt completion.
5. Object model: construction and initialization, the `mutable`/`abstract`/`override` rules, single inheritance,
   `super`, property initialization, virtual dispatch, interfaces, default conflicts, delegation,
   static members, initialization order, cycles, and one-time lazy initialization.
6. Equality and hashing: comparability, null cases, scalar and IEEE rules, enum recursion, class
   dispatch, reference-backed built-ins, `equals`/`hashCode` declarations and pairing, `super`
   behavior, identity domains, collection key/element semantics, and switch matching.
7. Generics and collections: arity, inference, invariance, erasure restrictions, every collection
   constructor/member, ordering and duplicate behavior, and defined bounds/collection failures.
8. Enums, abstract classes, and match: construction, payload typing, extension across file
   boundaries, ordering, reachability, binding, wildcard coverage, exhaustiveness, and result joining.
9. Control flow: statement/expression `switch`, non-fallthrough, constant and regex cases, loops,
   ranges, `break`, `continue`, `return`, and implicit-main behavior.
10. Regex: the complete portable dialect, rejected extensions, matching/search/replacement,
    captures and offsets, empty matches, construction, equality/hash behavior, and regex switch cases.
11. Files and namespaces: include path rules, relative bases, raw paths, canonical identity,
    duplicate inclusion, diamonds, cycles, expansion order, modules, aliases, namespace lookup,
    physical source identity, and diagnostics across files.
12. Exceptions: nominal exception types, construction/message rules, `throw`, catch type/order/scope,
    cross-call propagation, every `finally` exit path, replacement semantics, return analysis, and
    uncaught program-boundary behavior.
13. `Result`: structural recognition, every synthesized operation, wrong-variant faults,
    must-consume checking, safe access, evaluation order, propagation typing, early return, and
    interaction with `finally`.
14. Process behavior: no-entry program, normal completion, `print`/`println`, `exit`, compile errors
    before execution, language runtime failures, and source/fixture resolution.

Do not add tests for deferred behavior. When a requirement cannot be tested portably, record why and
place it in a clearly named optional or platform-specific profile; do not silently omit it.

## 12. Deterministic and Safe Execution

Run each test in a fresh temporary directory with a controlled fixture tree, working directory,
stdin, environment, locale, and timezone. Apply independent compilation and execution timeouts.
Capture stdout, stderr, language status, adapter status, and process status separately. Clean up
artifacts and terminate subprocess trees where supported.

Start from an environment allowlist, not the runner's full inherited environment. Pass only values
required to locate/execute the configured adapter plus manifest-declared deterministic values.
Explicitly control or remove variables affecting locale, timezone, home expansion, temporary paths,
language options, classpaths, module paths, and implementation caches. Reports must record the names
of passed variables but redact secrets and sensitive values.

Do not depend on filesystem iteration order or repository-relative paths. Do not pass manifests
through a shell. Validate every copied path before use and define symlink handling. A temporary
directory provides isolation for reproducibility; it is not a security sandbox. Clearly warn users
that third-party implementations and adapters must be trusted or run in an external sandbox.

Resolve and validate every fixture component before copying it. Reject device files, FIFOs, sockets,
hard links where identity matters, symlinks that escape the fixture root, Unicode-normalization name
collisions, case-folding collisions on case-insensitive hosts, and archive entries with absolute or
parent paths. Revalidate containment after creation to reduce time-of-check/time-of-use exposure.

Read captured streams concurrently to prevent pipe deadlock and enforce byte limits while reading.
On timeout, terminate the complete adapter/IUT process tree, wait a bounded grace period, force
termination where supported, drain/close handles, and report any surviving process as an
infrastructure error. Cleanup must run after pass, failure, timeout, and interruption without
deleting paths outside the runner-created workspace.

## 13. Reporting and Certification Rules

Produce deterministic human-readable terminal output and a versioned JSON report. JUnit XML is
optional. Reports must include:

- TCK, specification, schema, and protocol versions;
- implementation identity and declared capabilities;
- platform and execution timestamp;
- selected profile and filters;
- counts for `PASS`, `FAIL`, `NOT_RUN`, and `INFRASTRUCTURE_ERROR`;
- unsupported required and optional capabilities;
- per-test phase results, observables, diagnostics, durations, and reproduction command; and
- requirement coverage, untested requirements, ambiguities, and infrastructure failures.

JSON reports must use a published closed schema, stable test ordering, UTC RFC 3339 timestamps, and
explicit units. Store reproduction invocations as argument arrays plus a redacted environment map;
a human-readable shell rendering is informational only and must be safely quoted. Do not embed
secrets, unrestricted host environment data, or unbounded guest output in reports. Record full-output
digests when configured report-size limits require external attachments; truncation alone can never
be used for oracle comparison.

A required test that is not executed is never a pass. An infrastructure error is not automatically
an implementation conformance failure, but it prevents certification. A required unsupported
capability prevents full-profile certification. Do not present a percentage as certification.

Use these runner exit codes: `0` for a fully executed, passing requested verification; `1` for one or
more conformance failures with no infrastructure error; and `2` for invalid invocation, invalid TCK
input, protocol failure, unsupported required capability, `NOT_RUN`, or any other infrastructure
error. Infrastructure status takes precedence over conformance status when both occur. A filtered
run may return `0` for the requested tests but must set full-profile conformance to `NOT_EVALUATED`;
it cannot issue a full-profile result.

A full-profile conformance result is `PASS` only when the exact full profile was selected, every
required test executed once and passed, every required capability was available, requirement
coverage validation succeeded, and no ambiguity or infrastructure error affects that profile.

Do not automatically retry a failed test and replace it with a later pass. An explicitly requested
repeat or flakiness investigation must retain every attempt, mark the test non-passing if outcomes
differ, and cannot produce a full-profile `PASS`. Randomized execution order may be a diagnostic mode
only; normative reporting uses the deterministic order.

## 14. Runner Self-Tests

Runner self-tests must use only Python and fake subprocess adapters; they must run without Solvik,
Java, GraalVM, Maven, or built distributions. Test at least:

- valid success, compile success, compile error, and runtime error results;
- wrong stdout/stderr/status/code/location/error category;
- compile error misreported as runtime error and the inverse;
- crash, OS failure, invalid JSON, missing/unknown fields, duplicate messages, and wrong protocol
  version;
- duplicate JSON keys, trailing data, invalid UTF-8, oversized responses, mismatched request IDs,
  nonzero adapter exit with an otherwise valid-looking response, and guest output attempting to
  inject protocol JSON;
- compile and execution timeouts plus subprocess-tree cleanup;
- execution attempted after failed compilation;
- application output produced during compile-only validation;
- missing or changed compiled artifacts;
- false capability declarations;
- changed implementation identity, mutated sources/artifacts, cross-test artifact handles, and
  execute-before-compile state violations;
- all manifest rejection cases;
- duplicate test and requirement identifiers;
- missing normative references and incompatible specification versions;
- deterministic discovery, filtering, reports, reproduction commands, and temporary isolation;
- mandatory `NOT_RUN` preventing certification;
- path traversal, escaping symlinks, unsafe cleanup targets, output/resource-limit enforcement, and
  environment redaction; and
- deliberate corruption of expected output and adapter behavior.

Fake adapters must prove the runner rejects defective implementations. A happy-path-only runner test
suite is insufficient.

Tests must also prove runner exit-code precedence, that a filtered pass is not full conformance, that
optional reference-schema validation agrees with the dependency-free validator, and that test order
or parallel scheduling cannot change the report's semantic content. If parallel execution is added,
run the same fake corpus serially and in parallel and compare normalized reports excluding only
timestamps and durations.

## 15. Differential Mode

Provide a separate mode that runs identical portable programs through two or more adapters and
reports disagreements in compilation acceptance, diagnostics made normative by the specification,
stdout, stderr, exit behavior, and runtime failures. Normalize only differences explicitly allowed
by the manifest/protocol.

Add a JVM-versus-native smoke run after both distributions exist. Differential results must never
update expected results automatically or override the normative oracle.

## 16. Build Integration

Preserve the existing wrapper contract:

- `./build.sh` performs a clean JVM package and runs the existing corpus through the JVM launcher;
- `./build-native.sh` performs a clean native package and runs the existing corpus through the native
  binary; and
- `./build-all.sh` remains the final repository quality gate and runs both in order.

Add TCK stages without making either distribution use a different corpus or recompiling needlessly.
The runner self-tests and manifest validation must run independently and early. Distribution
conformance runs must reuse the freshly built artifact for their respective wrapper. The final
integration must make a required TCK failure visible as a nonzero build/CI result without hiding the
existing JUnit, JaCoCo, packaging, or regression-corpus failures.

The portable runner and its self-tests must not access the network. A conformance run must not fetch
schemas, manifests, dependencies, or expected results; all versioned inputs are local and digest
verified. Build integration must write generated reports only to ignored build-output directories and
must not update sources, manifests, or goldens.

Document commands for runner self-tests, schema/corpus validation, requirement coverage, test and
profile filtering, JVM/native/third-party adapters, differential testing, and report generation.

## 17. Incremental Implementation Order

Maintain `tck/IMPLEMENTATION_PLAN.md` during implementation. Record completed slices, files changed,
commands actually executed, results, pending work, architectural decisions, ambiguities, and known
implementation defects.

Implement in dependency order:

1. Re-audit the repository and freeze the exact baseline.
2. Resolve specification version identification without changing semantics.
3. Build and validate the normative requirement inventory.
4. Define manifest schema, profiles, and fixtures model.
5. Define the adapter protocol and result taxonomy.
6. Implement manifest/requirement validation and runner self-tests with fake adapters.
7. Implement the runner, deterministic reporting, filtering, timeouts, and isolation.
8. Add the smallest genuine compile-only/structured boundary needed by current distributions.
9. Implement JVM and native adapters against that boundary.
10. Review and migrate portable cases from the existing corpus and JUnit runtime tests.
11. Add missing conformance cases by specification section.
12. Add differential mode and third-party example adapter.
13. Integrate with build wrappers and CI.
14. Complete documentation, traceability, security review, and final audit.

Each slice must be coherent and verified before the next depends on it. Do not build corpus scale on
an untested protocol or runner.

## 18. Verification Sequence

For the completed implementation, execute and record results in this order:

1. runner self-tests without Solvik installed or invoked;
2. schemas, manifests, requirements, profiles, and traceability validation;
3. fake-adapter negative/mutation tests;
4. complete portable corpus through the JVM adapter;
5. complete portable corpus through the native adapter;
6. JVM/native differential verification;
7. all existing focused tests affected by launcher/compiler integration;
8. `./build.sh`;
9. `./build-native.sh`; and
10. `./build-all.sh` as the final quality gate.

Never claim a command passed unless it executed successfully. If a dependency is unavailable,
record the exact blocker and leave the corresponding verification incomplete.

## 19. Final Audit and Acceptance Criteria

Before completion, inspect the final diff and independently review for false positives, oracle
circularity, compile/runtime confusion, ignored tests, unsafe path handling, subprocess leaks,
nondeterminism, implementation coupling, invalid version claims, and missing negative tests.

The TCK is acceptable only when all of the following are true:

1. The runner and portable corpus do not import or depend on Solvik implementation internals.
2. Runner self-tests work without Java, GraalVM, Maven, or Solvik.
3. Every manifest and protocol message is versioned and schema-validated.
4. Every required portable test traces to a valid versioned normative requirement.
5. Compile-only validation is genuine and demonstrably executes no application code.
6. Compile failures, runtime failures, crashes, OS failures, protocol failures, and timeouts cannot
   satisfy one another's expectations.
7. Defective fake adapters are rejected by automated tests.
8. JVM and native adapters run the identical corpus and expectations.
9. Required unexecuted tests and unsupported required capabilities prevent certification.
10. Existing examples, regression tests, diagnostics, coverage gates, and build behavior remain
    operational.
11. A third party can implement the documented protocol without Solvik Java or Truffle classes.
12. Requirement gaps and specification ambiguities are visible and prevent affected full-profile
    certification.
13. `./build-all.sh` passes after all applicable TCK verification has passed.
14. No mandatory component is an empty stub, placeholder, or documentation-only facade.
15. The final report lists actual commands, measured results, covered and uncovered requirements,
    implementation defects, limitations, and unresolved ambiguities.
16. Protocol framing, request correlation, adapter-process exit behavior, artifact binding, and the
    outcome state machine are covered by adversarial self-tests.
17. JSON duplicate keys, unknown fields, invalid encodings, unsafe fixture paths, escaping links,
    environment leakage, output exhaustion, timeouts, and process cleanup fail closed.
18. Released TCK inputs are immutable and every report is bound to their digests and an IUT
    fingerprint.
19. Only specification-defined optionality affects full conformance; implementation capability
    declarations cannot waive normative requirements.
20. Oracle review records demonstrate that expected results were not generated from the IUT.

Passing all implemented tests does not establish 100% language correctness if normative
requirements remain untested or ambiguous. Completion requires evidence against this entire brief,
not merely a green test count.
