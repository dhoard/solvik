# TCK Implementation Plan & Verification Log

Factual record of work, commands actually executed, results, pending work,
decisions, ambiguities, and known implementation limits. Sections cite `../TCK.md`.

Status: **portable runner core, the genuine compile-only/structured boundary, and
the JVM + native distribution adapters are complete and validated end-to-end; the
conformance *runs* are wired into the build gate.** Corpus expansion (§11 cases) and
differential mode remain. Certification is currently withheld (by design) because the
normative baseline is pre-1.0 and full-language requirement coverage is incomplete —
see "Certification status".

## Verification actually executed

All results below were observed, not inferred. Runner self-tests run with **only
Python 3.14**; Solvik/Java/GraalVM/Maven are never invoked by them.

| Command | Result |
|---|---|
| `./tck/tck-check.sh` | exit 0 (validate + all self-tests) |
| `python3 tck/tests/test_strict_json_and_schema.py` | 196/196 passed (incl. reference-validator parity vs `jsonschema` Draft 2020-12; 0 skipped) |
| `python3 tck/tests/test_manifest_and_inventory.py` | 41/41 passed (incl. requirement/test linkage, whose error path is exercised by injected defects) |
| `python3 tck/tests/test_oracle_quotes.py` | 883/883 passed (every quoted normative passage is grepped out of `LANGUAGE_SPEC.md`, in the inventory *and* in corpus oracle comments; section citations are resolved against real headings; corpus/structural invariants including exact-oracle independence between SUCCESS tests) |
| `python3 tck/tests/test_protocol.py` | 22/22 passed |
| `python3 tck/tests/test_preflight_and_determinism.py` | 19/19 passed |
| `python3 tck/tests/test_integration_fake.py` | 62/62 passed |
| `python3 tck/tests/test_solvik_adapter.py` | 69/69 passed (adapter translation + protocol state machine via a fake launcher; no GraalVM) |
| `python3 tck/tests/test_differential.py` | 90/90 passed (comparator axes, refusal-as-absence, declaration-gated comparison, the legality-only marker, evidence digests, CRLF transform exercised under a simulated CRLF host, vacuity exit code, plus an end-to-end `tck_cli differential` run over behavior-scripted fake adapters; every guard exercised in both directions and proven falsifiable by injected defects). Comparability is decided at **two levels**, which one collapsed question once conflated: a *position on legality* (`COMPILE_ACCEPTED` or `COMPILE_REJECTED`, which a compile-only adapter holds because accepting a program is a claim about it) makes the acceptance axis comparable, while a *full language result* (a position plus an executed program) is what additionally makes stdout/stderr/exit/runtime-category comparable. Collapsing them let a partner that accepted a program the implementation rejected be classified as having observed nothing, reporting `disagreements=0` and exit 0 over the most fundamental divergence available; a refusal holds no position, and neither does a crash, whose recorded compile status may be well-formed but was produced by a process that then died) |
| `python3 tck/tests/test_reference_adapter.py` | 39/39 passed (drives the **real** `Runner` over the **real** corpus with the independent reference front end: every executed program reproduces its oracle byte for byte, the answered set is an explicit allowlist, and every unimplemented program is an honest refusal that the runner can never record as a language result. Two further guards hold the front end to the specification rather than to convenience: its refused-name set must still equal the *mechanical* extraction of backticked bare lowercase words from LANGUAGE_SPEC.md -- section 1 reserves keywords but names no list, so curating that set would have the "independent" partner making language decisions it has no authority to make -- and a decimal literal outside the signed 32-bit range must be refused rather than accepted, because section 1 forbids it yet names no diagnostic to report. Both are falsifiable: dropping one word from the set as "obviously prose", a word that changes no program's behavior and no other guard observes, is caught by exactly one check) |
| `python3 tck/runner/tck_cli.py validate` | OK: **124 requirements**, 1 profile, **184 manifests**; coverage **124/124 active**; states that `2026.09-draft` is a draft/non-certifiable baseline so aggregate certification is withheld |
| `python3 tck/runner/tck_cli.py differential --left-config <jvm> --right-config <native>` | **exit 0**, `disagreements=0 inconclusive=0 compared=184 unconstrained=109` -- the two shipped distributions agree on every normative observable across the entire portable corpus; the unconstrained entries are launcher `stderr` wording the oracle does not declare, reported with per-side digests but never counted |
| `python3 tck/runner/tck_cli.py differential --left-config <jvm> --right-config <reference-subset>` | exit 0, `disagreements=0 inconclusive=173 compared=11 unconstrained=10` over 184 tests. The deliberately-incomplete adapter refuses 173 programs it does not implement, reported as an absence of observation rather than 173 fabricated disagreements; on the 11 programs both sides actually judge -- three raw-string/escape and `$` semantics, boolean/null rendering, three include-resolution rejections, and three section 1 lexical programs (the identifier character class, a `//` line comment, an in-range decimal literal) -- they agree on every oracle-declared observable, including byte-exact stdout with embedded NULs and escapes |

Total: **1421 self-test assertions** across 9 modules, all Python-only. The
`test_integration_fake.py` suite additionally drives the aggregate conformance
decision at the pure `build_report` layer (certifiable baseline -> `PASS`; draft
baseline -> withheld; genuine failure -> `FAIL`; `NOT_RUN` -> withheld) so the `PASS`
path is a live, tested branch rather than a facade.

`test_differential.py` exists because the comparator has no oracle to lean on, and the two
conditions that matter most -- an adapter honestly declining an unsupported program, and two
implementations differing only in non-normative wording -- arrive as *noise* in a real
end-to-end run and cannot be isolated there. Both were live defects, found only because the
guard was built before the evidence: refusals were counted as 110 fabricated disagreements,
and GraalVM launcher warnings on stderr were counted as conformance-relevant divergence. Each
guard is asserted in both directions (fires when it should; silent when it should not), and
re-injecting either original defect fails 4 and 5 assertions respectively.

`run_selftests.py` parses the `N/N passed` tally each module prints and **fails the build** if any count stated in this file -- per module or the aggregate assertion total -- differs from what was actually executed (verified: stating 30/30 for a module that runs 22, or 900 for 842, exits 1; the clean state exits 0). The numbers above are therefore enforced, not aspirational; `./tck/tck-check.sh` remains the authoritative gate overall.

### Distribution conformance runs (require a built launcher; run inside `./build.sh` / `./build-native.sh`)

These exercise the **real** JVM/native distributions through the subprocess adapter
protocol against the seed corpus. They are invoked by the build gate, not by the
pure-Python self-tests, so they need GraalVM/Maven and are listed separately.

| Command | Result |
|---|---|
| `./tck/tck-run.sh ./standalone/target/solvik solvik-jvm` | exit 0; `PASS=184 FAIL=0 NOT_RUN=0 INFRA=0`; fingerprint bound to the launcher script + every `modules/*.jar`; `fullProfileConformance: NOT_EVALUATED` (draft baseline) |
| `./tck/tck-run.sh ./standalone/target/solvik-native solvik-native` | exit 0; `PASS=184 FAIL=0 NOT_RUN=0 INFRA=0`; fingerprint = SHA-256 of the native binary (distinct from the JVM fingerprint) |
| missing-artifact control runs | Two infrastructure surfaces were exercised against the full 184-test corpus, both exiting 2 with **zero** language-level results: a config whose launcher path is deleted produces `PASS=0 FAIL=0 INFRA=184` (adapter nonzero per test), and a config pointing at a nonexistent adapter script produces `NOT_RUN=184` with `preflight failed: adapter closed stdout before a response line`. A missing or unresponsive IUT is an infrastructure error, never 184 language failures (protocol criterion 6) |
| `./build-all.sh` | exit 0: early TCK gate, JVM build + JVM corpus + JVM conformance run, native build + native corpus + native conformance run, both JaCoCo coverage gates |

The corpus exercises SUCCESS (exact stdout), `exit(n)` (NORMAL_EXIT with a nonzero
language status distinct from a runtime failure), **six structured `COMPILE_ERROR`
diagnostics**, and **five `RUNTIME_ERROR` cases** across three protocol categories
(`ARITHMETIC_ERROR`, `RESULT_WRONG_VARIANT`, `UNCAUGHT_EXCEPTION`). Direct adapter probes additionally confirmed the
RUNTIME_FAILURE channel (`1/0` -> `ARITHMETIC_ERROR`, byte-converted location),
crash-without-a-record -> infrastructure error (never a language result), guest
stdout/stderr isolation from the protocol channel, and exact UTF-16-char -> UTF-8-byte
offset conversion when the two diverge (non-ASCII source).

## Completed slices (../TCK.md §17 order)

* **Slice 1 — re-audit baseline.** Confirmed: Maven reactor `language`/`launcher`/
  `standalone`/`coverage`; GraalVM for JDK 25 at `/opt/graalvm`; ANTLR; JUnit;
  21 example + 76 regression programs; 12 diagnostic fixtures; `test-corpus.sh`;
  no pre-existing `tck/`.
* **Slice 2 — specification version identification.** Added an explicit, pre-1.0
  language-specification revision `2026.09-draft` to `docs/LANGUAGE_SPEC.md`
  ("Versioning"). Semantics unchanged; the Maven `1.0.0-SNAPSHOT` is explicitly *not*
  treated as a spec version. A commit hash is not a semantic version.
* **Slice 3 — normative requirement inventory.** `requirements/requirements.json`
  (+ `ORACLE_REVIEW.md`); `runner/tck_runner/inventory.py` enforces unique ids,
  resolvable references, spec-version compatibility, exactly-one-profile membership,
  non-empty full profile, and rejects profile cycles. Diagnostic codes are marked
  normative only when the spec names them (`diagnosticNormative`).
* **Slice 4 — manifest schema, profiles, fixtures model.** Draft 2020-12 schemas
  (manifest/requirements/profile/protocol/report) published with canonical `$id`;
  closed objects, bounded integers/lengths, `const` versions, closed normalization
  enum, and outcome-conditional expectation constraints.
* **Slice 5 — adapter protocol & result taxonomy.** `protocol/protocol.md` +
  `runner/tck_runner/protocol.py` + `outcome.py`. Strict JSON framing, request/response
  correlation, size limits, half-open UTF-8-byte source spans, and the §8.1 outcome
  state machine.
* **Slice 6 — inventory/manifest validation + fake-adapter self-tests.** Behavioral
  fake adapter (`tests/fake_adapters/fake_adapter.py`) plus dependency-free
  reference-parity self-tests proving the runner's schema validator is not weaker
  than `jsonschema` for every keyword the TCK schemas use.
* **Slice 7 — runner, deterministic reporting, filtering, timeouts, isolation.**
  `runner/tck_runner/runner.py`, `adapter_transport.py` (per-phase timeouts, bounded
  concurrent capture, process-tree cleanup), `isolation.py` (env allowlist,
  workspace/compile/run subtrees, fixture path/symlink/device validation, env
  redaction), `report.py` (deterministic JSON report, exit-code precedence).

### Additional core hardening verified in self-tests

Adversarial fake adapters are *rejected*: wrong stdout/stderr/exit/code/category;
compile/runtime/crash/OS-failure/timeout cross-classification; wrong protocol
version/request id; duplicate JSON keys; multiple JSON values per line; invalid
UTF-8; oversized request/response; blank line; missing/unknown fields; execute-before-
compile; application output smuggled into `compile`; execute after rejected compile;
false capability declaration; changed identity mid-run; missing/mismatched materialized
artifacts; source mutation after staging; guest/adapter-stderr protocol-injection
attempts (harmless); path traversal / escaping symlinks / device files / FIFOs;
duplicate test & requirement ids; missing normative references; incompatible spec
version; mandatory `NOT_RUN`/unsupported-capability/coverage-gap preventing
certification; filtered-pass != full conformance; env redaction; deterministic
order-independent reporting.

### Additional verification executed via the real launcher (Slice 8/9 boundary)

The launcher's compile-only + structured-diagnostic + structured-execute channels were
verified directly against the freshly built JVM distribution and native image for:
`exit(3)` -> `NORMAL_EXIT languageExit:3` (not RUNTIME_FAILURE); `1/0` ->
`RUNTIME_FAILURE runtimeCategory:ARITHMETIC_ERROR` with a converted location; the
class `equals`-without-`hashCode` rejection -> `COMPILE_ERROR` carrying
`SOLV-SEM-045`; and a launcher crash (nonzero exit, no structured record) -> adapter
infrastructure exit (rc 200) with empty protocol stdout. The launcher script's
fallback-JVM warning was routed to **stderr** so guest stdout is byte-exact regardless
of `JAVA_HOME` (the launcher contract: "Standard output carries only program output").

## Certification status (as of this writing)

Full-language conformance certification is **withheld, and enforced in code**, for two
independent reported reasons — neither of which an adapter can waive:

1. **The normative baseline is a draft.** `2026.09-draft` is not a frozen, exhaustive
   normative inventory, and TCK.md §5 requires certification against an
   unversioned/draft baseline to be withheld. `versions.CERTIFIABLE_SPEC_VERSIONS` is
   therefore empty, so `report.build_report` returns `NOT_EVALUATED` for the aggregate
   result while still judging every test PASS/FAIL and still returning the honest
   build-gate exit code. `validate` states this explicitly so a coverage line can never
   be misread as "ready to certify".
2. **The requirement inventory is a growing seed, not an enumeration.** TCK.md §5.1
   requires the full-language profile to contain *every* portable non-deferred
   requirement, and §6 requires the inventory to be derived from every normative `must`
   in the specification. The inventory now holds 124 requirements with 184 portable tests
   (`124/124 active`), still against a 23-section specification containing far more
   normative rules than that. Reported coverage `124/124` therefore means "every inventoried
   requirement is tested", **not** "the specification is covered" — and it is precisely
   this second reading that the withheld certification prevents. Numerics beyond
   overflow, nullability, classes/interfaces/delegation, equality breadth, generics,
   every collection member, enums/sealed/`match`, the regex dialect, includes/modules,
   most exception rules, and most `Result` operations remain unrepresented.

### A false certification was caught and fixed (historical, still enforced)

This describes the increment that added the first two `RUNTIME_ERROR` tests; the guard it
introduced remains active and now withholds certification for the larger 30-requirement
seed as well.

Closing the `REQ-0100` gap took reported coverage to `4/4 active`, at which point the
report flipped to `fullProfileConformance: PASS` after only six portable tests. That was
**wrong output, not a milestone**: it would have asserted that the distribution conforms
to the whole of `2026.09-draft` on the strength of a 4-requirement seed. The root cause was
an under-enforcement in `report.build_report`, which withheld certification only for
per-requirement gaps/ambiguities and never consulted the baseline's own maturity —
contradicting this repository's own written policy (the README and this plan had both
already claimed certification is withheld because the baseline is pre-1.0). The fix adds
`baselineCertifiable`, derived from `CERTIFIABLE_SPEC_VERSIONS`, to the certification
conjunction, and proves the `PASS` branch live in a unit test so the withholding is a
policy decision rather than an artifact of an unimplemented path. Growing the seed to 13
requirements exercises the same guard: full coverage of the seed still does not certify.

