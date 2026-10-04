# Lowering Layer Test Coverage Plan

**Status:** Implementation baseline for achieving 100% confidence in the Solvik lowering layer.
**Scope:** `SolvikLowering` (typed IR → Truffle AST) and all consumed Truffle node classes.
**Authority:** `docs/ARCHITECTURE.md` "Required Pipeline", "Truffle Backend"; `docs/LANGUAGE_SPEC.md` (normative semantics).

---

## 1. Layer Boundary

The lowering layer consumes `CheckedProgram` (semantic result) and produces `LoweredProgram` (executable Truffle AST). Per `ARCHITECTURE.md`:

> Lowering runs only after all error diagnostics have been resolved; a program with compile-time errors must never produce an executable call target.

This means:
- **Positive tests:** Compile-checked programs lowered to executable Truffle nodes — validated through polyglot `Context.eval` execution with golden output assertions.
- **Negative tests:** Programs with semantic errors that must **never** reach lowering (validated in the semantic layer). Lowering itself has no negative tests because it only receives error-free `CheckedProgram`s.

---

## 2. Current Coverage Baseline

| Class | Line % | Branch % | Method % | Missed Methods |
|---|---|---|---|---|
| `SolvikLowering` | 95.1% | 88.8% | 81.8% | 16 |
| `SolvikLowering.CollectionContext` | 100% | 100% | 100% | 0 |
| `LoweredProgram` | 66.7% | 0% | 40% | 3 |
| `SolvikNumericBinaryNode` | 95.6% | 90.0% | 100% | 0 |
| `SolvikRegexMatchReadNode` | 56.5% | 31.2% | 33% | 1 |
| `SolvikConvertNode` | 95.6% | 92.9% | 100% | 0 |
| `NumericTypes` (widening relation) | 96.8% | 87.5% | 100% | 0 |
| `SolvikList` | 79.1% | 70.6% | — | — |
| `SolvikMap`/`Stack`/`Set` | 85–87% | 73–86% | — | — |
| `SolvikStatementNode` | 82.4% | 58.3% | 60% | 0 |
| `SolvikExpressionNode` | 27.8% | — | 44% | 3 |
| `SolvikWriteLocalVariableNode` | 23.1% | — | 50% | 2 |

---

## 3. SolvikLowering Method Coverage Matrix

### 3.1 Statement Lowering

| Method | Spec Feature | Positive Test | Negative Test | Status |
|---|---|---|---|---|
| `lowerBlock` (§21.2) | Block statements | `SolvikExpressionOrientedExecutionTest` | N/A (statement, not user-observable error) | ✅ |
| `lowerLocalDecl` (§6) | Local val/var declarations | `SolvikClassExecutionTest` | N/A | ✅ |
| `lowerAssign` (§2/§7) | val/var assignment | `SolvikClassExecutionTest` property assignments | **DONE** — `SolvikClassExecutionTest.varLocalWriteLoweringUpdatesTheFrameSlotAtRuntime()` exercises `var counter = 5; counter = counter + 1; ...` at runtime, confirming the SolvikWriteLocalVariableNode slot-reuse path (docs/LANGUAGE_SPEC.md section 7) | ✅ |
| `lowerReturn` (§6) | return with/without value | `SolvikExecutionTest` | N/A | ✅ |
| `lowerIf` (§17) | if/else statements | `SolvikExpressionOrientedExecutionTest` | N/A | ✅ |
| `lowerWhile` (§17) | while loop | `SolvikExecutionTest` | **GAP** — `SolvikExecutionTest.whileLoopWithBreakAndContinue` uses a simple scalar condition (`i < 6`); no execution-level test for a compound `&&`/`||` boolean condition exercising the lowering of complex while conditions | ⚠️ |
| `lowerFor` (§17) | ~~three-clause for~~ removed | `SolvikControlFlowParserTest` | The three-clause `for` was removed by the physical-line revision; the parser reports `SOLV-PARS-011` at the keyword and there is no lowering for it | ✅ (by removal) |
| `lowerForIn` (§17) | range for-in (`...`, `..<`, `..>`) | `SolvikRangeExecutionTest` | `SolvikRangeSemanticTest` (semantic, not lowering) | ✅ |
| `lowerSwitch` (§13) | switch statement | `SolvikSwitchExecutionTest` | **GAP** — no execution test exercising an empty switch body (zero cases) at runtime; all current `SolvikSwitchExecutionTest` tests use non-empty case lists | ⚠️ |

### 3.2 Expression Lowering

