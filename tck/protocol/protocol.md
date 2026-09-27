# Solvik TCK Adapter Protocol v1

Versioned, language-independent, subprocess protocol between the portable runner
and an implementation-under-test (IUT) adapter. Schema:
`schemas/protocol-1.schema.json` (`$id` `https://solvik.org/tck/schema/protocol-1.json`,
JSON Schema Draft 2020-12). Third-party adapters implement this document plus the
schema; no Solvik, GraalVM, or Truffle code is required.

## 1. Transport and framing

* One adapter **process per test**, launched by the runner with a runner-created
  workspace as its working directory and an allowlisted environment.
* The runner writes **one** compact UTF-8 JSON **request** per line to adapter
  **stdin** and reads **exactly one** JSON **response** line from adapter **stdout**.
* Requests/responses are separated by `\n`. JSON string values must escape literal
  newlines (no raw line breaks in a message).
* After the last request the runner closes stdin and requires stdout **EOF** and
  adapter process **exit code 0**. A nonzero adapter exit is an infrastructure error
  even if an earlier response claimed a language result.
* Adapter **stderr** is non-protocol diagnostic logging; the runner captures it to a
  bounded buffer. Guest program stdout/stderr are **base64 fields inside responses**,
  never the adapter's own streams, so guest output can never forge protocol data.
* Commands are argument arrays, never shell strings. The runner does not use a shell.

## 2. Message envelope

Every message carries, exactly:

| field | meaning |
|---|---|
| `protocolVersion` | the string `"1"` (must match exactly) |
| `schemaVersion` | the integer `1` |
| `requestId` | a per-session integer chosen by the runner |
| `op` | `"describe"`, `"compile"`, or `"execute"` |

The response's `requestId` and `op` **must match** the request exactly, or the runner
reports a protocol error. The runner rejects: duplicate JSON keys, blank lines,
multiple JSON values on a line, invalid UTF-8, unknown fields (closed schema),
oversized messages, unsupported versions, and premature process exit.

The shared envelope schema validates structure. Response **completeness** (a
`compile`/`execute` response must carry `status`; a `describe` response must carry
`implementation`) is enforced by the runner's correlation layer, because a *request*
legitimately carries neither field.

## 3. Source location convention

All source spans use **UTF-8 byte offsets** with a **half-open** interval
`[startByteOffset, endByteOffset)`. Adapters translate their internal representation
(JVM UTF-16 offsets, line/column pairs, etc.) into this convention. The runner never
compares representations from two different conventions.

## 4. Operations

### `describe`

Request: `{op:"describe"}`.
Response adds `implementation`:

```json
{"name":..., "version":..., "fingerprint":"<sha256hex>",
 "specVersions":["2026.09-draft"], "profiles":["full-language"],
 "capabilities":["compile-only"],
 "limits":{"maxRequestBytes":...,"maxResponseBytes":...,"maxCapturedOutputBytes":...,
           "maxSourceTreeBytes":...,"maxDiagnostics":...,"maxArtifacts":...,"cancelGraceMs":...}}
```

`describe` is used both in a **preflight** session (to select compatible tests and
freeze the identity/versions/capabilities/limits) and again as the **first** request
of every per-test session; the per-test `describe` must match the frozen preflight
identity exactly or the test is an infrastructure error. Changing identity,
capabilities, or limits mid-run is a protocol violation.

### `compile`

Request adds: `workspace` (canonical compile-dir path), `inputTreeDigest` (SHA-256 of
the canonical staged input tree), `entryPoint` (workspace-relative),
`compileTimeoutMs`, optional `options`.

The adapter performs **complete static validation without running application code**.
It must not run `println`, mutation, `exit`, static initializers, constructors, or any
top-level application behavior.

* On acceptance: `{status:"COMPILE_ACCEPTED", artifactHandle:"<64hex>", artifactManifest:[{path,digest},...]}`,
  **with no** `diagnostics`/`stdoutBase64`/`languageExit`. `artifactHandle` is an
  opaque token bound to the input digest and this compilation; `artifactManifest` is a
  sorted list of workspace-relative regular files and their digests for AOT backends
  (empty for in-memory backends).
* On rejection: `{status:"COMPILE_REJECTED", diagnostics:[{family,code,file?,location?,text?},...]}`,
  **with no** `artifactHandle`/`artifactManifest`/`stdout`/`languageExit`. A rejected
  compile leaves no executable artifact.

### `execute`

Legal **only after** a `COMPILE_ACCEPTED` `compile` in the same session. Request adds
`workspace` (run-dir path), `artifactHandle`, `inputTreeDigest`,
`executeTimeoutMs`, `stdinBase64`. The adapter executes the exact compiled artifact.

* `{status:"NORMAL_EXIT", languageExit, stdoutBase64, stderrBase64}` — normal
  completion including an explicit `exit(n)`.
* `{status:"RUNTIME_FAILURE", runtimeCategory, location?}` — a language runtime
  failure; carries **no** `languageExit`.
* `{status:"IMPLEMENTATION_FAILURE", message}` — a *caught* crash/internal error; a
  real IUT result that produces a conformance FAIL (distinct from a language error).

### 4.1 `runtimeCategory` — the TCK's classification of a language runtime failure