(An earlier reason — that the distributions exposed no genuine compile-only /
structured-phase boundary — was resolved in Slices 8/9.)

## Completed slices (continued)

* **Slice 8 — genuine compile-only / structured boundary.** Added to the language:
  `SolvikDiagnosticObject`/`SolvikDiagnosticArray`/`SolvikStringArray` interop views,
  `SolvikException` exposing a stable `category` member, and a `RuntimeCategory`
  enum; `SolvikParseException` now carries a `SourceCatalog` + structured `Diagnostic`
  list exported as the interop member `diagnostics`. Added to the launcher:
  `--compile-only` (routes to `Context.parse`, which runs full static analysis without
  ever invoking the returned call target — so `println`, mutation, `exit`, static
  initializers, and constructors cannot run) and `--diagnostics-json=<path>` /
  `--run-json=<path>` structured channels (`DiagnosticsJson`, `RunResultJson`). All
  covered by positive/negative in-process tests (`SolvikCompileOnlyTest`,
  `DiagnosticsJsonTest`, `SolvikRunJsonTest`, extended `SolvikInteropTest`) with both
  modules above the JaCoCo line/branch gates. Parsing/semantic analysis is *not*
  duplicated in the adapter.
* **Slice 9 — JVM and native adapters.** `adapters/solvik_launcher_adapter.py` is one
  stdlib-only translator (no Solvik/GraalVM/runner imports) shared by both
  distributions; the JVM and native runs are distinct IUT identities bound by
  content-derived fingerprints (JVM = launcher script + every `modules/*.jar`; native
  = SHA-256 of the binary) computed by the adapter itself (exposed via
  `--fingerprint`) so `configs/make-adapter-config.py` records the matching value and
  the report binds the exact distribution. It converts the launcher's UTF-16 char
  offsets to the protocol's half-open UTF-8 byte-offset interval (protocol §3) using
  the bytes it owns, surfaces a crash/internal error as an infrastructure exit rather
  than a language result, isolates guest stdout/stderr as base64 response fields, and
  runs the whole staged tree into the run dir (include/asset safe). Both adapters pass
  the identical seed corpus with no forked goldens.

