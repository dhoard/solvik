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

## PHASE 2 — Named top-level functions as values  ✅ COMPLETE, gate-green

**Delivered.** `SolvikFunctionValue` (canonical per declaration, `InteropLibrary`
`isExecutable()` true, reference-identity equality/hash, `func` display),
`SolvikFunctionValueNode` (constant canonical-value node), `SolvikIndirectCallNode` +
`SolvikFunctionDispatchNode` (monomorphic `DirectCallNode` → polymorphic `IndirectCallNode`),
analyzer `functionReferences` / `indirectCalls` maps, `checkIndirectCall`, and
`checkCallThroughFunctionProperty`. Predeclared `print`/`println`/`exit` were given real lowered
call targets so they are usable as values. The direct-call path is untouched: taking a function as
a value does not route its own direct calls through a value
(`SolvikFunctionValueTest.takingAFunctionAsAValueDoesNotRouteItsDirectCallsThroughAValue`).

**Deliberate deferrals.** Generic function references still report `SOLV-TYPE-014`
(`SolvikFunctionTypeTest.genericFunctionReferenceIsRejected`) — that is Phase 5's subject, not a
gap here. Bound method references are Phase 6.

**TCK.** The Phase 0 conversion obligation is discharged for four of the five records:
`tck/tools/gen37.py` owns REQ-3307, REQ-3309, REQ-3310, REQ-3311 as `tested` (10 new tests,
SOL-TCK-0426..0435) and `gen36.py` retains only REQ-3308, which stays `untested-portable`
permanently as far as the corpus is concerned — its observable is an embedding host calling a guest
value, so its witness belongs to the embedded-API suite (Phase 7).

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
- TCK: originally listed as `FCF-NAMED-REFERENCE`, `FCF-QUALIFIED-REFERENCE`, `FCF-INDIRECT-CALL`,
  `FCF-FUNCTION-IDENTITY`, `FCF-FUNCTION-EQUALITY-HASH`, `FCF-FUNCTION-DISPLAY` (runtime oracles) +
  corpus + generator + coverage docs (`LOWERING-TEST-COVERAGE.md`). Those ids were placeholders — the
  inventory names requirements `REQ-NNNN` only — and Phase 2's actual delivery is `SOL-TCK-0426..0435`
  under `REQ-3307`/`REQ-3309`/`REQ-3310`/`REQ-3311`, written by `tck/tools/gen37.py`.

## PHASE 3 — Anonymous non-capturing functions  ✅ COMPLETE, gate-green

**Delivered.** `anonymousFunctionExpr: FUNC LPAREN parameterList? RPAREN (COLON typeRef)? block`
in `primary` — the capture list is deliberately absent until Phase 4, so a written capture list is a
parse error rather than an accepted-then-rejected form, which is what the spec's "a non-capturing
anonymous function is written `func(...)`" requires of this revision. `AnonymousFunctionExprNode`,
`FunctionSymbol.anonymous(...)` (carries its body directly; no declaration, no scope entry),
`SymbolTable.enterFunctionBoundaryScope()` with a boundary-aware `resolveLocalChain` and
`hiddenAcrossFunctionBoundary`, `checkAnonymousFunction`, and
`lowerAnonymousCallable`/`lowerAnonymousFunction` over the extracted `lowerCallableBody`.
`SolvikAnonymousFunctionValueNode` allocates a fresh `SolvikFunctionValue` per evaluation over one
shared `RootCallTarget` — fresh identity, shared code.

**Function boundary.** `checkCallable` now saves and restores every piece of per-body state
(`loopDepth`, `breakDepth`, `currentClass`/`currentInterface`, `typeParameterScope`, receiver
availability, definite-initialization state) instead of resetting it, because an anonymous body is
checked from inside an enclosing one. So `return` returns from the body, `break`/`continue` cannot
cross the boundary, an enclosing method's type parameters are not visible, and an enclosing receiver
is recorded without being reachable. Globals stay visible because they resolve outside the lexical
chain; a top-level `val`/`var` is hidden, because the spec makes it "a local of the implicit main,
not a global".

