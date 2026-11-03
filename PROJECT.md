# PROJECT — Solvik

Project-specific instructions. [AGENTS.md](AGENTS.md) defines how work is done in this
repository; this file defines what the project is, which sources are authoritative, and what
to optimize for when goals compete.

## What this project is

Solvik is a small, statically typed language that transpiles to standalone Java 25 source.
The compiler is a Java 25 application built with the Maven wrapper in this repository.
Generated programs depend only on the JDK and the Java libraries they bind explicitly:
there is no Solvik runtime jar, no interpreter loop, and no reflective dispatch.

The language supports signature-based overloading, nominal traits with stateless defaults,
native Java bindings, modules that map to Java packages, and a packaged standard library.
`example.sol` and `include.sol` exercise the implemented surface; the packaged library lives
under `src/main/resources/solvik/`.

## Priorities

These priorities are ordered. When they compete, the lower number wins:

1. **Correctness and consistency of the Solvik language.**
2. **Performance of the Solvik language and its generated Java code.**
3. **Clean transpiled Java output.**

A lower-priority improvement never justifies a higher-priority regression. Do not trade
observable behavior or language consistency for speed, and do not trade runtime cost for
prettier output.

### 1. Correctness and consistency (highest)

- `LANGUAGE.md` is the language contract. Where code and documentation disagree, the code
  wins and the documentation is the bug: fix the drift rather than letting either side stand.
  A deliberate language change updates `LANGUAGE.md` and the focused regression together.
- A program means the same thing from the source AST and its generated Java, whether it runs
  from the cache or from a fresh compile. The backend has one lowering path and one renderer.
- Compile-time rejection, stable diagnostics (`SOL-L###`, `SOL-P###`, `SOL-T###`, `SOL-I###`,
  `SOL-R###`), and runtime abort behavior must match the documented rules. No silent
  coercion, no undocumented acceptance, and no documented rule left unimplemented.
- Correctness includes preserved contracts: null and untyped-slot behavior, numeric promotion
  and overflow, array bounds, checked Java exceptions surfacing as `SOL-R011`, cache-key
  completeness, and the reserved `solvik` root namespace.
- Cover negative, boundary, null, overflow, and failure paths — not just the happy path —
  before calling a feature complete.

### 2. Performance of the language and generated code (middle)

- Generated programs run directly on the JDK. Do not add a Solvik runtime jar, an interpreter
  loop, reflection-based dispatch, or per-operation helper calls where direct Java lowering
  has the same semantics.
- Prefer the cheapest lowering that preserves Solvik semantics: direct field access,
  `List.get`/`List.set` for arrays, unboxed primitive arithmetic where the checker has fixed
  the carrier, no avoidable boxing or allocation, and temporaries only when evaluation order
  requires them.
- Emit support code only for what the program actually reaches. Helper blocks are embedded
  source selected by recorded uses, not an installed runtime.
- Performance work needs evidence: inspect the emitted Java, use output-shape tests or
  measurements, and state the observed effect. Do not claim a speedup that was not measured.
- Compiler and launcher turnaround (caching, incremental rebuild) matters for iteration, but
  it never outranks correctness or cache-key safety.

### 3. Clean transpiled Java output (lowest)

- Generated Java is a product: readable, deterministic, idiomatic, and warning-free under the
  project's `-Xlint:all -Werror` gate. Every emitted file must compile standalone with
  `javac --release 25` and the supplied classpath only.
- Keep output minimal: no unused support members, imports, wrappers, casts, or dead
  scaffolding; stable generated names; no redundant `toStringOf`-style layers. Support code
  is the transitive closure of what is used.
- `GeneratedOutputOptimizationTest` pins the emitted shape. Change it deliberately when the
  shape genuinely improves, never to accommodate accidental churn.
- Formatting and cleanup changes must be behavior-preserving and must not add runtime cost.
  If a cleaner form is slower or less faithful to the semantics, keep the current form and
  record why.

### Resolving conflicts

- A correctness fix that costs performance is acceptable; report the cost and note any
  follow-up rather than weakening the fix.