## Slice 10/11 progress — expanding the inventory toward an enumeration

The requirement inventory grew from a 4-entry seed to **49 requirements / 75 portable
tests**, each with a hand-derived oracle quoted from the specification. Each batch so far
has established the pattern for the sections it touched rather than claiming the rest:

| New requirements | Area | Outcome kinds exercised |
|---|---|---|
| REQ-0200..0202 | `throw` operand typing, `try` handler requirement, non-overridable static members | `COMPILE_ERROR` with spec-named `SOLV-SEM-*` |
| REQ-0203..0204 | expression `if` requires `else`, expression `switch` requires `default` | `COMPILE_ERROR`, including the "does not fabricate a branch mismatch" clause |
| REQ-0205, REQ-0206 | `Result` must-consume rule, `Result` wrong-variant faults | `COMPILE_ERROR` + `RUNTIME_ERROR` |
| REQ-0207 | uncaught exception at the program boundary | `RUNTIME_ERROR` (`UNCAUGHT_EXCEPTION`) |
| REQ-0300 | `val` reassignment is illegal | `COMPILE_ERROR`, **family only** (see below) |
| REQ-0301 | `val` freezes the binding, not the object graph | `SUCCESS`, byte-exact stdout |
| REQ-0400..0405 | raw-string delimiters and preserved newlines, the closed normal-string escape set, `$` having no interpolation meaning, explicit `;` termination, unterminated raw strings, physical newline in a normal string | `SUCCESS` with **byte-exact** stdout, and `COMPILE_ERROR` at the **`LEX` family** level |
| REQ-0450..0452 | sign-symmetric truncating division, IEEE 754 NaN/negative-zero/infinity equality, least-common-widened-type `==` | `SUCCESS` with byte-exact stdout (all four division sign cases; IEEE cases computed rather than literal-parsed) |
| REQ-0453..0457 | safe `?.` access, `??` coalescing, required flow-sensitive narrowing, narrowing invalidated by a write, `null` only to nullable, `S?` not assignable to `T` | byte-exact `SUCCESS`, plus a matched accept/reject **pair** (SOL-TCK-0032 vs 0037) isolating the invalidating-write rule to one statement |
| REQ-0458, REQ-0459 | implicit widening holds for *exactly* the enumerated relation (incl. the spec-named non-relations `Integer`-to-`Float` and `Long`-to-`Float`/`Double`); no common widened type makes an operator ill-typed | `COMPILE_ERROR` at the **`TYPE` family** level |

