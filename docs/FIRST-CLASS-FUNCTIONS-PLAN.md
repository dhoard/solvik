# First-Class Functions — Implementation Status & Remaining Plan

Handoff document for completing the first-class-functions feature described in
`FIRST_CLASS_FUNCTIONS.md`. This file records **verified current status** and the
**exact remaining steps**. Keep it updated as each phase completes.

> Source-of-truth order (AGENTS.md): `AGENTS.md` → `docs/LANGUAGE_SPEC.md` →
> `docs/ARCHITECTURE.md` → `FIRST_CLASS_FUNCTIONS.md`. The design plan is
> implementable only after its rules are written into `LANGUAGE_SPEC.md`
> (Phase 0). Tests/TCK oracles must quote the revised spec, never the plan.

## Environment / build facts (verified)

- GraalVM for JDK 25 is at **`/opt/graalvm`** (set `GRAALVM_HOME`/`JAVA_HOME` to it;
  do NOT append a version subdir — the host JDK at `/opt/java` must never be used).
- Focused Maven: `JAVA_HOME=/opt/graalvm PATH=/opt/graalvm/bin:$PATH ./mvnw -pl language test -Dcheckstyle.skip=true`
- Full gates: `./build.sh` (JVM: package + corpus + `tck-check.sh` + `tck-run.sh`),
  `./build-native.sh` (native-image + corpus + `tck-run.sh` + `tck-differential.sh`).
  `./build-all.sh` = `build.sh && build-native.sh` — the final quality gate.
- Corpus lives in `language/tests/` (`.sol` + golden `.output`), NOT `examples/`.
- `./build.sh`/`build-native.sh` run the TCK. **`tck-check.sh` → `verify_regen.py`
  re-runs generators in `tck/tools/` that assert every requirement's
  `normativeQuotes` appear VERBATIM in `docs/LANGUAGE_SPEC.md`.** Any spec edit
  must keep those quotes verbatim (or migrate the affected requirements in the same
  commit). This is why Phase 0 is atomic.

## PHASE 1 — Function types  ✅ COMPLETE, gate-green

Confidence: 100%. `./build.sh` and `./build-native.sh` both pass (package, corpus
22/76, all 273 TCK requirements, coverage, JVM↔native differential).

Implemented:
- **Grammar** `Solvik.g4`: `typeRef` folds in `functionTypeRef` and the grouped
  `(func(...): R)?` form → function types parse in every written type position; the
  **analyzer** decides legality (doc §4.1 needs the structural nullability
  distinction, not source-text inference).
- **AST**: new `TypeRef` abstraction (`language/.../ast/declaration/TypeRef.java`)
  — an abstract class (a type ref is also an `AstNode` child), implemented by final
  `TypeRefNode` and standalone final `FunctionTypeRefNode.java`. ~17 type-position
  fields converted uniformly. `AstKind.FUNCTION_TYPE_REF` added. Passes
  `SolvikAstStructureTest` incl. `concreteNodeClassesAreFinal`.
- **Type model** `FunctionType.java`: canonicalization via
  `ConcurrentHashMap.computeIfAbsent` (thread-safe across Truffle contexts — was a
  race), structural `equals`/`hashCode`, contravariant-param/covariant-result
  `isSubtypeOf`, `Any`-top, `nullableView`, `substitute` through params/result,
  `superType()=Any` so unrelated function types **join to `Any`**. Source-style
  rendering `func(Integer): String` (doc §7.2).
- **Analyzer**: `resolveType(TypeRef)` dispatch; `resolveFunctionTypeReference`
  applies `(func)?`→nullable view; rejects function types as `is`/`as`
  (`TYPE_INVALID_TYPE_OPERAND`) and superclass (`SEM_INVALID_SUPERCLASS`) targets.
  Bare function refs still rejected with `TYPE_FUNCTION_AS_VALUE` (correct for P1).
- **Tests**: `SolvikFunctionTypeTest` (11 end-to-end: parse as param, nested,
  nullable grouped form, omitted return→Unit, signature result, generic arg,
  named-as-value still rejected, superclass/is/as rejected). Expanded
  `SolvikTypeModelTest` (variance/covariance/nullable/join/canonicalization).
  Updated `SolvikGenericsSemanticTest` `(T) -> T` → `func(T): T`.