**`SEM_UNLISTED_CAPTURE` (SOLV-SEM-058) is live**, reported on the body reference for a read or a
write of a hidden outer binding and for `this`. It is re-homed by Phase 4 onto "omitted from the
capture list" semantics, which is what the spec's wording assumes; until then an unlisted use is the
only capture-shaped use the language can contain. `this` with no enclosing receiver anywhere keeps
`SOLV-RESOL-005`, and a name no enclosing function declares stays `SOLV-RESOL-001`.

**Tests.** `SolvikAnonymousFunctionTest` (30 tests), regression corpus
`22-anonymous-functions.sol` (verified on JVM and native), parser-test update
(`missingFunctionNameIsRejected` → `parseOk`, plus `missingMemberFunctionNameIsRejected` since
class members must still be named), and `language/tests/diagnostics/SEM-058.sol`.

**Known limitation, pre-existing and shared with `ifExpr`:** semicolon insertion does not fire inside
call parentheses, so an anonymous function written as a call argument needs an explicit `;` in its
body or must be bound to a `val` first. The spec's own examples use the binding form.

**Not done here:** a TCK batch for anonymous functions. The highest requirement id the inventory
carries is `REQ-3311`, and no id is allocated for the anonymous-function behaviour, so its oracles are
carried by `SolvikAnonymousFunctionTest` and the regression corpus. A batch must be opened (next ids
`REQ-3312` upward) before Phase 7 closes the feature, or the feature ships with behaviour the
inventory does not claim.

## PHASE 4 — Explicit immutable closure capture  ✅ COMPLETE, gate-green

**Delivered.** `anonymousFunctionExpr: FUNC (LBRACKET captureItemList RBRACKET)? LPAREN
parameterList? RPAREN (COLON typeRef)? block`, with `captureItemList: captureItem (COMMA captureItem)*`
and `captureItem: Identifier | THIS`. The list's *shape* is grammar and its *content* is not: an item
must resolve at the closure-creation site to an eligible binding, which the parser cannot know, so every
naming rule is the semantic layer's, which can locate the offending item. `func [](...)` never parses,
because the non-empty list is required rather than optional — the spec makes an empty list a parse error
— and `captureItemList` therefore mirrors `parameterList`, whose body is likewise never empty. `this` is
a token, not an `Identifier`, so it is an explicit alternative. An item is one token wide, which is the
structural reason capture aliases and capture expressions are absent: the grammar has no shape for them.

`CaptureItem` (AST record: name + span, unresolved), `CapturedValue` (semantic record: name, type, source
binding, item span), `FunctionSymbol.AnonymousCallable` (body + captures + rejected names, one value so
"is anonymous" stays a single derived test), `SolvikCapturingFunctionValueNode` (reads each captured
value at creation), and `SolvikFunctionValue.capturing(...)` / `withCapturedState(...)`.
Non-capturing closures keep `SolvikAnonymousFunctionValueNode` and allocate no array.

**`[this]` is an ordinary captured value, not `SolvikFunctionValue.receiver`.** A closure body has no
receiver, so `this` in one is a captured value like any other. `receiver` stays reserved for bound methods
(Phase 6), and the choice is what makes nested `[this]` forwarding work: lowering writes the captured
receiver into `thisSlot`, so an inner closure reads its enclosing closure's slot and one receiver threads
through arbitrarily many intervening closures with no extra mechanism. `CapturedValue.ofReceiver` gives
that value a synthetic `VariableSymbol` declared in no scope — it is a frame-slot key, and `this` being a
keyword means it can never collide with an identifier.

**Transitivity is structural, not a rule to remember.** Capture items resolve through
`SymbolTable.resolveLocalChain`, which stops at the innermost function boundary. So the only outer name an
inner item can find is one the enclosing closure itself holds, and "a name used in an inner capture list
counts as a use by the enclosing closure, so every intervening closure must list and forward that value
explicitly" follows from no name above the boundary being reachable at all. A one-link break is
`SOLV-RESOL-001` on the inner item plus `SOLV-SEM-058` on the body use
(`SolvikCaptureTest.anInnerCaptureItemNamingStateBeyondTheEnclosingClosureIsRejected`).