### Lexical/parse rejections cannot carry code oracles yet

Auditing showed the specification names **no `SOLV-LEX-*` or `SOLV-PARS-*` code anywhere**,
while the implementation freely emits them (`SOLV-LEX-001/002/003`, `SOLV-PARS-001`). So
for lexical errors the TCK asserts the protocol `LEX` family and marks the requirement
`diagnosticNormative: false`. Two further subtleties recorded in `ORACLE_REVIEW.md`:

* SOL-TCK-0025's specification obligation ("the diagnostic must show the exact closing
  delimiter that was expected") is **not** asserted: §15 fixes message *content* but no
  message *text*, and TCK.md §7 says diagnostic wording is non-normative unless the spec
  says otherwise — so asserting it would make the TCK choose an open observable.
* SOL-TCK-0026 requires only that a `LEX` diagnostic be **present**, not that it be the
  only one, because the implementation legitimately also reports a follow-on parse
  error. Demanding a singleton diagnostic would over-constrain behavior the spec leaves
  open.

### The normative-vs-implementation-stable code line, enforced

TCK.md §6 requires the inventory to distinguish specification-required diagnostic codes
from codes that are merely stable in the current Java implementation. Auditing the
specification showed it names **only** `SOLV-RESOL-*`, `SOLV-SEM-*`, and `SOLV-TYPE-*`
codes; there are **no `SOLV-LEX-*` or `SOLV-PARS-*` codes anywhere in the spec**, while
the implementation enum carries ~130 codes far beyond the ~60 the spec names. Consequences
applied in this batch:

* Every code-bearing negative oracle asserts **only** a code that the specification names
  verbatim, and the inventory records it as `diagnosticCode` + `diagnosticNormative: true`.
* Where the spec states a rule but names no code (`REQ-0300`, `val` reassignment), the
  manifest asserts the **family only** and the requirement is marked
  `diagnosticNormative: false`. The IUT reports `SOLV-TYPE-006` here; that code was
  deliberately **not** adopted.
* Rejection cases whose only spec description is "a lexical/parse error" are **not**
  authored as code-bearing oracles at all, because the spec defines no code to assert and
  the protocol family is the only taxonomy the TCK itself defines.

### Three discrepancies caught while authoring (all in `ORACLE_REVIEW.md`)

1. `SOL-TCK-0008`'s first draft used `try`+`finally` with no `catch`; §22.3 requires the
   absence of **both** clauses. Caught by re-reading the spec before the test ever ran.
2. `SOL-TCK-0012` genuinely **FAILED** on the first IUT run: the draft used `1 => { ... }`
   arms, which the parser rejected with `SOLV-PARS-001`, so `SOLV-SEM-043` was never
   reached. Fixed from §21.5's own example syntax with the expectation unchanged — the
   parse error was evidence the *test program* was malformed, not evidence about the
   oracle. (A separate apparent mismatch on `SOL-TCK-0018` was traced to the shell
   command used to compare outputs, which strips trailing newlines; re-comparing the
   raw bytes showed 21 bytes matching exactly, so the oracle and the IUT were both
   right and only the harness was wrong.)