- A performance change that alters observable semantics is rejected. If the language itself
  should change, that is a contract decision requiring explicit authorization and a spec
  update first.
- An output-cleanliness change that would change behavior or slow the generated code is not
  made. Clean output is valuable, but it is the last tiebreaker.
- If a genuine conflict cannot be resolved within the requested scope, surface the trade-off
  and the options instead of silently choosing one.

## Language corpus (`example.sol`)

`example.sol` must use and exercise **all** Solvik language constructs, syntax, and documented
features — not a convenient subset. It is the executable inventory of the language surface:
if Solvik can express it, `example.sol` (with `include.sol` for included declarations)
demonstrates it, and `./solvik run example.sol` must run it end to end.

- Adding or changing a language construct, syntax form, declaration kind, operator, literal
  form, standard-library type, or binding shape updates the corpus in the same change.
- Prefer live, observable use over dead declarations: the construct should execute and its
  effect should be visible in the program's output or behavior where the language allows it.
- `include.sol` exists to exercise includes, aliases, and cross-file declarations; it is not a place to
  park unused constructs.
- Negative and diagnostic cases stay in the test suite; the corpus demonstrates valid language
  surface, while tests prove rejection and failure behavior.

## Authoritative sources

| Source | Authority |
| --- | --- |
| `LANGUAGE.md` | Current language behavior; code wins where they disagree |
| `JAVA_BACKEND.md` | Java representation, lowering, and code-generation contract |
| `README.md` | Build, run, CLI, caching, and repository overview |
| `src/main/resources/solvik/**/*.sol` | Packaged standard library, loaded into every program |
| `example.sol`, `include.sol` | Language corpus: must exercise the complete implemented surface |
| `plans/*.design.md`, `plans/*.impl.md` | Decision and execution records for past features |
| `AGENTS.md`, `DESIGN.md`, `IMPLEMENT.md` | Engineering workflow; not project behavior |

Planning documents record decisions; they do not prove the implementation still matches them.
Verify important claims against current source and tests.

## Build and verification

- JDK 25 is required. `./mvnw verify` is the definition of a healthy build and runs in CI
  (`.github/workflows/build.yml`).
- Gates: `-Xlint:all -Werror` for main and test sources, Surefire unit tests, Failsafe
  packaged-JAR integration tests, and JaCoCo thresholds of 85% instruction and 80% branch
  coverage.
- Focused checks come first, then the broader gate. The language-consistency suite compiles
  and runs generated Java with an empty dependency classpath.
- `./solvik run example.sol` is the end-to-end smoke test; `--keep-sources` retains the generated
  `.java` files in the cache for inspection.
- Java sources use four-space indentation, LF line endings, and an ASCII-sorted import block
  after any `import static` lines (`.editorconfig`).
- Behavior changes update the authoritative documentation and add the focused regression that
  proves them; language-surface changes also update `example.sol` per the corpus rule above.
  Do not edit `target/` output by hand.

## Contracts to preserve

- No Solvik runtime jar. A generated program needs the JDK plus only the JARs supplied with
  `--cp`, and resolves native bindings at compile time.
- Packaged `.sol` resources are the standard-library registration mechanism: adding a file
  under `src/main/resources` is the only step, and packaged source text is part of the
  compilation-cache key.
- The root `solvik` module is compiler-owned; only packaged library resources may declare it.
- Diagnostics keep their stable codes and actionable source/caret rendering. CLI exit codes
  stay `0` success, `1` compile error or runtime abort, `2` bad usage.
- Source-level immutability, `pub`, and `mut` are Solvik semantic rules even where emitted
  Java does not yet enforce them at the Java boundary.

## Change control

- Substantial changes, contract/interface/persistent-data changes, and language-surface
  changes use a design plan and an implementation plan under `plans/` per `AGENTS.md`.
  A small, well-bounded fix may be implemented directly when requested.
- Preserve unrelated user edits and untracked work. Do not stage, commit, push, install, or
  deploy unless the user asks.