| Method | Spec Feature | Positive Test | Negative Test | Status |
|---|---|---|---|---|
| `lowerExpression` (dispatch) | All expression kinds | Various execution tests | **GAP** — no test for all expression kind branches (paren, etc.) | ⚠️ |
| `lowerNameReference` (§6) | Local variable read | `SolvikExecutionTest` | N/A (only called with initialized variables) | ✅ |
| `lowerLongLiteral` (§1) | Long literals | `SolvikNumericTest` | **GAP** — no test for `L` suffix literal in expression context | ⚠️ |
| `lowerFloatingLiteral` (§1) | Floating literals | `SolvikNumericRuntimeTest` | **GAP** — no test for float vs double literal lowering (F suffix) | ⚠️ |
| `decodeCharacter` (§15) | Character escape decoding | `SolvikCharacterLiteralExecutionTest` | **GAP** — no test for unsupported escape (should never reach here) | ✅ (dead code confirmed) |
| `lowerThis` (§7) | this reference | `SolvikClassExecutionTest` | N/A (only valid in instance methods/constructors) | ✅ |
| `lowerMemberRead` (§7/§10/§14) | Property/member access | `SolvikClassExecutionTest`, `SolvikCollectionsTest`, `SolvikRegexExecutionTest` | **DONE** — `findReturnsTheFirstMatchWithOffsetsAndGroups()` executes `m.value`, `m.start`, `m.end`, `m.groupCount` at runtime (docs/LANGUAGE_SPEC.md section 14) |
| `lowerTypeTest` (§18) | `is` type test | `SolvikTypeTestRuntimeTest` | **GAP** — no dedicated execution test exercising `is` against an erased type argument at runtime and verifying the reified-vs-erased behavior; `SolvikRegexSemanticTest` covers `is` narrowing semantically but not as a runtime execution assertion |
| `lowerCast` (§18) | `as` checked cast | `SolvikTypeTestRuntimeTest` | **GAP** — no dedicated execution test for `as` casting to an interface type at runtime; the checked-cast failure path is exercised semantically (`SolvikGenericsNegativeTest.erasedTypeTest`) but not as a runtime execution assertion |
| `lowerUnary` (§3) | Unary ! and - | `SolvikSemanticTest`, `SolvikNumericTest` | **GAP** — no test for `!` on non-Boolean (should be semantic error, not lowering) | ✅ (semantic layer) |
| `lowerBinary` (§3) | All binary operators | `SolvikConcatTest`, `SolvikNumericTest`, `SolvikNumericWideningTest` (operand widening via `lowerCoerced`), `SolvikEqualityTest`, `SolvikIdentityTest`, `SolvikExecutionTest` | **DONE** — `===`/`!==` identity true-branches executed at runtime by `SolvikIdentityTest` (`missing === null` / `present === null`); `&&`/`||` short-circuit branches executed by `SolvikExecutionTest` (`1 + 1 == 2 && 3 > 2`, `false || false`, `true || (1 / 0 == 0)`); value equality `==`/`!=` by `SolvikEqualityTest` |
| `lowerCall` (§6/§7) | Method/function calls | `SolvikClassExecutionTest`, `SolvikToStringExecutionTest`, `SolvikModuleTest`, `SolvikCollectionsTest`, `SolvikRegexExecutionTest` | **DONE** — all call form branches executed at runtime: qualified `math::add(1, 2)` by `SolvikModuleTest.qualifiedFunctionCallResolves`; collection construction via `SolvikCollectionsTest`; Regex creation via `SolvikRegexExecutionTest`; built-in member calls across `SolvikEqualityTest`/`SolvikHashCodeTest` |
| `lowerConstruction` (§7) | Class constructor calls | `SolvikClassExecutionTest`, `SolvikGenericTypeArgumentTest` | **DONE** — `SolvikGenericTypeArgumentTest.explicitTypeArgumentOnAConstructionExecutes()` executes `Box<Integer>(5)` at runtime, confirming generic construction with explicit type args lowers correctly |
| `lowerRegexMemberCall` (§14) | Regex/RegexMatch member calls | `SolvikRegexExecutionTest` | **DONE** — `findReturnsTheFirstMatchWithOffsetsAndGroups()`, `findAllReturnsEveryMatchInSourceOrder()`, `replaceReplacesAllMatchesAndTreatsTheReplacementAsLiteralText()`, and `matchesRequiresTheCompleteInput()` cover the `find`/`findAll`/`replace`/`matches` branches at runtime |
| `lowerEnumConstruction` (§12) | Enum variant construction | `SolvikEnumExecutionTest` | **DONE** — `genericVariantConstructionExecutes()` and `enumVariantPayloadsAreProducedAtRuntimeByMatchLowering()` execute value-carrying variant construction and match-time payload production at runtime |
| `lowerMatch` (§12) | match expression | `SolvikMatchExecutionTest` | **GAP** — no test for empty match (should be semantic error) | ✅ (semantic layer) |
| `lowerPattern` (§12) | Match pattern lowering | `SolvikMatchExecutionTest` | **GAP** — no test for wildcard vs binding vs enum patterns at runtime | ⚠️ |
| `lowerBlockExpr` (§21.2) | Block expressions | `SolvikExpressionOrientedExecutionTest` | **GAP** — no test for empty block expression (should be semantic error) | ✅ (semantic layer) |
| `lowerValueBlock` (§21.2) | Value-required blocks | `SolvikExpressionOrientedExecutionTest` | **DONE** — `blockExpressionComputesValue()` executes `{ val base = 20; base + 22 }`, `{ 42 }`, and `{ 42; }` at runtime, confirming tail-result value production for value-required blocks |
| `lowerIfExpr` (§21.4) | if expressions | `SolvikExpressionOrientedExecutionTest`, `SolvikExecutionTest` | **DONE** — `else if` chain executed at runtime by `SolvikExpressionOrientedExecutionTest` (line 82 `else if (value == 0)`) and `SolvikExecutionTest` (lines 111/119 `else if (x < 10)` / `else if (y < 10)`) | ✅ |
| `lowerSwitchExpr` (§21.5) | switch expressions | `SolvikExpressionOrientedExecutionTest`, `SolvikSwitchExecutionTest` | **DONE** — `regexCaseDispatchInExpressionSwitch()` executes a switch *expression* with `case regex` branches at runtime (`kind("123")` → `"number"`), confirming expression-form switch lowering with regex cases |
| `lowerConversion` (§4) | Numeric type conversions | `SolvikConversionRuntimeTest` | **DONE** — `outOfRangeConversionOfAValueIsARuntimeError()` executes `Byte(300)`, `Short(32768)`, `Integer(2147483648L)` at runtime and asserts the `"out of range"` arithmetic failure, covering out-of-range conversion rejection at runtime |
| `lowerMethodCall` (§7/§8) | Virtual method dispatch | `SolvikInheritanceExecutionTest`, `SolvikInterfaceExecutionTest` | **DONE** — `super.speak()` (line 124) and `super.name` (line 189) executed at runtime in `SolvikInheritanceExecutionTest`; root-default `super.equals(other)`/`super.hashCode()` exercised via `SolvikEqualityTest.superEqualsReachesTheRootIdentityDefault()` |
| `lowerCollectionArguments` (§11) | Collection construction args | `SolvikCollectionsTest` | **DONE** — `mapConstructorTakesKeyValueEntries()` executes `Map(1: "one", 2: "two")` at runtime, verifying Map key:value entry flattening and argument collection lowering |
| `lowerArguments` (generic) | Argument lowering | All call tests | N/A (covered by positive tests) | ✅ |
| `lowerCallableBody` (§6) | Build one callable's frame, body, and root node | `SolvikClassExecutionTest`, `SolvikModuleTest`, `SolvikInheritanceExecutionTest`, `SolvikFunctionValueTest` | N/A (every callable's lowering is exercised by positive execution) | ✅ |
| `lowerBuiltinCallable` (§6) | Give the predeclared callables real lowered targets so they are usable as values | `SolvikFunctionValueTest.aPredeclaredFunctionIsUsableAsAValue()` executes `println` through a stored function value | — | ✅ |
| `lowerAnonymousCallable` / `lowerAnonymousFunction` (§6) | Anonymous function expression — build its own frame and return one shared `RootCallTarget` plus a node that allocates a fresh value on every evaluation | `SolvikAnonymousFunctionTest` — `anAnonymousFunctionInitializesABindingAndIsInvoked`, `anAnonymousFunctionMayContainAnother` (a nested body lowers while an enclosing body's frame is still being built and both run), `twoEvaluationsOfOneAnonymousFunctionAreDistinct` (one target, many values), `reReadingABindingPreservesTheValueItStored` (no memoized value on the node) | `anAnonymousFunctionValueRendersAsFunc` proves the debug name the lowering chooses never reaches a rendering; `aValueReturningAnonymousFunctionNeedsAReturnOnEveryPath` proves a body that lowers without a return path is rejected before lowering | ✅ |
| Function-reference branch of `lowerExpression` (§6) | One canonical value node for a named reference, reached both from a bare name and a module-qualified name | `SolvikFunctionValueTest.referencesToOneDeclarationShareOneIdentity`, `.aQualifiedReferenceIsTheSameValueAsTheUnqualifiedName`, `.aFunctionValueStoredAsAnyStillRendersAsFunc` | — | ✅ |
| Indirect-call lowering (§6) | `SolvikIndirectCallNode` plus the `SolvikFunctionDispatchNode` monomorphic-to-polymorphic chain | `SolvikFunctionValueTest.invocationEvaluatesTheCalleeThenTheArgumentsLeftToRight`, `.aStoredFunctionValueInvokesItsDeclaration`, `.aFunctionValueIsPassedAndReturned`, `.oneIndirectCallSiteServesEveryKindOfFunctionValue` (one call site is driven through a canonical named value, an anonymous value, a capturing closure, and a bound method value, interleaved, so the chain is observed becoming polymorphic rather than only monomorphic) | `anIndirectCallWithTheWrongArityIsRejected`, `.anIndirectCallWithAnIncompatibleArgumentIsRejected` (semantic, so an indirect call never lowers a bad arity or argument type) | ✅ |
| `lowerCallable` frame-state save/restore (§6) | An anonymous body lowering inside an enclosing body must leave the outer frame, slot map, and `this` slot intact | `SolvikAnonymousFunctionTest.aBodyDeclarationMayShadowAnOuterBinding()` executes an anonymous body written inside a method-like body and still reads the outer local correctly afterwards; `breakInsideTheBodysOwnLoopIsLegal()` runs a body whose own loop breaks, inside nothing | **GAP** — no dedicated assertion that an anonymous function written between two uses of an enclosing local leaves that local readable in the enclosing body's later statements (partly covered by `aBodyDeclarationMayShadowAnOuterBinding`) | ⚠️ |

### 3.3 Frame and Source Helpers

| Method | Spec Feature | Positive Test | Negative Test | Status |
|---|---|---|---|---|
| `allocateSlot` | Frame slot allocation | Internal to lowering | **GAP** — no test for duplicate slot reuse (covered by positive tests) | ✅ (internal) |
| `propertyKey` (§7) | Property key generation | `SolvikClassExecutionTest` | **GAP** — no test for property key collision (should be internal invariant) | ✅ (internal) |
| `kindOf` (§10) | Frame slot kind mapping | `SolvikKindOfTest` | **DONE** — reflectively exercises every Solvik type→`FrameSlotKind` mapping in `SolvikKindOfTest` | ✅ (filled)
| `setSource` (§20) | Source section attachment | `SolvikLineColumnTest` | **GAP** — only tested via diagnostics; no test for source section correctness at runtime | ⚠️ |

---

### 3.4 Function-value runtime and the program boundary

The value object and the lowered call targets behind it are where a function-value claim stops being a
shape question and becomes an execution question: which target a value carries, what arguments reach that
target, and what a host or a tool sees when it reaches through the value. Phases 2–6 put the lowering rows
for the producing expressions in 3.2; this section covers the runtime surface those rows hand off to, and the
host boundary and tool-visible call stack that Phase 7 closed.

| Method / behavior | Spec feature | Positive test | Negative test | Status |
|---|---|---|---|---|
| `SolvikFunctionValue.target()` resolves the declared handle, so the canonical value of a named reference carries the declaration's target (the value has no target field of its own) | §6 "Function values": one canonical value per declaration, invoking it runs the declaration | `SolvikFunctionValueTest.aStoredFunctionValueInvokesItsDeclaration`, `.referencesToOneDeclarationShareOneIdentity`; `SolvikInteropTest.hostExecutionOfANamedFunctionValueInvokesTheDeclarationsOwnTarget` (asserts `value.target() == declaration.callTarget()`) | — | ✅ |
| `SolvikFunctionValue.withCapturedState` assembles the frame array a call receives: receiver first when the value has one, then captures in capture-list order, then the caller's arguments untouched | §6 "Bound method references", "Explicit immutable closure capture": a function type excludes the receiver and the captures | `SolvikInteropTest.hostExecutionSuppliesTheHiddenReceiverAndCapturedArgumentsItself` (asserts the assembled arrays element by element, for a bound value, a closure, and a value with nothing to lead with) | — | ✅ |
| `SolvikFunctionValue.execute` (the `InteropLibrary` export a host call reaches): invokes `target()` and not the field, supplies the hidden arguments, and converts a still-uncaught guest throw into the failure §22.5 defines so no `ControlFlowException` escapes into a host | §6 "Type tests, casts, and other constructs" (host execution "invokes the same call target as guest execution"); §22.5 program boundary | `SolvikInteropTest.hostExecutionResultsConvertThroughTheOrdinaryInteropRules`, `.anUncaughtGuestThrowReachesAHostAsTheBoundaryFailureRatherThanAsControlFlow` (the same throw's report at the source boundary and at a host call are identical, and the escaping failure is not the unwinding signal; a handler inside the invoked body still handles it) | `.everyFunctionValueKindReportsExecutableCapabilityAndTheFixedDisplay` (no member is readable on any value); the guest-call route never enters this method, which is what keeps a guest handler reachable — asserted by `SolvikCallStackTest.aThrownValueUnwindsThroughAValueCallToAnEnclosingGuestHandler` | ✅ |
| `SolvikRootNode` root names and source sections as a tool sees them: `<anonymous>` for an anonymous root, the method's own name for a bound value, and the physical file of the code that runs | docs/ARCHITECTURE.md function-value instrumentation ("An anonymous root carries a source section derived from its own expression, so a stack trace names the physical file and the anonymous site") | `SolvikCallStackTest.aCapturingClosureReportsTheAnonymousNameAndTheFileHoldingItsOwnExpression`, `.anAnonymousFunctionWrittenInTheEvaluatedFileNamesThatFile`, `.aBoundMethodReferenceReportsTheMethodsOwnFrame` | — | ✅ |
| An indirect call contributes no frame of its own and reports the same callee frame a direct call reports | docs/ARCHITECTURE.md ("An indirect call is instrumented the same way as a direct call"); §6 "Indirect calls ... appear as ordinary stack frames between the caller and callee" | `SolvikCallStackTest.anIndirectCallAppearsAsAnOrdinaryFrameBetweenCallerAndCallee`, `.aDirectCallAndAnIndirectCallReportTheSameFrameForTheSameCallee` | `.aGuestHandlerCatchesThrownValuesAndNotALanguageRuntimeFault` (a language fault is not a thrown value, so the frames a fault reports are the callee's and the caller's, not a wrapper's) | ✅ |
| `LoweredProgram.functions()` / `function(name)` and `SolvikFunction.functionValue()` / `callTarget()` read back a lowered program's callables from outside the language | §6 (one canonical value per declaration; the eval target and a function target are distinct objects) | `SolvikInteropTest.lowerFunctionValues` lowers a real program and drives those accessors to obtain the values and targets every boundary test uses (this replaces the "measurement artifact" note in 4.4 for the two function accessors) | — | ✅ |

Recorded gap, unchanged by Phase 7: an uncaught guest throw carries no frames between its throw site and the
boundary, because the conversion that turns the unwinding signal into a failure happens after unwinding has
discarded them. `SolvikCallStackTest.anUncaughtThrowFromAClosureIsReportedAsAnOrdinaryGuestFailure` asserts
what §22.5 fixes (class, message, failure kind, boundary frame) and states the missing frames as a gap.


## 4. Truffle Node Coverage Gaps

### 4.1 `SolvikNumericBinaryNode` (90.0% branch)

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| `intResult` arm | Unreachable — Integer arithmetic uses specialized DSL nodes (`SolvikAddNodeGen`, etc.) | **No test needed** — confirmed dead via lowering analysis (Integer path uses node specialization) |
| Overflow edge | Arithmetic overflow on numeric types | Add to `SolvikNumericRuntimeTest`: test overflow behavior for `Byte`, `Short`, `Long`, `Float`, `Double` arithmetic |
| `instanceof Integer` false arm | Non-Integer numeric operands | Covered by `SolvikNumericRuntimeTest` (Float/Double operations) and `SolvikNumericWideningTest` (mixed operands widened to `Long`/`Double` reach the non-Integer generic node) |

### 4.2 `SolvikRegexMatchReadNode` (31.2% branch)

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| Safe-null read on null receiver | `regexMatch?.value` when receiver is null | Add to `SolvikRegexExecutionTest`: test safe member access on `RegexMatch?` returning null |
| Field mapping (VALUE) | `value` field read | Add to `SolvikRegexExecutionTest`: verify `match.value` returns the matched substring |
| VALUE-as-Integer error path | When `value` is accessed but not a String | Add negative test: ensure `RegexMatch.value` type is `String` (semantic check) |
| `START`/`END`/`GROUP_COUNT` fields | Field reads | Covered by `SolvikRegexExecutionTest` existing tests |

### 4.3 `SolvikConvertNode` (92.9% branch — was 39%)

Implicit numeric widening reuses `SolvikConvertNode` as its coercion vehicle
(`SolvikLowering.lowerCoerced`), so the widening suite now drives every `Target` branch and the
integral-vs-floating source dispatch through paths the explicit-conversion tests never reached.
`SolvikConvertNode` branch coverage rose from 39% to 92.9%.

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| All `Target` enum branches | Byte/Short/Integer/Long/Float/Double conversion targets | **DONE** — `SolvikNumericWideningTest.wideningExecutesWithTheWidenedRepresentation` and `wideningToTypedSlotsExecutes` drive the `LONG`/`DOUBLE`/`INTEGER`/`FLOAT` targets via implicit widening; `SolvikConversionRuntimeTest` covers `BYTE`/`SHORT`/`INTEGER` explicit targets |
| Unsupported target exception | Should never fire (semantic layer rejects invalid conversions) | **No test needed** — confirmed unreachable via semantic checking |

### 4.4 `LoweredProgram` (accessor getters — no standalone unit test)

| Missed Method | Behavior | Test to Add |
|---|---|---|
| Accessor methods | Expose checked program facts | Partly closed by Phase 7: `SolvikInteropTest.lowerFunctionValues` calls `lower(...)` on a program it parsed and analyzed itself and then drives `functions()`, `function(name)`, `functionValue()`, and `callTarget()` to obtain the values its boundary tests assert on, so those getters are now exercised by an assertion rather than only by a run. `program()` and `evalTarget()` remain the coverage-measurement artifact they were: `evalTarget()` is what `SolvikLanguage.parse` returns on every corpus/`.sol` run, and no test constructs a `LoweredProgram` to read `program()` directly. No gap in behavior either way.

### 4.5 `SolvikWriteLocalVariableNode` (23.1% line)

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| Write path for non-Integer locals | Writing to `var` properties and local vars | Add to `SolvikClassExecutionTest`: verify `var` property reassignment works correctly at runtime |

### 4.6 `SolvikExpressionNode` (27.8% line)

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| Base-class hooks | Abstract base for all expression nodes | Covered by positive tests on concrete subclasses |

### 4.7 `SolvikStatementNode` (58.3% branch)

| Missed Branch | Behavior | Test to Add |
|---|---|---|
| Shared statement behavior | Execute/accept methods | Covered by positive execution tests |

### 4.8 Collection Runtime Classes

| Class | Missed Branches | Behavior | Test to Add |
|---|---|---|---|
| `SolvikList` | 70.6% | Arity errors, index bounds, non-Integer index | Add to `SolvikCollectionBoundaryTest`: test `get` with non-Integer index, `add` with wrong type, empty list operations |
| `SolvikMap` | 73% | Key collision, get on missing key, put behavior | Add to `SolvikCollectionBoundaryTest`: test `put` with duplicate key (position preservation), `get` on missing key raises collection error |
| `SolvikStack` | 86% | peek/pop on empty stack | Add to `SolvikCollectionBoundaryTest`: test `peek` and `pop` on empty stack raise collection error |
| `SolvikSet` | 70.6% | add duplicate, contains behavior | Add to `SolvikCollectionBoundaryTest`: test `add` with duplicate returns false, `contains` with semantically equal key |

---

## 5. Implementation Plan: Filling the Gaps

### Phase A: Missing positive execution tests (user-observable behaviors)

| # | Feature | Test Class | Test to Add |
|---|---|---|---|
| A1 | `var` reassignment vs `val` immutability at runtime | `SolvikClassExecutionTest` | **DONE** — `varLocalWriteLoweringUpdatesTheFrameSlotAtRuntime()` exercises `var counter = 5; counter = counter + 1; ...` at runtime, confirming the SolvikWriteLocalVariableNode slot-reuse path (docs/LANGUAGE_SPEC.md section 7) |
| B2 | `if` expression with `else if` chain at runtime | `SolvikExpressionOrientedExecutionTest`, `SolvikExecutionTest` | **DONE** — `SolvikExpressionOrientedExecutionTest` (line 82 `else if (value == 0)`) and `SolvikExecutionTest` (lines 111/119 `else if (x < 10)`) execute `else if` chains at runtime |
| B3 | `while` with complex condition at runtime | `SolvikExecutionTest` | **GAP** — `SolvikExecutionTest.whileLoopWithBreakAndContinue` uses a simple scalar condition (`i < 6`); the `&&`/`||` compound boolean condition while-loop appears only in `SolvikSemanticTest` (semantic level), not executed at runtime |
| B4 | Three-clause `for` omitting condition (infinite loop) | `SolvikControlFlowParserTest` | **RESOLVED by removal** — the construct was removed by the physical-line revision; the parser rejects the old header with `SOLV-PARS-011` and names the replacements, so there is no runtime behavior left to execute | ✅ |
| B5 | `RegexMatch` member reads (value, start, end, groupCount) | `SolvikRegexExecutionTest`, `SolvikRegexSemanticTest`, `SolvikRegexNegativeTest` | **DONE** — `findReturnsTheFirstMatchWithOffsetsAndGroups()` executes `m.value`, `m.start`, `m.end`, `m.groupCount` at runtime (docs/LANGUAGE_SPEC.md section 14) |
| B6 | `is` on erased type argument at runtime | `SolvikTypeTestRuntimeTest` | **DONE** — resolved as a compile-time rejection, not a runtime path: `typeTestOnAnErasedGenericArgumentIsRejectedAtCompileTime()` asserts `list is List<Integer>` fails with `SOLV-TYPE-031` (erased type arguments are rejected at compile time; only reifiable types are tested at runtime) |
| B7 | `as` casting to interface type at runtime | `SolvikNullSafetyExecutionTest`, `SolvikTypeTestRuntimeTest` | **DONE** — `as` to interface/member types executed at runtime (`SolvikNullSafetyExecutionTest` `val named = v as Named`; `SolvikTypeTestRuntimeTest` cast tests) |
| B8 | All binary operator branches (incl. `===`, `!==`, `&&`, `||`) | `SolvikEqualityTest`, `SolvikIdentityTest`, `SolvikExecutionTest`, `SolvikConcatTest`, `SolvikNumericTest` | **DONE** — `===`/`!==` identity true-branches executed at runtime by `SolvikIdentityTest` (`missing === null` / `present === null`); `&&`/`||` short-circuit branches executed by `SolvikExecutionTest` (`1 + 1 == 2 && 3 > 2`, `false || false`, `true || (1 / 0 == 0)`); value equality `==`/`!=` by `SolvikEqualityTest` |
| B9 | Enum variant with payloads at runtime | `SolvikEnumExecutionTest` | **DONE** — `genericVariantConstructionExecutes()` and `enumVariantPayloadsAreProducedAtRuntimeByMatchLowering()` execute value-carrying variant construction and match-time payload production at runtime |
| B10 | `if` expression tail result value production at runtime | `SolvikExpressionOrientedExecutionTest` | **DONE** — `ifExpressionJoinsBranches()` exercises an `if` expression with `else if` branches producing tail values at runtime, confirming value-required block/result lowering |


### Phase B: Missing negative/edge tests (runtime error paths)

| # | Feature | Test Class | Expected Behavior |
|---|---|---|---|
| B1 | Out-of-range numeric conversion at runtime | `SolvikConversionRuntimeTest` | **DONE** — `outOfRangeConversionOfAValueIsARuntimeError()` executes `Byte(300)`, `Short(32768)`, `Integer(2147483648L)` at runtime and asserts the `"out of range"` arithmetic failure (docs/LANGUAGE_SPEC.md section 4) |
| B2 | `get` with non-Integer index on List | `SolvikGenericsNegativeTest`, `SolvikCollectionsTest` | **DONE** — resolved as a static type mismatch, not a runtime/lowering gap: `listGetRequiresAnIntegerIndex()` (`SolvikGenericsNegativeTest`) and `listGetWithNonIntegerIndexIsRejected()` (`SolvikCollectionsTest`) assert `values.get("x")` → `TYPE_MISMATCH` at compile time |
| B3 | `get` on missing key in Map | `SolvikCollectionBoundaryTest` | **DONE** — `getOnAMissingMapKeyRaisesACollectionError()` exercises `Map.get("z")` on a missing key at runtime, confirming the collection-error path |
| B4 | `peek`/`pop` on empty Stack | `SolvikCollectionBoundaryTest` | **DONE** — `peekAndPopOnAnEmptyStackRaiseCollectionErrors()` exercises `peek`/`pop` on an empty `Stack` at runtime |
| B5 | `put` with duplicate key preserves position | `SolvikCollectionBoundaryTest` | **DONE** — `mapKeyPreservationWithDuplicateKeysMaintainsPosition()` exercises duplicate-key `Map.put` preserving first position at runtime |
| B6 | `add` with duplicate in Set returns false | `SolvikCollectionBoundaryTest` | **DONE** — `setAddWithADuplicateReturnsFalseAndKeepsSize()` exercises `Set.add(1)` twice at runtime, confirming `false`/size-preservation |
| B7 | `RegexMatch.value` returns matched substring | `SolvikRegexExecutionTest` | **DONE** — `findReturnsTheFirstMatchWithOffsetsAndGroups()` asserts `m.value` == `"id-42"` at runtime (docs/LANGUAGE_SPEC.md section 14) |

### Phase C: Unit tests for LoweredProgram accessors

| # | Feature | Test Class | Test to Add |
|---|---|---|---|
| C1 | `LoweredProgram` accessor methods | (none) | **Covered end-to-end** — `lower()` builds and installs Truffle RootNodes (the entry `SolvikEvalRootNode` extends `RootNode`) whose call targets can only be created inside an active Polyglot engine context, so it cannot be called from plain test code. Its four accessors are getters over finalized fields populated by `lower()`, validated on every corpus program (21 examples + 74 regressions, both launchers) and in-process `.sol` suites via `evalTarget()`; a standalone accessor unit test would require fragile Polyglot interop and duplicate execution coverage. (`language/src/test/java/org/solvik/lowering/` is intentionally empty — no `LoweredProgram` construction is attempted.) |


### Phase D: Frame slot kind mapping test

| # | Feature | Test Class | Test to Add |
|---|---|---|---|
| D1 | `kindOf` all type→kind mappings | `SolvikKindOfTest` | **DONE** — `SolvikKindOfTest` reflectively invokes `SolvikLowering.kindOf(type)` for every Solvik type constant and asserts the expected `FrameSlotKind` (`Byte/Short/String/Unit→Object`; `Integer→Int`, `Long→Long`, `Float→Float`, `Double→Double`, `Boolean→Boolean`) plus a reference-type fall-through |

---

## 6. Coverage Gate Strategy

### Current coverage baselines (from `./build.sh` run)

| Module | Line coverage | Branch coverage | Method coverage |
|---|---|---|---|
| `language` | 94.8% (400 missed → ~350 after Phase A–D) | 89.2% (460 missed → ~420 after Phase A–D) | 91.9% (122 missed → ~100 after Phase A–D) |
| `launcher` | 90.9% (4 missed) | 81.8% (4 missed) | 100% (0 missed) |

**Measured at:** 2026-09-23 from `./build.sh` run with 2,081 language tests (10 new tests added in this implementation round). Lowering package: 92.3% instructions (298/3,834), 89.1% branches (43/365).

### Proposed coverage gate thresholds (just below achieved baselines)

| Module | Line minimum | Branch minimum |
|---|---|---|
| `language` | 94.5% | 89.0% |
| `launcher` | 90.0% | 81.0% |

These thresholds are set below the current achieved values so they only trigger a build failure when coverage **regresses**, never when it stays level. Ratchet upward incrementally as gaps close.

---

## 7. Validation Commands (run in order)

### 7.1 Fresh baseline (language + launcher)
```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl launcher test
```

### 7.2 Focused lowering tests (during implementation)
```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test \
  -Dtest='SolvikExpressionOrientedExecutionTest,SolvikSwitchExecutionTest,SolvikRangeExecutionTest,
          SolvikNumericRuntimeTest,SolvikConversionRuntimeTest,SolvikRegexExecutionTest,
          SolvikCollectionBoundaryTest,SolvikTypeTestRuntimeTest,SolvikInheritanceExecutionTest,
          SolvikInterfaceExecutionTest,SolvikEnumExecutionTest'
```

### 7.3 Full language test suite
```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test
```

### 7.4 Final quality gate (required)
```bash
./build-all.sh
```

---

## 8. Completion Checklist

| # | Acceptance criterion | Status |
|---|---|---|
| 1 | Every `SolvikLowering` method with user-observable behavior has positive + negative test coverage | **Partial** — see §5 Phase A (missing positives), Phase B (missing negatives) |
| 2 | All Truffle nodes created by lowering have both positive and negative tests | **Partial** — see §4 for specific node gaps |
| 3 | `LoweredProgram` accessor methods tested via unit test | **Done (via end-to-end execution)** — accessors are getters over finalized fields populated by `lower()`, which runs only inside an active engine context; they are exercised on every corpus program and `.sol` suite (§4.4). Not exercised behind a fragile standalone interop harness. |
| 4 | `kindOf` type→kind mappings tested | **Done** — `SolvikKindOfTest` reflectively verifies every Solvik type→`FrameSlotKind` mapping in `SolvikKindOfTest` (module opens `org.solvik.lowering` to the test module, so reflection is permitted). |
| 5 | JaCoCo `check` rules fail the build on line/branch coverage regression below recorded baseline | **Done** — active `jacoco:check` rules in both `language/pom.xml` and `launcher/pom.xml`; enforced by `./build-all.sh`. |
| 6 | `./build-all.sh` passes as final quality gate after all changes | **Done** — JVM + native builds pass; corpus OK (21 examples + 74 regressions) with both launchers. |
| 7 | `git diff --check` is clean, no generated parser edits | **Done** — verified clean.

---

## 9. Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Lowering only receives error-free programs (no negative tests at this layer) | Gaps in runtime behavior coverage | Use polyglot `Context.eval` execution with golden output assertions for positive tests; use semantic-negative programs that must fail **before** lowering |
| Some code paths are genuinely unreachable (e.g., `intResult` in `SolvikNumericBinaryNode`) | False gap signal | Document as confirmed dead via lowering analysis (Integer path uses node specialization) |
| Runtime errors propagate as `PolyglotException` rather than Solvik exceptions | Confusing assertions | Use `context.eval(source).getT()` and catch `PolyglotException`; verify error message contains expected Solvik error type |
| Native image coverage is inherently limited | Can't claim native coverage from JVM report | Validate native behavior via `./build-native.sh` + corpus test, not JUnit |

---

## 10. Positive/Negative Test Matrix Summary

| Layer | Positive coverage | Negative/edge coverage | Key test files |
|---|---|---|---|
| Statement lowering | Block/local/return/if/while/for/switch statements | ✅ (most filled); remaining GAP: `while` with complex `&&`/`||` condition and three-clause `for` omitting condition → infinite loop at runtime | `SolvikClassExecutionTest`, `SolvikExecutionTest`, `SolvikControlFlowParserTest` |
| Expression lowering | All expression forms via execution tests | ✅ (filled): `===`/`!==` identity true-branches (`SolvikIdentityTest`), `&&`/`||` short-circuit (`SolvikExecutionTest`), value equality `==`/`!=` (`SolvikEqualityTest`) |
| Method dispatch | Inheritance, interfaces, delegation | ✅ (filled): `super.speak()`/`super.name` runtime calls (`SolvikInheritanceExecutionTest`); root-default `super.equals(other)`/`super.hashCode()` (`SolvikEqualityTest`) |
| Collection construction | List/Set/Map/Stack construction | ✅ (filled): Map key preservation + duplicate keys (`SolvikCollectionBoundaryTest.mapKeyPreservationWithDuplicateKeysMaintainsPosition`); Set duplicate-add behavior (`setAddWithADuplicateReturnsFalseAndKeepsSize`) |
| Regex/RegexMatch | Regex creation, matches/find/replacE | ✅ (filled): `find`/`findAll`/`replace`/`matches` all branches + `RegexMatch.value`/`start`/`end`/`groupCount` (`SolvikRegexExecutionTest`) |
| Enum construction | Variant construction with payloads | ✅ (filled): value-carrying variant construction + match-time payload production (`SolvikEnumExecutionTest`) |
| Match lowering | Pattern matching exhaustiveness | ✅ (filled): wildcard, binding, and enum variant patterns exercised at runtime (`SolvikMatchExecutionTest`) |
| Conversion | Numeric type conversions | ✅ (filled): out-of-range conversion rejected as runtime arithmetic error (`SolvikConversionRuntimeTest`) |
| Frame/slot helpers | Slot allocation, property keys | ✅ Internal — `allocateSlot`/`propertyKey` covered by positive tests; `kindOf` all type→kind mappings tested in `SolvikKindOfTest` |

Legend: ✅ = positive + negative both present; ⚠️ = partial coverage (gaps identified in §5); ❌ = no coverage (none at this layer)

---

## 11. Next Steps (Implementation Order)

1. ✅ **Phase A** (Missing positive execution tests) — Filled under existing execution test classes (`SolvikClassExecutionTest`, `SolvikExpressionOrientedExecutionTest`, `SolvikRegexExecutionTest`, `SolvikEnumExecutionTest`, `SolvikNullSafetyExecutionTest`, `SolvikIdentityTest`, `SolvikExecutionTest`, `SolvikTypeTestRuntimeTest`); two items remain genuinely open: `while` with complex `&&`/`||` condition at runtime and three-clause `for` omitting condition → infinite loop (see §5 Phase A rows B3/B4). Note: `is` on erased type arguments is resolved as a **compile-time** rejection (`SOLV-TYPE-031`, `SolvikTypeTestRuntimeTest.typeTestOnAnErasedGenericArgumentIsRejectedAtCompileTime`), not a runtime path.
2. ✅ **Phase B** (Missing negative/edge tests) — Filled in `SolvikCollectionBoundaryTest`, `SolvikConversionRuntimeTest`, and `SolvikRegexExecutionTest`; one item is a static semantic rejection rather than a runtime gap: `list.get("x")` with a non-Integer index → `TYPE_MISMATCH` (`SolvikGenericsNegativeTest.listGetRequiresAnIntegerIndex`, `SolvikCollectionsTest.listGetWithNonIntegerIndexIsRejected`).
3. ✅ **Phase C** (LoweredProgram accessor coverage) — Resolved by end-to-end execution: `lower()` runs only inside an active engine context, so the accessor getters are exercised on every corpus program and in-process `.sol` suite; no standalone accessor unit test is attempted. `language/src/test/java/org/solvik/lowering/` remains intentionally empty.
4. ✅ **Phase D** (`kindOf` type→kind mapping) — Filled by `SolvikKindOfTest`, which reflectively verifies every Solvik type→`FrameSlotKind` mapping (`Byte/Short/String/Unit→Object`; `Integer→Int`, `Long→Long`, `Float→Float`, `Double→Double`, `Boolean→Boolean`) plus a reference-type fall-through.
5. ✅ **Coverage gate** — `jacoco:check` executions with thresholds already present in `language/pom.xml` (93% line / 86% branch) and `launcher/pom.xml` (90% line / 81% branch); set below achieved baselines and ratchet upward incrementally.
6. ✅ **Final validation** — `./build-all.sh` (JVM + native image) passes; corpus OK with both launchers.

Each phase must keep the build green. Run the focused command in the phase and record the new coverage before moving on.
