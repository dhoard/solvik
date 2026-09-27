# Solvik TCK — Technology Compatibility Kit

An implementation-independent conformance kit for the Solvik language. The same
portable corpus and expectations run against any conforming implementation via a
subprocess protocol; the runner imports no Solvik, GraalVM, Truffle, or launcher
Java classes and computes no expected result from the implementation under test
(IUT).

Authority order for all TCK behavior: `AGENTS.md` > `docs/LANGUAGE_SPEC.md` >
`docs/ARCHITECTURE.md` > `../TCK.md` (this kit's implementation brief).

## Layout

```
tck/
  README.md                 this file
  IMPLEMENTATION_PLAN.md     factual progress + verification log
  VERSION                    TCK release id (immutable per release)
  requirements/              versioned normative requirement inventory
    requirements.json        machine-readable inventory (REQ-*)
    ORACLE_REVIEW.md         per-oracle derivation record (never IUT-derived)
  schemas/                   published JSON Schemas (Draft 2020-12), one per $id
  protocol/                  protocol spec + worked transcript
  profiles/                  compliance profiles (list requirement IDs, no globs)
  corpus/<spec-version>/     portable .sol programs + .manifest.json
  adapters/                  current-distribution + independent reference adapters
    solvik_launcher_adapter.py  stdlib-only bridge to a Solvik launcher (JVM + native)
    reference_subset_adapter.py independent stdlib-only front end for a declared subset
  configs/                   adapter-config generation (fingerprints the launcher IUT)
  runner/                    portable runner (stdlib only) + tck_cli.py
  tests/                     runner self-tests + behavioral fake adapters
    test_oracle_quotes.py     greps every normative quote out of the spec -- in the
                            inventory and in corpus oracle comments -- plus
                            section-citation and corpus-structure invariants
  reports/                   ignored build-output location (never committed)
  tck-check.sh               early independent gate (validate + self-tests)
  tck-run.sh                 conformance run against a built distribution
```

## Versioning

Four independent identifiers (see `runner/tck_runner/versions.py` and
`docs/LANGUAGE_SPEC.md` "Versioning"):

* language specification revision — `2026.09-draft` (pre-1.0; **not** the Maven
  `1.0.0-SNAPSHOT`);
* TCK release — `tck/VERSION`;
* manifest schema version — embedded per manifest (`manifestSchemaVersion: 1`);
* adapter protocol — `protocolVersion: "1"`.

Released inputs are immutable; corrections require a new release. Reports bind the
content digests of the requirements inventory, selected manifests, adapter
configuration, and an IUT fingerprint.

## Commands (no network, no Solvik/Java for validate + self-tests)

```bash
# Validate every versioned input and run requirement-coverage checks.
python3 tck/runner/tck_cli.py validate

# Run the portable runner self-tests (fake subprocess adapters only).
python3 tck/tests/run_selftests.py            # or: python3 tck/runner/tck_cli.py selftests

# Both, early, as the CI/build gate (invoked by ./build.sh):
./tck/tck-check.sh

# Conformance run against a built Solvik distribution (needs a launcher; runs inside
# ./build.sh against the JVM launcher and ./build-native.sh against the native image):
./tck/tck-run.sh ./standalone/target/solvik         solvik-jvm
./tck/tck-run.sh ./standalone/target/solvik-native   solvik-native

# Or, directly, against any configured adapter:
python3 tck/runner/tck_cli.py run --adapter-config <cfg.json> [--profile NAME]
    [--test SOL-TCK-0001 ...] [--report PATH]
```

`<cfg.json>` is `{ "name", "argv": [abs exe, ...], "fingerprint": "<sha256hex>",
"env": {...} }`. `argv[0]` and script paths must be **canonical** because the
adapter runs in a runner-created workspace with an allowlisted environment.
`tck/tck-run.sh` generates that config for you (via
`configs/make-adapter-config.py`, which asks the adapter itself for the launcher's
fingerprint so the recorded value always equals what the adapter advertises).

### Exit codes

`0` — requested tests executed and passed. `1` — one or more conformance failures,
no infrastructure error. `2` — invalid invocation/input, protocol failure, unsupported
required capability, `NOT_RUN`, or any infrastructure error. Infrastructure status
dominates conformance status.

## Conformance rules (summary; authoritative text in `../TCK.md` §13)

* A required test that did not execute is never a pass.
* An infrastructure error prevents certification but is not an implementation failure.
* An unsupported required capability, a requirement coverage gap, or a specification
  ambiguity withholds full-profile conformance (`NOT_EVALUATED`).
* **The normative baseline itself must be certifiable.** Aggregate conformance is only
  ever issued for a spec revision listed in `runner/tck_runner/versions.py`
  (`CERTIFIABLE_SPEC_VERSIONS`). That set is currently empty, because `2026.09-draft` is
  a pre-1.0 draft and the requirement inventory is a deliberate seed rather than an
  enumeration of every normative rule in the specification (`../TCK.md` §5–§6). Individual
  tests are still judged PASS/FAIL and the build gate still fails on a real conformance
  failure; only the aggregate conformance *claim* is withheld, so a passing run can never
  be mistaken for certification against a draft baseline.
* **Reported coverage is seed coverage, not specification coverage.** Whatever ratio
  `validate` prints ("N tested / N active") means only that every *seeded* requirement has
  a test. It never means the specification is covered: the inventory remains far short of
  the enumeration §6 requires, and the count is deliberately not duplicated in this
  document so it cannot go stale. `IMPLEMENTATION_PLAN.md` records which spec areas are
  and are not yet covered. The withheld certification is what keeps the two readings apart.
* A percentage is never presented as certification; a filtered run returns
  `NOT_EVALUATED` for the full profile.

## Outcomes (manifest) and their phase contract

| Outcome | Phase requirement |
|---|---|
| `SUCCESS` | successful compile then normal execution; exact observables; declared language exit (omission means exactly 0) |
| `COMPILE_SUCCESS` | full static validation, no application execution, zero observables |
| `COMPILE_ERROR` | structured source rejection from `compile`, before any execution |
| `RUNTIME_ERROR` | successful compile, then a structured runtime failure from `execute` |

A compile error can never satisfy `RUNTIME_ERROR`; a crash/timeout/OS failure can
never satisfy a language-error or success expectation.

## Adapter protocol

Language-independent, newline-delimited strict-JSON protocol over a per-test
subprocess. Guest stdout/stderr are base64 response fields, never the adapter's own
streams. Source spans are half-open UTF-8 byte offsets. See
`protocol/protocol.md` and `protocol/examples/handshake.md`.

## Current-distribution adapters

`adapters/solvik_launcher_adapter.py` is a single, stdlib-only translator shared by
both shipped distributions; the JVM and native runs are therefore **distinct IUT
identities that share one faithful translation layer**, not forked test suites. It
drives the launcher's structured channels (`--compile-only` + `--diagnostics-json` for
static validation that never runs the program; `--run-json` to tell `exit(n)` from a
runtime failure from a crash) and performs the one job the protocol assigns to an
adapter: converting the launcher's UTF-16 code-unit character offsets into the
protocol's half-open UTF-8 byte-offset interval (it owns the staged file bytes),
classifying a crash/internal error as an infrastructure exit rather than a language
result, and carrying guest stdout/stderr as base64 response fields. Each distribution
is bound by a content-derived fingerprint (JVM = launcher script + every
`modules/*.jar`; native = the binary's SHA-256), so a rebuild or a switch of
distribution is detected. `tck/tck-run.sh` exercises both against the shared corpus as
part of the build gate.

## Writing a third-party adapter

Implement the three operations (`describe`, `compile`, `execute`) and point an
adapter config at your program. No Solvik Java or Truffle classes are required.

`adapters/reference_subset_adapter.py` is a worked example: an independently written,
stdlib-only front end that genuinely compiles and runs a **declared subset** (top-level
`val` of a literal, `print`/`println` of a literal or bound name, and `include`
resolution with the registry-named `SOLV-RESOL-008`/`-012`/`-011` conditions). It is
deliberately incomplete and refuses everything outside its subset with
`IMPLEMENTATION_FAILURE`, which the runner maps to a non-conformance exit rather than a
language verdict. That refusal is the point: an adapter that accepted programs it could
not actually run would report PASS for results it never computed, so a partial adapter
must announce the boundary instead of guessing. Because it implements a subset, it can
never certify the `full-language` profile, and it is not an oracle.

The runnable reference implementation of the full protocol, including every error path,
is `tests/fake_adapters/fake_adapter.py` (which deliberately emits wrong results to
prove the runner rejects them).

A conforming `compile` performs complete static validation and **does not** execute
application code. If an implementation cannot provide a genuine compile-only
boundary, it must declare the `compile-only` capability unavailable and cannot pass
a profile that requires it.