3. `SOL-TCK-0010`: the IUT emitted the spec-unnamed `SOLV-TYPE-006`; not adopted.

The other programs matched their hand-derived expectations on the first run on **both**
distributions.

### Quote verification is now a machine-checked invariant

While authoring the nullability/numerics batch, a draft justified `SOLV-TYPE-001` by
citing "section 10.6" and a `TYPE_MISMATCH`/`TYPE_OPERAND` diagnostic table. **No such
section or table exists.** The citation was fluent and invented -- pattern-completed from
neighbouring sections rather than read out of the document. It was caught only because the
cited string was grepped out of the specification afterwards. That is the most dangerous
possible oracle failure, because a fabricated quote makes an implementation-chosen result
look specification-mandated, so diligence was promoted to an invariant:

* `requirements.json` now carries a **required** `normativeQuotes` field (schema-verified,
  `minItems: 1`) holding the exact specification passages each oracle was derived from.
* `tests/test_oracle_quotes.py` greps **every** recorded passage out of
  `LANGUAGE_SPEC.md`, normalizing only typography, markdown backticks/emphasis and
  whitespace -- never words, so an altered or invented passage still fails.
* The module also asserts the two specific fabrications are absent from the specification
  (a canary), and pins `SOLV-TYPE-001` to its 3 real occurrences so no future edit can
  quietly claim it is the general assignability code without a specification change.
* Falsifiability was verified by injecting the exact historical fabrication: the suite
  fails with `REQ-0001 quote occurs verbatim in LANGUAGE_SPEC.md` and exits 1.

`test_manifest_and_inventory.py` additionally pins that a requirement lacking
`normativeQuotes`, carrying an empty list, or carrying a non-substantive quote is rejected
at the schema level.

### The first RUNTIME_ERROR oracles (REQ-0100)

With the structured execute boundary in place (Slices 8/9), `REQ-0100`'s recorded
blocker was resolved and it was migrated into the portable corpus as **SOL-TCK-0005**
(division by zero) and **SOL-TCK-0006** (checked `Integer` overflow), both
`RUNTIME_ERROR` asserting the protocol's `ARITHMETIC_ERROR` category with empty stdout.
Oracle discipline (recorded in `requirements/ORACLE_REVIEW.md`):

* The fault class is taken from the specification's own words — §4: "division by zero
  raises a Solvik runtime arithmetic error", "Integral arithmetic is checked and raises
  a Solvik runtime arithmetic error on overflow" — mapped onto the protocol's normative
  category (TCK.md §8). It was **not** read back from the implementation.
