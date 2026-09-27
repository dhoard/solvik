# Resolve Subsystem Correction Plan

Item-by-item phased plan for correcting the include/module **resolution** subsystem
(`org.solvik.parser.IncludeResolver`, `TruffleIncludeSourceAccess`, `ModuleNames`, `FileScope`)
against `docs/LANGUAGE_SPEC.md` section 20 and `docs/ARCHITECTURE.md`, following a standard SDLC
sequence: baseline -> defect characterization -> failing test -> minimal fix -> regression ->
full gate -> review.

Authority order per `AGENTS.md`: `AGENTS.md` > `docs/LANGUAGE_SPEC.md` > `docs/ARCHITECTURE.md`.
This plan changes no language design and no normative semantics. It does **not** implement
`prompts/INCLUDE-HASH-PLAN.md` (see Non-Goals).

---

## 1. Baseline (Phase 0) — recorded, not a change

| Check | Result |
|---|---|
| Resolve suite (`SolvikInclude*`, `SolvikModuleTest`, `SolvikNamespaceNegativeTest`, `SolvikSourceModelTest`, `SolvikDiagnosticCodeCoverageTest`) | PASS — 121 tests |
| `git status` | user-owned in-flight edits to `IncludeResolver`, `ModuleNames`, `ClassSymbol`, `SolvikLanguage` + 8 test files; preserved throughout |

Each item below was characterized by running the current implementation, not by reading comments.

---

## 2. Item R-1 — Vacuous include-access assertions (test-side defect)

**Severity:** high. Two tests assert nothing and therefore cannot fail.

**Location:** `language/src/test/java/org/solvik/test/SolvikIncludeAccessTest.java`,
`includingADirectoryPathReportsNotAFile`, `includingAnUnreadableFileReportsIOError`.

**Defect:** both assert with

```java
assertThat(message).satisfiesAnyOf(
        m -> m.contains("SOLV-RESOL-009"),
        m -> m.contains("SOLV-RESOL-008"));
```

`satisfiesAnyOf` takes `Consumer`s. A lambda whose body is a boolean-returning method invocation is
a valid statement expression whose value is discarded, so each consumer does nothing, so the
assertion succeeds unconditionally.

**Observed (current code, this machine):**

```text
contains-any=false            (message matches neither code)
satisfiesAnyOf PASSED         -> the assertion is vacuous
```

**Oracle:** `AGENTS.md` — "Never weaken or delete tests merely to make an implementation pass";
"add positive and negative tests for every semantic feature". The file's own established idiom is
the `codeContains(PolyglotException, DiagnosticCode)` helper, which asserts for real.

**Fix:** replace both `satisfiesAnyOf` usages with `codeContains(failure, <the specified code>)`.
No expectation widening, no `or`-list.

**Acceptance:** each test fails when the emitted code changes and passes today with the single
specified code.

---

## 3. Item R-2 — Resolution diagnostics 009/010 are documented unreachable but are reachable

**Severity:** high. Two spec-required diagnostics are claimed untestable and are allow-listed out
of coverage while the implementation produces them correctly.

**Claims under test**

- `SolvikIncludeAccessTest` class javadoc: `RESOL_INCLUDE_NOT_FILE` / `RESOL_INCLUDE_IO` "cannot be
  driven through the context on this platform".
- `SolvikDiagnosticCodeCoverageTest.ALLOW_LIST` carries both codes for the same reason.

**Oracle:** `LANGUAGE_SPEC.md` section 20 required-diagnostics table spans `009` and `010` at the
include directive, and: "Denied access and other I/O failures become `SOLV-RESOL-010` and never
escape as host errors."

**Observed (current implementation)**

| Scenario | Actual diagnostic |
|---|---|
| include a **directory whose name ends in `.sol`** | `SOLV-RESOL-009: included path is not a regular file` |
| include a `---------` permissioned file | `SOLV-RESOL-010: cannot read include ...` |
| include under a **`allowAllAccess(false)` / `allowIO(false)`** context | `SOLV-RESOL-010: ... Operation is not allowed for: ...` |
| baseline: same absolute include with access allowed | resolves, no failure |

The earlier "reported as not found" premise is false on this platform: a directory whose name does
not end in `.sol` is rejected earlier as `SOLV-RESOL-007` (correct — the path rule requires a `.sol`
name), which is what made the old probe look like `008`.

**Fix:**
1. Drive `009` from a created **`<tmp>/not-a-file.sol` directory** (portable, no permissions needed).
2. Drive `010` from an **IO-denied context** (portable, no filesystem permissions, not root-sensitive;
   the permission-bit approach is unreliable for a root user and on Windows).
3. Delete both `ALLOW_LIST` entries and the false javadoc; the codes are now genuinely referenced.
4. Keep the positive baseline case proving the same absolute path resolves when access is allowed,
   so `010` is proven to be about denial rather than about absolute paths.

**Acceptance:** `SolvikDiagnosticCodeCoverageTest` passes with an empty allow-list entry set for
`009`/`010`; both codes asserted exactly, once each.

---

## 4. Item R-3 — Asymmetric module-prefix collision detection in `IncludeResolver`

**Scope note (this is not about paths):** a file's module comes only from its own `module`
declaration, and a prefix is bound either by `alias` or by an unaliased include of a
module-declaring file. Filesystem paths never determine a module. The defect concerns two *prefix
bindings* inside one file colliding, and it is reproducible with any directory layout.