`runtimeCategory` is **not** a language feature and is **not** taken from any
implementation's own error strings, class names, or enum values. It is a closed TCK-owned
classification over `RUNTIME_FAILURE`, defined here and enforced by
`schemas/protocol-1.schema.json` and `schemas/manifest-1.schema.json`. An adapter maps the
observed guest failure onto exactly one member of this set; a manifest may assert one.

Every category below is defined by a sentence of `docs/LANGUAGE_SPEC.md`, quoted verbatim.
The mapping is a TCK editorial decision, so it is stated rather than implied:

| Category | Defining specification text | Section |
|---|---|---|
| `ARITHMETIC_ERROR` | "Integral arithmetic is checked and raises a Solvik runtime arithmetic error on overflow" / "division by zero raises a Solvik runtime arithmetic error" | §3, §4 |
| `CAST_FAILURE` | "An unsuccessful `as` cast raises a Solvik runtime type error" | §18 |
| `INDEX_OUT_OF_BOUNDS` | "An invalid index raises a Solvik runtime bounds error" | §11 |
| `COLLECTION_FAILURE` | "`get` for a missing key raises a Solvik collection error" / "`peek` and `pop` on an empty stack raise a Solvik collection error" | §11 |
| `RESULT_WRONG_VARIANT` | "`unwrap` on an `Err`, `unwrapErr` on an `Ok`, and `expect` on an `Err` raise a Solvik runtime fault of the same class as an arithmetic, cast, or bounds fault" | §23.1 |
| `UNCAUGHT_EXCEPTION` | "An uncaught thrown value is a guest-visible failure: it terminates the program with a non-zero exit status" | §22.5 |
| `OTHER_RUNTIME_ERROR` | *No defining sentence: this row is editorial, not a specification quotation.* Catch-all for a runtime failure the specification requires but this table does not yet name. Asserting it claims only that a runtime failure of an unspecified class occurred, never a specific class, which is why no conformance test in the corpus asserts it. | — |

**Two schema members are reserved and must not be asserted by any conformance test:**
`NULL_DEREFERENCE` and `REGEX_FAILURE`. `LANGUAGE_SPEC.md` contains no vocabulary for a
null-dereference fault and none for a regex-evaluation fault (verified by `grep`). They
exist in the schema only so an adapter can classify a failure it observes without
inventing a protocol violation; a manifest that asserts either one would be asserting a
language guarantee the specification does not make. The corpus therefore contains no
oracle on either, and `test_oracle_quotes.py` asserts that no manifest uses them.

A manifest asserting a category is claiming the specification *requires a runtime failure
of that class*, from the row above. Where the specification requires a failure but does
not distinguish its class beyond that, the manifest asserts the outcome
(`RUNTIME_ERROR`) without a category rather than guessing one.

### 4.2 `family` — the phase-level classification of a compile diagnostic

A compile diagnostic carries a `family` drawn from the closed set `LEX`, `PARS`, `RESOL`,
`TYPE`, `SEM`. TCK.md §6 recognizes these as the families in which implementation-stable
codes exist and TCK.md §7 permits a manifest to assert a *stable code or diagnostic
family*; this section fixes them as protocol values so an adapter and a runner cannot
drift apart.

`family` is the weaker of the two assertions and is the **default** for a rejection whose
rule the specification states without naming a code. Asserting a family claims only "the
implementation rejected this program during this phase of analysis" — it deliberately does
not claim which rule fired. Because the specification names codes only in the `RESOL`,
`SEM` and `TYPE` families, `LEX` and `PARS` can currently only ever be asserted at family
level; that is a genuine gap in the specification, recorded in `IMPLEMENTATION_PLAN.md`
rather than closed by adopting implementation codes.

An `execute` before a successful `compile`, execution after a rejected `compile`, a
reused/unknown/expired `artifactHandle`, a compile diagnostic returned from
`execute`, or application output produced during `compile` are all protocol
violations (infrastructure errors).

## 5. Outcome-matching state machine (runner-owned)

1. Preflight `describe` selects compatible tests.
2. Per-test `describe` must match the frozen preflight.
3. Stage + hash the fixture tree; re-verify before execute.
4. `compile` under the compilation timeout; verify materialized artifacts.
5. `COMPILE_SUCCESS`/`COMPILE_ERROR`: stop without execute, compare the compile result.
6. `SUCCESS`/`RUNTIME_ERROR`: require success, then `execute` under a separate timeout.
7. Compare structured observables.

A compile error can never satisfy `RUNTIME_ERROR`; a crash/timeout/OS failure can never
satisfy a language-error or success expectation. Extra phases or observables are
failures, not ignored data.

## 6. Limits, timeouts, cleanup

Units are milliseconds and bytes; all limits are hard. Exceeding one is an
infrastructure result, never output truncation followed by comparison. On timeout the
runner terminates the adapter/IUT process tree, waits a bounded grace, force-kills, and
reports a surviving process as an infrastructure error. Cleanup runs on every path
(pass, fail, timeout, interrupt) and deletes only runner-created workspace paths.

## 7. Worked example

See `examples/` for a full byte-level session (a conforming JVM-style adapter run).
The self-test fake in `../tests/fake_adapters/fake_adapter.py` is a complete,
runnable reference implementation of this protocol.