* `2147483647` = 2^31 − 1 is *derived* from §4 ("A literal outside the signed 32-bit
  range is a compile-time error"), not probed from the IUT.
* Operands are held in mutable `var` bindings so the fault is genuinely a run-time
  event; a `COMPILE_ERROR` can never satisfy a `RUNTIME_ERROR` expectation (§8.1), so
  the outcome choice is itself an oracle claim, and the tests confirm the programs
  compile cleanly.
* The IUT was run only afterwards, to detect a discrepancy; none was found.

**Deliberately not done, and why.** The 21 examples / 76 regression / 12 diagnostic
fixtures were *not* bulk-converted into portable manifests. Their golden `.output` files
were generated by running the implementation, which is exactly the capture-from-IUT path
TCK.md §6 forbids for a conformance oracle. Converting them means re-deriving each
expected observable from the specification and recording it — real per-case review work,
not a copy. The large regression corpus also stays intact as implementation regression
testing (`test-corpus.sh`), which TCK.md §2 treats as a distinct category from
conformance. Bulk migration is deferred so that no captured golden silently becomes a
normative oracle. Building the inventory out to an actual enumeration of the
specification remains the prerequisite for ever lifting the certification withholding.

## Pending slices (dependency order; NOT yet done)
* **Slice 12 — differential mode + third-party example adapter: DONE.** The comparator
  (`runner/tck_runner/differential.py`), the `tck_cli.py differential` command, the
  `tck/tck-differential.sh` wrapper wired into `./build-native.sh`, and a genuinely
  working second implementation (`adapters/reference_subset_adapter.py`, a deliberately
  incomplete spec-derived front end with no Solvik/Java/Truffle code) are all done and
  exercised together over the whole corpus.

  The earlier `adapters/example_third_party.py` was deleted rather than kept. It returned
  `COMPILE_ACCEPTED` / `NORMAL_EXIT` / exit 0 unconditionally, so the runner would have
  reported `PASS` for programs it never compiled: a false-pass facade, which TCK.md treats
  as worse than no adapter at all. Acceptance criterion 11 asks that a third party *can*
  implement the protocol without Solvik or Truffle classes, which is a claim about the
  protocol being implementable -- not a claim of Solvik conformance, and not a license to
  emit a verdict without doing the work. `reference_subset_adapter.py` demonstrates the
  same implementability honestly: it really compiles and executes the subset it declares,
  and refuses everything else instead of inventing a result (8 PASS / 110 FAIL when asked
  to cover `full-language`, which is the correct verdict for an incomplete front end).

* **Slice 13 — build/CI integration (conformance *runs* wired).** `./tck/tck-check.sh`
  is wired into `./build.sh` early (skippable via `SOLVIK_SKIP_TCK=1`); `tck/tck-run.sh`
  now drives a real conformance run against the JVM distribution in `./build.sh` and
  against the native image in `./build-native.sh`, gated symmetrically with the
  existing `SOLVIK_SKIP_CORPUS` corpus step, so a required TCK failure is a nonzero
  build result without hiding JUnit/JaCoCo/packaging/corpus failures. Remaining: CI
  wiring beyond the local wrappers if/when a hosted CI is added.
* **Slice 14 — documentation, traceability, security review, final audit.**

## Known implementation limits / defects to address in remaining slices

* The requirement inventory is **124 requirements, still not an enumeration**. TCK.md §6
  requires deriving an entry from *every* normative `must`/`must not`/algorithm/required
  diagnostic/observable runtime rule; the specification has 23 sections and far more such
  rules than 124 entries.
  *Covered so far*: variables/`val` (§2), typing/precedence/concatenation/equality, the
  `equals`/``hashCode` pairing and the built-in equality rows incl. `Regex`/`RegexMatch` (§3),
  numerics incl. checked arithmetic, IEEE 754, widening and no-common-type operators (§4),
  nullability incl. coalescing, safe access, narrowing, its invalidation and member access on
  a nullable receiver (§5), function lookup, `exit`, arity, display and the implicit `main`
  (§6), classes incl. inheritance, `override`, parameter invariance, `extends Any` and member
  resolution (§7), statics (§7), interfaces incl. multiple implementation and default methods
  (§8), delegation incl. synthesized forwarding, explicit-method precedence and ambiguous
  delegation (§9), generics incl. construction forms, inference, invariance, non-reified type
  tests, user type parameters and every collection member and failure mode (§11), `switch`
  incl. no-fallthrough, first-match-wins, shared cases, nested `break`/`continue` and the
  expression-form `default` requirement (§13), the regex dialect incl. complete-input matching,
  raw-string construction, the portable pattern inventory, non-overlapping left-to-right
  iteration with exclusive `end`, literal replacement, rejected constructs and regex `switch`
  cases (§14), raw/normal strings and `$` (§15), statement termination (§16), control flow
  incl. all three range operators (§17), type tests and checked casts (§18), module prefixes,
  aliases, expansion order, expand-once and the named include diagnostics (§20), expression
  constructs (§21) incl. block-expression scope isolation and source order, the three
  invalid value-block shapes and the abrupt-only `Nothing` case under `SEM_BLOCK_RESULT_REQUIRED`,
  semicolon/comment tail equivalence, chained and abrupt-branch `if` expressions, expression
  `switch` value production with exactly-once scrutinee evaluation, the `Number`-not-`Long`
  join rule and its discriminating rejection, expression contexts and `match` branch blocks, and
  the `SEM_HASHCODE_WITHOUT_EQUALS` half of the pairing rule, exceptions (§22) incl.
  reachability of a handler written on a built-in base, the synthesized optional message and
  its `null` case, the private-slot rule that makes `e.message` unresolvable, its type and
  arity codes, its independence from a declared constructor, the reservation of `message`
  and `getMessage` in both directions, non-constructible bases, first-match-wins with the
  specific clause written first, the `SEM_UNREACHABLE_CATCH` clause an earlier base clause
  shadows, catch-binding scope and name reuse, rethrow of an initialized binding, the
  `SEM_INVALID_CATCH_TYPE` handler rule, `finally` ordering against propagation to an
  enclosing handler, a `finally`-only `try`, a trailing `throw` covering the
  value-on-all-paths obligation and unwinding across two call frames, and `Result`
  operations (§23); and lexical basics (§1) incl. the identifier character class, reserved
  keywords and the no-leading-digit rule, `$` as a non-identifier character, `//` line
  comments, non-nesting block comments pinned in both directions, the clause that a
  comment's physical newlines remain visible to semicolon insertion, the signed 32-bit
  decimal-literal range boundary pinned on both sides, `L`-suffixed `Long` and `F`-suffixed
  `Float` literals pinned as *type* choices by the cross-assignment each one forbids,
  exponent application pinned by equality rather than by rendering, and character literals
  with exactly one scalar or one escape.
  *Still uncovered*: the remainder of identifier and literal syntax that the specification
  describes without stating a decidable rule (notably `Byte`/`Short` "explicit conversion",
  which §1 never actually specifies a syntax for), built-in runtime representation (§10),
  enums/sealed/`match` (§12), member chaining
  (§16), the generic exception rules (§22.1: a generic class can be neither thrown nor
  caught, and its surplus arguments stay an ordinary arity error) and the remaining `Result`
  rules, and interface
  covariance/diamond-default rules (§8). Two clauses are recorded as **untestable rather
  than forced** (see ORACLE_REVIEW.md): the §13 `default` count/ordering clauses, which cannot
  be separated because two `default`s necessarily put one out of position; and all of §19,
  whose feature-conflict priority ordering is a design-time rule with no observable program
  behavior. The §14 sentence that regex cases do not establish exhaustiveness is *observable*
  -- SOL-TCK-0115 shows a regex case failing to make an expression `switch` exhaustive -- but
  it needs no separate oracle, because §13 already states the rule and names its code, so the
  §14 phrasing is carried by REQ-1108 rather than duplicated. One item is **blocked rather than merely unstarted**: any oracle copying the
  `+`-on-`String` style of the §8 and §12 examples, which conflicts with §3 (see
  ORACLE_REVIEW.md).
  Coverage `124/124` is full **for the inventory**, not for the spec, and the withheld
  certification is what prevents that distinction being misread.
* Because the spec names no `SOLV-LEX-*`/`SOLV-PARS-*` codes, lexical and syntactic
  rejections can currently only be asserted at the protocol family level, or need a
  specification change to name codes. This is a genuine spec gap to raise, not something
  the TCK may paper over by adopting implementation codes.
* The 21 examples / 76 regression / 12 diagnostic fixtures still need case-by-case oracle
  derivation; their current goldens are IUT-captured and so cannot serve as conformance
  oracles.
* The launcher's guest-exception `category` interop is read structurally for runtime
  failures; a few `SolvikException` factory sites still map to `OTHER_RUNTIME_ERROR`
  (the schema-legal safe default) rather than a specific category. This is fidelity to
  an unknown category, never a wrong category, and is a Slice 11 polish item.

## Architectural decisions

* Four independent version identifiers; runner rejects unknown versions rather than
  reinterpreting them.
* One adapter subprocess per test; guest I/O as base64 response fields; adapter process
  exit `0` means only a clean protocol session, never a guest pass.
* The runner, not the adapter, applies the oracle via a pure state machine; adapters are
  untrusted result producers.
* `format` is *asserted* by the runner's dependency-free validator for a small set
  (`int32`/`date-time`/`sha256`), a documented strengthening over the annotation-only
  reference; parity self-tests assert the runner is never *weaker*.
* Draft 2020-12 array semantics (`prefixItems` + `items`); `additionalItems` is
  rejected as an out-of-dialect keyword rather than silently given draft-07 meaning.

### Slice 10/11 progress: section 11 (generics and collections)

Requirements REQ-0900..REQ-0908 and tests SOL-TCK-0076..SOL-TCK-0091 (91 total, all
passing on the JVM distribution; native re-verification runs in the final gate). Covers the
collection operation tables, construction forms, repeated-key behavior, the three failure
sentences (plus the second `Stack` operation separately), invariance with a positive
control, the non-reified type-test rule, and a user-declared generic class.

Two process rules became machine-checked during this batch, after they each would otherwise
have been violated:

* oracle-comment quotations of the specification must occur verbatim in it
  (`check_oracle_prose_quotes_are_verbatim`) — the batch's first drafts invented four
  "quotations" describing collection return types that section 11 does not give;
* a `SUCCESS` test may not contain a construct the specification makes categorically
  invalid (`check_success_tests_are_executable`) — the batch's first probe used `func main`
  everywhere, every probe failed to compile with `SOLV-SEM-001`, and reading only stdout
  produced a confident false conclusion that behavior "matched spec exactly";
* every corpus directory needs a program and a manifest (`check_corpus_dirs_are_complete`)
  — eight scaffold directories sat manifest-less and therefore unexecuted and unreported.

The `runtimeCategory` taxonomy was also found to be documented only in the schemas;
`protocol/protocol.md` sections 4.1/4.2 now define it with a per-member specification basis,
and `NULL_DEREFERENCE` and `REGEX_FAILURE` are reserved and asserted-unused. See
`requirements/ORACLE_REVIEW.md` for the full account.