Untracked new files: `TypeRef.java`, `FunctionTypeRefNode.java`,
`SolvikFunctionTypeTest.java` (+ this doc, + the untracked design doc).

## PHASE 0 — Normative baseline (spec + TCK revision)  ✅ COMPLETE, gate-green

Confidence: 100% on the surface described below. `./build-all.sh` passes (JVM package +
corpus + TCK conformance + self-tests + provenance, native image + corpus + conformance,
and the JVM↔native differential).

### Deviation from `FIRST_CLASS_FUNCTIONS.md` — reported, not silently adopted

AGENTS.md requires reporting rather than inventing where the design document and the
repository's authority conflict. One conflict arose and is recorded here.

The design document §4.8 has `toString`/`equals`/`hashCode` and the synthesized `Result`
operations bind as method references, and §11.1 step 6 retires the requirement that
rejects them (REQ-2309) along with corpus case SOL-TCK-0352. Doing that in this revision
would retire **five** already-passing normative oracles — REQ-1805 (`value.toString`
bare read), REQ-2309 (`r.isOk`), and corpus cases SOL-TCK-0283/0284/0285 — and would
make the *language* reject nothing where the design intends a value, while the phase that
would supply those values has not been written.

Instead, the specification adopted the narrower rule and the oracles stay live:

> Only a declared callable binds. The fixed language-defined universal members
> `toString`, `equals`, and `hashCode`, and the synthesized `Result` operations, are not
> bindable … and the same holds for a static method, a constructor, and an enum variant.

(`docs/LANGUAGE_SPEC.md`, "Bound method references"; the §6 required-diagnostic table
carries the matching `SOLV-TYPE-014` row.) Consequences, all deliberate:

* REQ-2309, REQ-1805, SOL-TCK-0283/0284/0285 and SOL-TCK-0352 remain **active and
  passing**; nothing was retired or weakened.
* Phase 6 therefore binds **declared class/interface instance methods only**. If the
  project later wants the document's wider rule, that is a further revision whose first
  act is retiring those five oracles on the record.
* `FIRST_CLASS_FUNCTIONS.md` itself is left unedited as the historical design record.

### What the revision actually contains

1. **Revision id** `2026.10-draft`; `docs/LANGUAGE_SPEC.md` header + revision-history
   paragraph.