**Severity:** medium-high. One prefix gets bound to two different modules and the second binding is
silently discarded, so qualified references resolve against the wrong module.

**Location:** `IncludeResolver.expandInclude`, unaliased branch: `prefixes.putIfAbsent(targetModule, targetModule)`
overwrites nothing and reports nothing, while the aliased branch does report a collision.

**Observed (current implementation)**

| Program | Result |
|---|---|
| `include "a.sol"` (declares `module app`), then `include "b.sol" alias app` (declares `module mod_b`) | `RESOL_ALIAS_DUPLICATE` |
| `include "b.sol" alias app` (declares `module mod_b`), then `include "a.sol"` (declares `module app`) | **silently succeeds** |
| two unaliased includes of the **same** module `app` | succeeds (correct: module merge) |
| own `module app` then unaliased include of module `app` | succeeds (correct: same binding) |

The first two rows are the same collision described in the opposite source order, and they must
agree.

**Observable harm, not merely cosmetic.** With the accepted ordering, prefix `app` keeps denoting
`mod_b`, so a reference to a member that genuinely lives in `module app` fails with the prefix
rewritten to the wrong module:

```text
root: include "b.sol" alias app      (module mod_b)
      include "a.sol"                (module app, declares onlyInModuleApp)
      println(app::onlyInModuleApp())

resolveSuccess=true
analyzeSuccess=false
  SEM RESOL_UNKNOWN_NAME | unknown name 'mod_b::onlyInModuleApp'
```

The author's `app::` names `mod_b`, the spliced `module app` declarations are unreachable through
any prefix, and the diagnostic reports a module the source never mentions. In the opposite order the
same intent is rejected up front with `RESOL_ALIAS_DUPLICATE` naming the prefix.

**Oracle:** `LANGUAGE_SPEC.md` section 20 — "Binding one prefix twice in a file, **including a
collision with a prefix an unaliased include already made visible**, is `SOLV-RESOL-013`." The
case the spec names explicitly is the direction that already works, which establishes prefix
uniqueness within a file as the rule; the mirror case is the same binding conflict and must be
reported the same way rather than resolved by whichever include appeared first.

**Fix (root cause, minimal):** in the unaliased branch, replace `putIfAbsent` with an explicit
binding check — if the prefix is already bound to a **different** module, report
`RESOL_ALIAS_DUPLICATE` at the include span with the existing message wording; if it is bound to the
**same** module, keep the current no-op so module merging still works. Same diagnostic, same span,
same message as the aliased path; no new code.

**Non-goal:** no change to `013` semantics, spans, or messages; no change to cycle, dedup, or
alias-of-default-module behavior.

**Acceptance:** new negative test (alias-then-unaliased collision) reports `RESOL_ALIAS_DUPLICATE`;
existing reverse-order test still reports `RESOL_ALIAS_DUPLICATE`; same-module merge and own-module
repeat cases still succeed; all resolve suites plus `./build-all.sh` pass.

---

## 5. Item R-4 — No-change decisions (recorded so they are not silently ignored)

| Observation | Decision | Reason |
|---|---|---|
| `prompts/TEST-COVERAGE.md` section 4.7 lists `TYPE_MEMBER_ACCESS_UNSUPPORTED`, `BuiltinCollectionTypes.byName`, `SolvikNumericBinaryNode.intResult` as dead code to remove | **No production change** | All three symbols are already absent from `language/src`; the doc is stale, the work is done. Doc belongs to the user; reported, not rewritten. |
| `prompts/INCLUDE-HASH-PLAN.md` allocates `SOLV-RESOL-015..019` | **Blocked / deferred** | `SOLV-RESOL-015` is already `RESOL_UNKNOWN_MODULE`; the plan's numbering is stale. Implementing `hash=` is a language **feature** requiring a `LANGUAGE_SPEC.md` addition, which this corrective plan must not make. |
| `module module` / `alias module` are rejected by the parser (`SOLV-PARS-001`) rather than `SOLV-RESOL-012`, though section 20 says a module name "is not a reserved word" | **No change** | The constraint is enforced (rejected at compile time, source-located). The diagnostic-family choice is not fixed by the spec or an established invariant, so treating it as a defect would invent semantics. |
| `ModuleNames.isValid` does not itself test reserved-ness | **No change** | Consequence of the row above; reserved words never reach the validator as identifiers. |

---

## 6. Execution order and gates

| Phase | Work | Gate |
|---|---|---|
| 0 | Baseline capture | resolve suites PASS before any edit |
| 1 | R-1 replace vacuous assertions | focused `SolvikIncludeAccessTest` |
| 2 | R-2 real `009`/`010` coverage, drop allow-list + false javadoc | `SolvikIncludeAccessTest`, `SolvikDiagnosticCodeCoverageTest` |
| 3 | R-3 failing collision test first, then `IncludeResolver` fix | `SolvikModuleTest` + all `SolvikInclude*` |
| 4 | Full regression | `./mvnw -pl language test`, then `./build-all.sh` |
| 5 | Review | `git diff --check`, full `git diff`, `git status` vs Phase 0 |

Per phase: run the narrowest relevant suite, keep the change local, and re-run any gate that fails
after fixing its cause. Nothing is considered done until `./build-all.sh` passes.