**Diagnostic placements, all three per spec.** `SOLV-SEM-057` on the capture item *and* on a body read or
write of a name the list named as a `var` — one code, two placements, so an implementation reporting only
the first fails `aBodyReadOfACapturedVarNameIsRejectedToo` which asserts two diagnostics. A `var` the list
never named stays `SOLV-SEM-058`, never silently converted. `SOLV-RESOL-002` for a repeated item or one
naming its own parameter, reported at the *capture item* (captures are declared after parameters, so the
diagnostic points at the defect a reader would edit) — including `[this, this]`. `SOLV-RESOL-001` for an
unknown item, including an item naming a top-level function or class, which resolve in the root scope the
walk stops at. `SOLV-RESOL-005` for `this` with no receiver.

**`SOLV-TYPE-008` is reachable now, and this is the phase that made it so.** §6 says listing the binding
being initialized in a capture list "is an ordinary read-before-initialization error
(`SOLV-TYPE-008`), because the value does not exist when its initializer is evaluated". Capture items
resolve before the binding is declared, so resolution alone reports `SOLV-RESOL-001` and the sentence
would be false. `checkLocalDecl` now pushes the name under initialization onto `pendingDeclarations`, and
`resolveCaptures` consults it for that one shape. It is deliberately *not* applied to ordinary reads: a
shadowed outer binding really is what a read sees, and converting every shadowing declaration into a
read-before-initialization error would break ordinary programs to serve a rule stated only about capture
items. `docs/SEMANTIC-TEST-COVERAGE.md` §5 A1 and §3 previously declared this code dead; both are
corrected.

**`SOLV-SEM-059` is unreachable in the current grammar and is allow-listed, with the analysis written
down.** A capture item is resolved by `resolveLocalChain`, whose scopes hold nothing but
`VariableSymbol`s (parameters, locals, `for-in` and pattern bindings, a catch binding, capture bindings),
so no item can resolve to a non-capturable symbol; an item naming a declaration resolves to nothing and is
`SOLV-RESOL-001`, which is the code §6 assigns an unknown item. The analyzer branch is kept and annotated
rather than deleted, because a revision declaring a non-variable symbol into a function scope makes it
correct.

**Meta-test hardening found while doing the above.** `SolvikDiagnosticCodeCoverageTest` scans test sources
to decide coverage, and that set included *itself* — so a code merely named in its own prose counted as
covered, and `TYPE_INVALID_CHARACTER_LITERAL` was passing on nothing. Now: (1) this file is excluded from
the coverage scan; (2) `thisFileDoesNotCoverAnyDiagnosticCodeItself` forbids non-allow-listed constant
names in this file, so prose uses the stable string (`SOLV-TYPE-008`); (3)
`everyAllowListedCodeIsStillUncoveredElsewhere` fails on a stale entry whose reason has gone stale. All
three were verified by mutation — naming a covered code in the prose, and adding an entry for a covered
code, each fails the build. `TYPE_INVALID_CHARACTER_LITERAL` was allow-listed with the real two-fact reason
(the lexer token yields at most two content characters, and the analyzer's preceding branches cover both
shapes it can yield).

**Frame layout** is `[receiver?, captures..., params...]`, matching `withCapturedState` exactly; both
halves of that agreement are in `lowerCallableBody`, which appends capture slots to `parameterSlots` in
the order the value stores its captured array. `CapturedValue` cannot be built inconsistently: name and
type are read off the source binding and cross-checked in the canonical constructor.

**Tests.** `SolvikCaptureTest` (36 tests) — the four claims that only a running program can establish are
value-vs-storage (`aCapturedObjectReferenceObservesLaterMutation`,
`aCapturedObjectIsTheSameObjectTheBodyReceives`), lifetime
(`aClosureRemainsValidAfterItsCreatorReturns`), no-flattening
(`aClosureCapturingAClosureRetainsTheCapturedClosuresOwnEnvironment` — a flattening implementation prints
the same numbers, so the witness is that the outer closure never names the inner name), and explicit
transitivity from both sides. Plus regression corpus `23-captures.sol` (15 golden lines, hand-derived then
confirmed, JVM and native), `language/tests/diagnostics/SEM-057.sol`, and a corrected `SEM-058.sol`
fixture comment — it had claimed "this revision implements no capture list", which Phase 4 made false.
`SolvikDiagnosticCodeCoverageTest` 1 → 3 tests. Suite total 2435; `./build-all.sh` green including the
JVM/native differential.

**Not done here:** the TCK batch. Ids `REQ-3312` upward are reserved for the anonymous-function and
capture obligations; the capture behaviour is currently carried by `SolvikCaptureTest` and the regression
corpus only.

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