2. **Spec rewrite** — new `### Function values` subsection under §6 (~420 lines):
   function-type syntax and the parenthesized-nullability rule, structural identity and
   contravariant/covariant assignability, the `Nothing`/nullability/`Any`-top/join rules,
   invocation (callee-first, left-to-right, arity-before-types, `SOLV-TYPE-029`/`002`/
   `003`), named function values and canonical identity, anonymous functions, explicit
   immutable capture, contextual generic instantiation, bound method references, the
   restricted bindable set, equality/hash/`func` display, non-reifiability (`SOLV-TYPE-
   025`), and interop executability — the last of which lives in §6, because the
   specification has no separate interop section. §3 gains function types in the
   identity-bearing list plus the fixed equality/hash/display prose; §6 "Callable arity"
   drops the no-first-class-values clause and states the indirect-call ordering. The three
   capture diagnostics are **specified in the table but not yet in `DiagnosticCode.java`**
   (added with Phase 4, per the plan's "no unused codes" rule).
3. **ARCHITECTURE.md** — new "Function Values and Indirect Calls" section: the direct
   callable symbol vs the guest function value, the compile-time facts table, capture
   analysis ordering and anti-cascading, the sealed runtime representation (canonical /
   anonymous / bound), lowering rules including hidden environment arguments and no
   primitive boxing, instrumentation/source-section requirements, and the closed-world
   statement. The Type System section records `FunctionType` as the only structural type.
4. **TCK migration, in place** (pre-release, so no archiving): corpus dir renamed to
   `2026.10-draft` (~900 files), schema enums, profile, protocol docs, `versions.py`,
   and every committed generator's `SPEC_VERSION`.
5. **Quote collisions resolved against the revised text**, not by deletion:
   `REQ-0508`'s superseded clause is dropped from its quote with a note in
   `oracleNotes`; `REQ-1701`'s summary now names function types among the bearing types
   (its tests are unchanged — none mentions a function type) with a pointer to REQ-3307.
   The two §3 bare-member-read sentences were kept verbatim, which is what allows the
   deferral statement to live only in the new §6 prose.
6. **New requirements + 9 conformance programs** (`tck/tools/gen35.py`, self-contained):
   REQ-3300 written positions + spelling sameness; REQ-3301 generic arguments; REQ-3302
   static property types incl. the reference zero value; REQ-3303 `null` → non-null
   function type (`SOLV-TYPE-001`); REQ-3304 matching *inside* a nested function type;
   REQ-3305 `(func(…)?` vs `func(…): R?`; REQ-3306 non-reifiable `is`/`as`
   (`SOLV-TYPE-025`). SOL-TCK-0417..0425. Every oracle was run first and then confirmed
   against the rule it cites; rejections the sections do not name a code for assert the
   diagnostic **family** only, per TCK.md §6.
   `tck/tools/gen36.py` records the five obligations the revision adopts but cannot test
   yet — REQ-3307 identity-bearing/equality/hash/display, REQ-3308 interop executability,
   REQ-3309 variance + no numeric widening, REQ-3310 `Any`-top/join/invariance,
   REQ-3311 structural comparison confined to function types — each `untested-portable`
   with a rationale naming the witness the next phase owes. **Phase 2 must convert each
   of these five to a tested record as it lands**, or full-profile conformance stays
   blocked by design.
7. **Counts**: 285 requirements / 425 manifests / coverage 269-285 / 2423 self-test
   assertions / 1876 oracle quotes / 334 self-contained directories. `tck/README.md`
   documents the revision and the deferral; `TCK.md` no longer lists first-class
   functions as deferred. `tck/tools/sync_counts.py` recomputes every guarded figure
   from `validate` + a self-test run + `verify_regen.py` and then re-runs the guard, so
   a batch no longer needs twenty hand edits; `tck/tools/README.md` explains why it
   refuses to sync over a genuinely failing module.
8. **Diagnostics**: none added to `DiagnosticCode.java` (Phase 0 needs none; the three
   capture codes arrive with Phase 4).
9. **`docs/SEMANTIC-TEST-COVERAGE.md`** gains §2.5.1 with the function-type rows, naming
   the real test methods and marking the three genuine gaps rather than implying coverage.
10. `./build-all.sh` green (see the run in the commit that closes this phase).

### Facts Phase 2 needs that Phase 0 established

* `SOLV-TYPE-025` (`TYPE_INVALID_TYPE_OPERAND`) is **specification-named** for a
  function-typed `is`/`as` target; the analyzer already emits it and REQ-3306 pins it.
* Binding a property of function type is legal; binding a declaration to initialize one
  is still `SOLV-TYPE-014`. That single code is what Phase 2 narrows.
* Interface implementation matching compares function types **structurally, through
  nested constructors** (REQ-3304), so a Phase 2 change to type identity will show up
  there.
* `tck/tools/gen35.py` and `gen36.py` are registered in `verify_regen.py`'s
  `SELF_CONTAINED` list; the floor is 334.
* A one-line `class C { … }` does not parse in this language (statement-termination
  behavior); corpus programs must use the multi-line form.

## PHASE 2 — Named top-level functions as values  ⬜

- Bare / module-qualified / predeclared function references become canonical
  function values (analyzer currently rejects at
  `SolvikSemanticAnalyzer.java:2702` `TYPE_FUNCTION_AS_VALUE`, 3721, 3754 — these
  become value-producing for top-level, keep for member reads until Phase 6).
- Canonical runtime value per declaration (one identity). New runtime type under
  `language/.../truffle/object/` (like `SolvikEnumValue`), built on a Truffle
  `CallTarget`. Closed-world: register for native-image.
- Indirect invocation (call through a function-typed value) with arity/type/eval
  order/result rules; **preserve** the existing direct-call path.
- Interop: non-null function value reports `executable`. `toString()` = `func`.
  Add to equality/identity/hash tables + `IdentityDomain`.
- Preserve direct-call optimization for statically-known targets; no primitive
  boxing for function values.
- Exit: named refs stored/passed/returned/compared/printed/invoked on JVM+native.
- TCK: `FCF-NAMED-REFERENCE`, `FCF-QUALIFIED-REFERENCE`, `FCF-INDIRECT-CALL`,
  `FCF-FUNCTION-IDENTITY`, `FCF-FUNCTION-EQUALITY-HASH`, `FCF-FUNCTION-DISPLAY`
  (runtime oracles) + corpus + generator + coverage docs (`LOWERING-TEST-COVERAGE.md`).

## PHASE 3 — Anonymous non-capturing functions  ⬜
- `anonymousFunctionExpr` grammar: `func (params): R { block }` (and capture form
  later). New identity per evaluation; callable-return rules (return from the
  function body; `break`/`continue` cannot cross the boundary).
- TCK: `FCF-ANONYMOUS`.

## PHASE 4 — Explicit immutable closure capture  ⬜
- `captureList: [ item, ... ]` grammar (doc §7.1). Capture immutable locals/params/
  `this` only, at creation in source order; object refs observe later mutation;
  closures valid after creator returns.
- Add diagnostics `SEM_MUTABLE_CAPTURE`/`SEM_UNLISTED_CAPTURE`/`SEM_INVALID_CAPTURE`
  (SOLV-SEM-057/058/059) + capture analysis in the analyzer; no shared AST state.
- TCK: `FCF-CAPTURE-*` (list/val/object/var-reject/omission-reject/invalid-reject/lifetime).

## PHASE 5 — Generic function values  ⬜
- Contextual monomorphic instantiation of a generic function/method reference under
  an expected function type; `TYPE_CANNOT_INFER` (`SOLV-TYPE-030`) when unconstrained.
  Recorded in `CheckedProgram` (no runtime type dispatch).
- TCK: `FCF-GENERIC-INSTANTIATION`, `FCF-GENERIC-NO-TARGET`.

## PHASE 6 — Bound method references  ⬜
- `receiver.method` value (receiver evaluated once, retained); preserves virtual
  dispatch; interface defaults/delegates; safe-call → null/nullable; `super.m` →
  immediate superclass. Resolves the REQ-2309/`SOLV-TYPE-014` tension for member
  reads (Result-operation bare reads re-homed in Phase 0 step 5).
- TCK: `FCF-BOUND-*`.

## PHASE 7 — Integration, examples, final validation  ⬜
- Runnable example `language/tests/FirstClassFunctions.sol` (+ golden `.output`) —
  must cover ALL SIX capabilities together (can't be added before Phase 6 works).
- `README.md` feature summary; finish `SEMANTIC/LOWERING-TEST-COVERAGE.md`; polyglot
  interop integration suite (kept OUT of the portable TCK — the launcher protocol
  doesn't expose guest function values to a host).
- Full `./build-all.sh` + JVM/native differential green = done.

## Quick "where things live" index
- Grammar: `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`
- AST type refs: `language/src/main/java/org/solvik/ast/declaration/{TypeRef,TypeRefNode,FunctionTypeRefNode}.java`
- Function type model: `language/src/main/java/org/solvik/type/FunctionType.java`
- Analyzer (5600+ lines): `language/src/main/java/org/solvik/semantic/SolvikSemanticAnalyzer.java`
  (`resolveType`/`resolveFunctionTypeReference` ~5450; value rejections 2702/3721/3754)
- Lowering: `language/src/main/java/org/solvik/lowering/SolvikLowering.java`
- Runtime value objects (add function value here): `language/src/main/java/org/solvik/truffle/object/`
- Diagnostics: `language/src/main/java/org/solvik/diagnostic/DiagnosticCode.java`
- TCK: `tck/requirements/requirements.json`, `tck/schemas/*.schema.json`,
  `tck/profiles/full-language.profile.json`, `tck/corpus/2026.09-draft/`,
  generators `tck/tools/gen*.py`, self-tests `tck/tests/`, validate `tck/runner/tck_cli.py`.
