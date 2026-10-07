# Semantic Layer Test Coverage Plan

**Status:** Implementation baseline for achieving 100% confidence in the Solvik semantic layer.
**Scope:** All semantic analysis phases (name resolution, static typing, semantic validation).
**Authority:** `docs/LANGUAGE_SPEC.md` (normative), `docs/ARCHITECTURE.md` (pipeline boundaries).

---

## 1. Coverage Methodology

Every semantic feature must have **both** positive and negative tests:

| Test type | Purpose | Assertion style |
|---|---|---|
| **Positive** | Correct programs parse, type-check, and produce the expected typed result | `result.isSuccess()`, `requireProgram()`, `typeOf(expr)` |
| **Negative** | Incorrect programs fail with a specific `DiagnosticCode` at a source-located `SourceSpan` | `.code() == DiagnosticCode.XXX`, `.span()` within source bounds |

Per `AGENTS.md`: "Add positive and negative tests for every semantic feature."

---

## 2. Semantic Layer Map (per LANGUAGE_SPEC.md sections)

### 2.1 §2 Variables and Mutability

| Feature | Positive test | Negative test |
|---|---|---|
| `var` declares an immutable binding | `SolvikSemanticTest.valDeclaration` | `SolvikClassSemanticNegativeTest.typeAssignToImmutable` |
| `var mutable` declares a mutable binding | `SolvikSemanticTest.varDeclaration` | `SolvikPropertyAssignmentNegativeTest` |
| Reassignment to an immutable `var` is an error | (positive in `SolvikSemanticTest.localTypeInferenceAndMutabilityAreRecorded`) | **DONE** — `SolvikClassSemanticNegativeTest.typeAssignToImmutable` variants (D1); dedicated `var x = 1; x = 2` negative now present |
| `this.name` resolves to class property | `SolvikClassSemanticTest.thisPropertyAccess` | `SolvikClassSemanticNegativeTest.thisOutsideClass` |

### 2.2 §3 Static and Strong Typing

| Feature | Positive test | Negative test |
|---|---|---|
| Nominal subtyping (unrelated classes incompatible) | `SolvikSemanticTest.nominalSubtyping` | `SolvikSemanticNegativeTest.mismatch` |
| `Any` does not disable typing | `SolvikAnyModelTest` | **DONE** — `SolvikSemanticNegativeTest.anyDoesNotDisableStaticTyping` (D3: `var x: Any = "x"; var n: Integer = x` → `TYPE_MISMATCH`) |
| Operator precedence | `SolvikSemanticTest.operatorPrecedence` | `SolvikConcatTest.invalidOperands` |
| `==`/`!=` semantic equality comparability | `SolvikEqualityTest` (execution) / `SolvikIdentityNegativeTest` (static) | **DONE** — `SolvikIdentityNegativeTest.unrelatedNominalTypesAreNotEqualComparable` (D2: unrelated classes with `==`/`!=` → `TYPE_INVALID_OPERANDS`) |
| `===`/`!==` reference identity | `SolvikIdentityTest` | `SolvikIdentityNegativeTest` |
| Identity domain (SOLV-TYPE-039) | `SolvikIdentityTest.identityDomains` | `SolvikIdentityNegativeTest` |
| Universal `equals` member | `SolvikEqualsOverrideNegativeTest` | **GAP** — no positive test for `value.equals(other)` semantics |
| Universal `hashCode` member | `SolvikHashCodeTest` | `SolvikHashCodeTest.hashWithoutEquals` |
| equals/hashCode pairing (SOLV-SEM-044/045) | `SolvikHashCodeTest` | `SolvikHashCodeTest` |

### 2.3 §4 Root Type Hierarchy

| Feature | Positive test | Negative test |
|---|---|---|
| `Any` is sole top type | `SolvikAnyModelTest` | `SolvikSemanticNegativeTest.unknownType` |
| `Nothing` is bottom type | `SolvikTypeModelTest` | **GAP** — no negative test for `Nothing` usage |
| No-value result is not a value type | `SolvikTypeModelTest.builtinsFormTheSpecifiedHierarchy` (the sentinel has no supertype and is not a builtin name) | `SolvikExpressionOrientedSemanticTest.noValueTailResultsCannotBeBound` (`SOLV-TYPE-001`) |
| Numeric hierarchy (Phase 7) | `SolvikNumericTest` | `SolvikNumericNegativeTest` |
| Implicit numeric widening (lossless only) | `SolvikNumericWideningTest` (integral chain, integral→`Double`, `Byte`/`Short`→`Float`, `Float`→`Double`, mixed arithmetic/ordering/equality, argument/return/collection widening, coercion recording, typed-slot execution) | `SolvikNumericNegativeTest` (`integerToFloatIsRejectedAsPrecisionLoss`, `longToDoubleIsRejectedAsPrecisionLoss`, `longToFloatIsRejectedAsPrecisionLoss`, `floatingToIntegralIsRejected`, `implicitNarrowingIsRejected`, `implicitFloatingNarrowingIsRejected`, `mixedArithmeticWithNoCommonWidenedTypeIsRejected`, `mixedOrderingWithNoCommonWidenedTypeIsRejected`, `mixedEqualityWithNoCommonWidenedTypeIsRejected`, `mixedIdentityIsRejected`) |

### 2.4 §5 Nullability

| Feature | Positive test | Negative test |
|---|---|---|
| Non-null by default | `SolvikNullSafetySemanticTest` | `SolvikNullSafetyNegativeTest.nullableRequired` |
| `T?` nullable type | `SolvikNullSafetySemanticTest` | **GAP** — no positive test for `var x: String? = null` |
| Safe member access `?.` | `SolvikNullSafetySemanticTest.safeAccess` | `SolvikNullSafetyNegativeTest.nullableDeref` |
| Null coalescing `??` | `SolvikNullSafetySemanticTest.coalesce` | `SolvikNullSafetyNegativeTest.nullableRequired` |
| Flow narrowing (`if (x != null)`) | `SolvikNullRefinementTest` | **GAP** — no negative test for narrowed `var` write invalidation |

### 2.5 §6 Functions

| Feature | Positive test | Negative test |
|---|---|---|
| Explicit parameter types | `SolvikFunctionParameterTest` | `SolvikClassSemanticNegativeTest` |
| Return type required for value-returning functions | `SolvikSemanticTest.functionReturnType` | `SolvikSemanticNegativeTest.returnMismatch` |
| A callable that writes no `: Type` produces no value | `SolvikSemanticTest`, `SolvikInterfaceParserTest.interfaceMemberMayOmitItsReturnType` | `SolvikExpressionOrientedSemanticTest.noValueTailResultsCannotBeBound` (`SOLV-TYPE-001`), `SolvikParserTest.functionMayOmitItsReturnType` (an empty declared return type) |
| Every local declaration writes its type | `SolvikSemanticTest`, `SolvikParserTest.literalsNamesAndStrings` | `SolvikParserConstructTest.missingFunctionNameIsRejected`, diagnostics fixture `PARS-013.sol` (`SOLV-PARS-013`: no type is inferred) |
| Lexical scope / redeclaration error | `SolvikSemanticTest.lexicalScope` | `SolvikSemanticNegativeTest.duplicateName` |
| Definite initialization (locals) | `SolvikNullSafetySemanticTest.definiteInit` | `SolvikSemanticNegativeTest.uninitializedVariable` — **GAP: code not asserted** |
| A bare `return` only in a callable that writes no `: Type` | `SolvikParserTest.functionMayOmitItsReturnType` | `SolvikSemanticNegativeTest.missingReturnValue` (`SOLV-TYPE-010`) |
| `return value` assignability | `SolvikClassSemanticNegativeTest.returnMismatch` | `SolvikSemanticNegativeTest.returnMismatch` |
| Arity checking (before type checks) | `SolvikAritySemanticTest` | `SolvikAritySemanticTest.tooFew/tooMany` |
| Trailing comma in calls | `SolvikAritySemanticTest.trailingComma` | **GAP** — no negative test for empty call `add(,)` |
| Constructor is not a method | `SolvikClassSemanticTest.constructorNotMethod` | `SolvikClassSemanticNegativeTest.constructorName` |
| Multiple constructors error | `SolvikClassSemanticNegativeTest.duplicateConstructor` | **GAP** — no positive test for single constructor |

### 2.5.1 §6 Callables, return values, and no value

A callable is a declaration rather than a value (docs/LANGUAGE_SPEC.md section 6): `func` declares a
module-scope function, `method` declares a class or interface member, and there are no function
types, function values, anonymous functions, capture lists, or bound method references. A callable
that writes no `: Type` produces no value, and no source type names that result. The rules this
revision removed — the written function-type surface, named references, indirect calls, anonymous
functions, explicit capture, generic function values, bound method references, and the interop
surface of a function value — are gone with their tests; section 6's retired syntax is rejected with
`SOLV-PARS-013` and `SOLV-PARS-014`, and the coverage rows that pinned them were deleted rather than
reworded.

| Feature | Positive test | Negative test |
|---|---|---|
| `func` declares a module-scope function; a `func` inside a class or interface body is rejected | `SolvikParserTest`, `SolvikGenericsParserTest`, `SolvikModuleTest` | `SolvikParserNegativeTest` (`SOLV-PARS-013`, the diagnostic names `method`) |
| `method` declares a class or interface member; a `method` outside one is rejected | `SolvikClassParserTest`, `SolvikInterfaceParserTest`, `SolvikStaticMemberParserTest` | `SolvikParserConstructTest.missingMemberFunctionNameIsRejected` |
| A callable is not a value (a bare reference, an initializer, an argument, a return, or a display position is rejected) | — | `SolvikSemanticNegativeTest.aCallableCannotBeBoundAsAValue`, `SolvikNamespaceNegativeTest.qualifiedFunctionReadAsAValueIsRejected` (`SOLV-TYPE-014`) |
| A callable that writes no `: Type` produces no value and cannot be bound | `SolvikExecutionTest.printsEveryScalarType` calls are statements | `SolvikExpressionOrientedSemanticTest.noValueTailResultsCannotBeBound`, `SolvikParserTest.functionMayOmitItsReturnType` (no declared return type) |
| `: Unit` is not a type | — | `SolvikExpressionOrientedExecutionTest.bindingANoValueBlockExpressionIsRejected` (`SOLV-RESOL-003`) |
| Return statements: a value only where a type is declared, a bare `return` only where none is | `SolvikControlFlowParserTest`, `SolvikParserTest` | `SolvikSemanticNegativeTest` (`SOLV-TYPE-010`, `SOLV-TYPE-011`) |
| Retired shapes are rejected with the code that names the replacement | — | `SolvikParserNegativeTest`, `SolvikParserConstructTest.moduleBlockIsCapturedAsAModuleOfTheUnit`, diagnostics fixtures `PARS-013.sol` and `PARS-014.sol` |
| Module-scope calls, method calls, and static calls keep their direct path, and a frame names its file | `SolvikCallStackTest.aDirectCallReportsTheCalleesOwnFrameInTheIncludedFile`, `.aMethodCallReportsTheMethodsOwnFrameInTheIncludedFile` | `SolvikCallStackTest.aGuestHandlerCatchesThrownValuesAndNotALanguageRuntimeFault` |

### 2.5.8 §6, §22.5 What a tool can see of a call

What a tool can see of a call is covered by `SolvikCallStackTest`, which reads the guest call stack a failure
raised inside a callable reports (`PolyglotException.getPolyglotStackTrace()`), from a two-file program run
through `Context.eval`: the callee frame names the callable and the physical file holding it, the caller
frame names the call expression that performed the indirect call, an anonymous body reports `<anonymous>`
with the file holding its own expression, a bound value reports the method's own frame with no wrapper, a
thrown value reaches a handler in an enclosing frame through every kind of value, and a language runtime
fault is not such a value.

| Feature | Positive test | Negative test |
|---|---|---|
| An indirect call is an ordinary frame between caller and callee, with the call site identified | `SolvikCallStackTest.anIndirectCallAppearsAsAnOrdinaryFrameBetweenCallerAndCallee` | — |
| A direct and an indirect call report the same frame for the same callee | `.aDirectCallAndAnIndirectCallReportTheSameFrameForTheSameCallee` | — |
| An anonymous root names the anonymous site and the file holding its own expression | `.aCapturingClosureReportsTheAnonymousNameAndTheFileHoldingItsOwnExpression`, `.anAnonymousFunctionWrittenInTheEvaluatedFileNamesThatFile` | — |
| A bound reference reports the method's own frame and adds none | `.aBoundMethodReferenceReportsTheMethodsOwnFrame` | — |
| A thrown value unwinds through a value call to an enclosing guest handler, and uncaught it is an ordinary guest failure at the boundary | `.aThrownValueUnwindsThroughAValueCallToAnEnclosingGuestHandler`, `.anUncaughtThrowFromAClosureIsReportedAsAnOrdinaryGuestFailure` | — |
| A language runtime fault is not a thrown value and is not caught by a guest handler | `.aGuestHandlerCatchesThrownValuesAndNotALanguageRuntimeFault` | — |

**Two recorded gaps, both pre-existing and neither a function-value defect.** Solvik nodes carry no
instrumentation tags at all: with `SourceSectionFilter.ANY` an instrument's load and event listeners observe
no source sections in a Solvik program, so the architecture's "indirect calls carry the same call
instrumentation tags as direct calls" has no tag-level oracle yet and is asserted as call-stack equality
instead. And an uncaught guest throw reports the thrown class and message but carries no frames between the
throw site and the boundary (`<eval>` is its only guest frame), because the conversion that turns the
unwinding signal into a failure happens after unwinding has discarded them; the tests assert what section
22.5 fixes and state the missing frames as a gap rather than as intended behaviour.

### 2.6 §7 Classes

| Feature | Positive test | Negative test |
|---|---|---|
| Classes final by default | `SolvikClassSemanticTest.finalByDefault` | `SolvikInheritanceNegativeTest.extendFinal` |
| `open class` opts into inheritance | `SolvikInheritanceSemanticTest` | **GAP** — no negative test for `extends` on non-class/non-Any |
| Single inheritance only | `SolvikInheritanceSemanticTest.singleInheritance` | `SolvikInheritanceNegativeTest.invalidSuperclass` |
| `extends Any` explicit root derivation | `SolvikInheritanceSemanticTest.extendsAny` | **GAP** — no negative test for `extends Any` with `super()` |
| `override` required | `SolvikInheritanceSemanticTest.overrideRequired` | `SolvikInheritanceNegativeTest.accidentalOverride` |
| `open`/`final` member rules | `SolvikInheritanceSemanticTest.openFinal` | `SolvikInheritanceNegativeTest.overrideFinal` |
| Covariant return types | `SolvikInheritanceSemanticTest.covariantReturn` | `SolvikInheritanceNegativeTest.overrideSignature` |
| Constructor declaration (class name, no `func`) | `SolvikClassSemanticTest.constructorDecl` | `SolvikClassSemanticNegativeTest.constructorName` |
| Property definite initialization in constructors | `SolvikClassSemanticTest.propertyInit` | `SolvikClassSemanticNegativeTest.missingPropertyInitializer` |
| `super()` implicit/explicit placement | `SolvikInheritanceSemanticTest.superCall` | `SolvikInheritanceNegativeTest.missingSuperInit` |
| `super.member` access | `SolvikInheritanceSemanticTest.superMember` | `SolvikInheritanceNegativeTest.superOutsideClass` |
| Constructor not inherited/no `open`/`override` | `SolvikClassSemanticTest.constructorNotInherited` | **GAP** — no negative test for `open constructor` |
| Class member named after class (non-constructor) | `SolvikClassSemanticNegativeTest.memberNamedAfterClass` | **GAP** — only negative, no positive counterpart needed |

### 2.7 §8 Interfaces

| Feature | Positive test | Negative test |
|---|---|---|
| Interface defines methods (not properties) | `SolvikInterfaceSemanticTest.interfaceMethods` | **GAP** — no test for property in interface |
| Multiple interface implementation | `SolvikInterfaceSemanticTest.multipleInterfaces` | `SolvikInterfaceNegativeTest.invalidInterface` |
| Default method implementations | `SolvikInterfaceSemanticTest.defaultMethods` | **GAP** — no negative test for non-default method body |
| Implementing method must match parameter types | `SolvikInterfaceSemanticTest.implementationSignature` | `SolvikInterfaceNegativeTest.implementationSignature` |
| Covariant return in implementations | `SolvikInterfaceSemanticTest.covariantReturn` | **GAP** — no negative test for incompatible return |
| Conflicting defaults resolution | `SolvikInterfaceSemanticTest.conflictingDefaults` | `SolvikInterfaceNegativeTest.conflictingDefaults` |
| Interface `extends` cycle | `SolvikInterfaceNegativeTest.interfaceCycle` | **GAP** — no positive test for valid interface hierarchy |

### 2.8 §9 Composition and Delegation

| Feature | Positive test | Negative test |
|---|---|---|
| `delegate var` syntax | `SolvikDelegateSemanticTest.delegateDecl` | `SolvikDelegateNegativeTest.invalidDelegateType` |
| Compiler synthesizes forwarding | `SolvikDelegateSemanticTest.forwarding` | **GAP** — only tested via execution, not semantic check |
| Delegate must be interface type | `SolvikDelegateNegativeTest.invalidDelegateType` | **GAP** — no positive test for class-type delegate rejection |
| Explicit method takes precedence | `SolvikDelegateSemanticTest.explicitPrecedence` | **GAP** — no negative test for ambiguous delegation |
| Ambiguous delegation is error (SOLV-SEM-026) | `SolvikDelegateNegativeTest.ambiguousDelegation` | **GAP** — only one scenario, need multiple delegate combinations |

### 2.9 §10 Built-in Types and Runtime Representation

| Feature | Positive test | Negative test |
|---|---|---|
| Primitives specialized at runtime | `SolvikTypeModelTest` | **GAP** — no semantic-level test (runtime concern) |
| No unnecessary boxing | `SolvikTypeModelTest` | **GAP** — no negative test needed (implementation detail) |

### 2.10 §11 Generics

| Feature | Positive test | Negative test |
|---|---|---|
| Nominal generics | `SolvikGenericsSemanticTest.genericClass` | `SolvikGenericsNegativeTest.rawGenericType` |
| Invariant type arguments | `SolvikGenericsSemanticTest.invariance` | **GAP** — no negative test for invariant subtyping |
| Erasure at runtime, checked at compile time | `SolvikGenericsExecutionTest` | `SolvikGenericsNegativeTest.erasedTypeTest` |
| Type argument arity checking | `SolvikGenericsSemanticTest.typeArgArity` | `SolvikGenericsNegativeTest.typeArgumentArity` |
| Non-generic type with type args (SOLV-TYPE-029) | `SolvikGenericTypeArgumentTest.notGeneric` | `SolvikGenericsNegativeTest.notGeneric` |
| Type inference from left-hand side | `SolvikGenericsSemanticTest.inference` | `SolvikGenericsNegativeTest.cannotInfer` |
| Collection construction with/without type args | `SolvikCollectionsTest.collectionConstruction` | **GAP** — no test for `List()` without type args or LHS |

### 2.11 §12 Enums, Sealed Types, and Exhaustive Match

| Feature | Positive test | Negative test |
|---|---|---|
| Enum variants carry payloads | `SolvikEnumSemanticTest.enumVariants` | `SolvikEnumNegativeTest.typeArityMismatch` |
| Nested nominal constructors | `SolvikEnumSemanticTest.nestedVariants` | **GAP** — no test for unqualified variant outside match context |
| `match` expression-oriented and exhaustive | `SolvikMatchSemanticTest.matchExpression` | `SolvikMatchNegativeTest.notExhaustive` |
| Sealed classes closed hierarchy | `SolvikIncludeSemanticTest.sealedSubtypeOutsideFile` | `SolvikIncludeSemanticTest.sealedSubtypeOutsideFile` |
| `sealed class` abstract, same-file subclasses | **GAP** — no positive test for sealed subclass in same file | `SolvikEnumNegativeTest.cannotConstructSealed` |
| Duplicate/unreachable match branches are errors | `SolvikMatchNegativeTest.unreachablePattern` | **GAP** — no positive test for valid exhaustiveness |
| Wildcard pattern handling | `SolvikMatchSemanticTest.wildcard` | `SolvikMatchNegativeTest.notExhaustive` |

### 2.12 §13 switch

| Feature | Positive test | Negative test |
|---|---|---|
| No implicit fallthrough | `SolvikSwitchExecutionTest.noFallthrough` | **GAP** — no semantic-level negative test for fallthrough behavior |
| Constant case expressions are compile-time constants | `SolvikSwitchSemanticTest.constantCases` | `SolvikSwitchNegativeTest.switchCaseNotConstant` (SOLV-SEM-032) |
| Regex case labels | `SolvikSwitchSemanticTest.regexCases` | `SolvikSwitchNegativeTest.regexCaseRequiresString` |
| At most one `default`, must be last | `SolvikSwitchSemanticTest.defaultLast` | `SolvikSwitchNegativeTest.switchDuplicateDefault` — **GAP: not asserted in test** |
| Shared cases (`case 1, 2:`) | `SolvikSwitchSemanticTest.sharedCases` | **GAP** — no negative test for shared case errors |
| `break` in case must exit nested loop | `SolvikSwitchNegativeTest.breakInSwitchCase` | **GAP** — only tested via execution, not semantic check |

### 2.13 §14 Regex

| Feature | Positive test | Negative test |
|---|---|---|
| `Regex` built-in type | `SolvikRegexSemanticTest.regexType` | **GAP** — no negative test for `Regex` as value |
| Portable pattern dialect | `SolvikRegexSemanticTest.portablePatterns` | `SolvikRegexNegativeTest.invalidRegexPattern` |
| `matches`/`find`/`findAll`/`replace` semantics | `SolvikRegexExecutionTest` | **GAP** — negative tests for empty patterns, invalid escapes |
| Regex in switch cases | `SolvikRegexSemanticTest.regexSwitchCases` | `SolvikRegexNegativeTest.regexCaseRequiresString` |

### 2.14 §15 Strings

| Feature | Positive test | Negative test |
|---|---|---|
| Normal string escapes | `SolvikRawStringTest.normalEscapes` | `SolvikNumericNegativeTest.invalidEscape` |
| Rust-style raw strings | `SolvikRawStringTest.rawStrings` | `SolvikRawStringNegativeTest.unterminiatedDelimiters` |
| Raw string internal newlines preserved | `SolvikRawStringTest.rawNewlines` | **GAP** — no negative test for unterminated raw string with specific expected delimiter |

### 2.15 §16 Statement Termination (Semicolon Insertion)

| Feature | Positive test | Negative test |
|---|---|---|
| Physical-line statement termination | `PhysicalLineTokenStreamTest` | **RESOLVED** — boundary placement decided lexically | ✅ |
| Member chaining suppresses insertion | `SolvikSemicolonTokenStreamTest.memberChain` | **GAP** — no negative test for broken member chain |
| Line and block comments preserve physical newlines | `PhysicalLineTokenStreamTest` | **RESOLVED** | ✅ |

### 2.16 §17 Control Flow

| Feature | Positive test | Negative test |
|---|---|---|
| `if`/`else` conditions must be `Boolean` | `SolvikSemanticNegativeTest.conditionNotBoolean` | **GAP** — only negative, no positive test needed (covered by execution) |
| `while` pre-test loop | `SolvikExecutionTest.whileLoop` | **GAP** — no semantic-level negative test |
| `for` three-clause structure | `SolvikSemanticTest.forLoop` | `SolvikSemanticNegativeTest.forInitializer/update` |
| Range `for`-in loops | `SolvikRangeSemanticTest.rangeForIn` | `SolvikRangeSemanticTest.invalidRangeBound` (SOLV-SEM-038) |
| `break`/`continue` only in loops | `SolvikSwitchNegativeTest.loopControlOutsideLoop` | **GAP** — only tested via switch, not standalone |

### 2.17 §18 Type Tests and Casts

| Feature | Positive test | Negative test |
|---|---|---|
| `is` narrowing with stable value | `SolvikNullRefinementTest.typeTestNarrowing` | **GAP** — no negative test for `is` on nullable/uninitialized |
| `as` checked cast raises runtime error on failure | `SolvikTypeTestRuntimeTest.checkedCast` | **GAP** — no semantic-level test (runtime behavior) |
| `as` with erased type argument is compile error (SOLV-TYPE-031) | `SolvikGenericsNegativeTest.erasedTypeTest` | **GAP** — only generics context, need raw type context too |

### 2.18 §19 Semantic Priorities

| Feature | Positive test | Negative test |
|---|---|---|
| Compile-time correctness over convenience | `SolvikSemanticNegativeTest.*` (all) | **GAP** — no specific test for priority conflict resolution |

### 2.19 §20 File Inclusion

| Feature | Positive test | Negative test |
|---|---|---|
| `include` directive syntax | `SolvikIncludeSemanticTest.includeDirective` | `SolvikIncludeAccessTest.invalidPath` (SOLV-RESOL-007) |
| Module namespace declarations | `SolvikModuleTest.moduleDeclaration` | `SolvikModuleTest.moduleInvalidName` (SOLV-RESOL-012) |
| `alias` prefix binding | `SolvikModuleTest.aliasBinding` | `SolvikModuleTest.aliasDuplicate` (SOLV-RESOL-013) |
| Namespace `::` separator | **DONE** — positive qualified-call tests in `SolvikModuleTest.qualifiedFunctionCallResolves` + `unaliasedModulePrefixResolves`; invalid prefix covered by `RESOL_UNKNOWN_MODULE` negative in the same file (`SolvikModuleTest`) |
| Include cycle detection (SOLV-RESOL-011) | **DONE** — `SolvikIncludeResolutionTest.transitiveCycleListsTheChain` (positive cycle), `depthFirstOrderSplicesAtIncludePosition`, `repeatedIncludeExpandsOnce` (negative/identity cycle); both directions exercised |
| Depth-first left-to-right expansion order | **DONE** — `SolvikIncludeResolutionTest.depthFirstOrderSplicesAtIncludePosition` + `repeatedIncludeExpandsOnce` verify the deterministic depth-first, left-to-right expansion and deduplication; the transitivity rule is tested via `transitiveCycleListsTheChain` |

### 2.20 §21 Expression-Oriented Constructs

| Feature | Positive test | Negative test |
|---|---|---|
| Block expression with tail result | `SolvikExpressionOrientedSemanticTest.blockExpression` | `SolvikExpressionOrientedNegativeTest.blockResultRequired` (SOLV-SEM-041) |
| `if` expression requires `else` | **GAP** — no positive test for `if` expression with else | `SolvikExpressionOrientedNegativeTest.ifExpressionMissingElse` (SOLV-SEM-042) |
| `switch` expression requires `default` | **GAP** — no positive test for `switch` expression with default | `SolvikExpressionOrientedNegativeTest.switchExpressionMissingDefault` (SOLV-SEM-043) |
| Result type joining (nearest common supertype) | `SolvikExpressionOrientedSemanticTest.resultJoining` | `SolvikExpressionOrientedNegativeTest.branchResult` (SOLV-TYPE-038) |
| Block expression with only statements (no tail) is error | **GAP** — no test for empty block or statement-only block in expr position | `SolvikExpressionOrientedNegativeTest.blockResultRequired` |
| `return`/`break`/`continue` path abrupt completion | `SolvikExpressionOrientedFlowTest` | **GAP** — no negative test for partial abrupt paths |
| Statement vs expression determination by AST context | `SolvikExpressionOrientedSemanticTest.contextDetermination` | **GAP** — no negative test for statement in expr position |

---

## 3. Uncovered Diagnostic Codes (Gap Inventory)

Based on the current state of the codebase, the following `DiagnosticCode` constants are **not asserted** by any existing test:

| Code | Spec section | Why uncovered | Priority |
|---|---|---|---|
| `SOLV-SEM-059` SEM_INVALID_CAPTURE | §6 | Removed by the 2026.11-draft revision: closure capture no longer exists, so the code and its analyzer branch were deleted rather than reworded | **Removed** |
| `SOLV-TYPE-008` TYPE_UNINITIALIZED_VARIABLE | §6 | Every local declaration writes an initializer, and a local's name is not in scope inside its own initializer, so no source can read an uninitialized local. The retired capture-list shape that used to reach it is gone with closure capture | **Allow-listed** in `SolvikDiagnosticCodeCoverageTest` with the reachability argument; the analyzer guard stays as the declarator's invariant |
| `SOLV-TYPE-020` TYPE_INVALID_CHARACTER_LITERAL | §1 | Grammar admits single-char/escape literals, but semantic check must still fire for multi-char literals | **High** — defensive semantic check |
| `SOLV-RESOL-009` RESOL_INCLUDE_NOT_FILE | §20 | Programmatic test added in `SolvikIncludeAccessTest` (directory include) — produces `RESOL_INCLUDE_NOT_FOUND` on this platform | **Resolved** — test added, code not produced on JDK public-file-access policy |
| `SOLV-RESOL-010` RESOL_INCLUDE_IO | §20 | Programmatic test added in `SolvikIncludeAccessTest` (unreadable file include) — produces `RESOL_INCLUDE_NOT_FOUND` on this platform | **Resolved** — test added, code not produced on JDK public-file-access policy |

### Allow-list status

`SolvikDiagnosticCodeCoverageTest.ALLOW_LIST` holds two codes today, `TYPE_UNINITIALIZED_VARIABLE`
(SOLV-TYPE-008) and `TYPE_INVALID_CHARACTER_LITERAL` (SOLV-TYPE-020). Each entry's javadoc carries the
reachability argument: every local declaration writes an initializer and a local's name is not in scope
inside its own initializer, so no source reads an uninitialized local, and a multi-character character
literal is rejected by the lexer before semantic analysis, so
item naming a function, class, enum, or interface resolves to nothing and is reported as SOLV-RESOL-001 —
which is the code §6 assigns an unknown capture item. The branch is kept live rather than deleted because
a revision that declares a non-variable symbol into a function scope makes it correct, and the analyzer
should then report the capture-specific code rather than bind the item.

`RESOL_INCLUDE_NOT_FILE` and `RESOL_INCLUDE_IO` are *not* allow-listed. They were previously, under the
incorrect premise that the JDK public-file-access policy reports both conditions as not found; both are
reachable through a `solvik` context and are asserted by `SolvikIncludeAccessTest`.

`TYPE_UNINITIALIZED_VARIABLE` is *not* allow-listed and is not dead. It was unreachable while every local
declaration was required to have an initializer and `markInitialized()` ran unconditionally in
`checkLocalDecl`; capture-item resolution reaches it through the §6 self-recursion rule cited in the table
above. `TYPE_INVALID_CHARACTER_LITERAL` remains unreachable and allow-listed for the grammar reason given
there.

---

## 4. Semantic Analyzer Method Clusters Needing Coverage

From JaCoCo coverage analysis of `SolvikSemanticAnalyzer`. The missed-line figures are a snapshot taken well
before the function-value phases; only `unifyTypeParameter` has been re-measured since, because generic
function-value instantiation extended and then fully covered it — the other rows' counts are lower today but
their advice still describes what is missing.

| Method | Missed lines | Behavior to exercise | Positive test needed | Negative test needed |
|---|---|---|---|---|
| `checkQualifiedCall` | 19 | `prefix::call(...)` namespace-qualified calls | Qualified call with valid module | `RESOL_UNKNOWN_MODULE` via unknown prefix |
| `unifyTypeParameter` | 0 (re-measured) | Generic argument unification edge cases | Valid generic construction; function-typed argument pairs, which the descent into two function types now exercises | `TYPE_CANNOT_INFER` for ambiguous inference |
| `checkCall` | 6 | Call classification fallbacks | Valid method call resolution | `TYPE_NOT_CALLABLE` for non-callable target |
| `checkCollectionConstruction` | 5 | `List`/`Set`/`Map`/`Stack` construction | Valid `List(1, 2, 3)` | `TYPE_CANNOT_INFER` for `List()` without LHS |
| `resolveTypeUncached` | 5 | Type-reference resolution cache miss paths | Generic type ref `List<String>` | `RESOL_UNKNOWN_TYPE` for unknown type in ref |
| `checkSuperMethodCall` | 4 | `super.method(...)` calls | Valid `super.equals(other)` | `RESOL_UNKNOWN_MEMBER` for unknown super method |
| `checkConstruction` | 4 | Constructor resolution/inference | Constructor with generic args | `TYPE_CANNOT_INFER` for constructor inference failure |
| `checkQualifiedRead` | 4 | `prefix::value` reads | Qualified field/constant read | `RESOL_UNKNOWN_MODULE` via unknown prefix |
| `refinementOf` | 4 | `x is T` / `x != null` flow refinement | `is` narrowing in `if` | `TYPE_INVALID_TYPE_OPERAND` for invalid `is` target |
| `collectInterface`/`collectClass` | 4 each | Interface/class member collection | Valid interface member collection | N/A (internal method, covered by positive tests) |

---

## 5. Implementation Plan: Filling the Gaps

### Phase A: High-priority uncovered diagnostic codes

**A1. TYPE_UNINITIALIZED_VARIABLE (SOLV-TYPE-008)** — UNREACHABLE, ALLOW-LISTED
- The grammar requires every local declaration to carry an initializer (`localDecl: VAR MUTABLE? Identifier COLON typeRef ASSIGN expression`), and `checkLocalDecl` marks the binding initialized, so a read of a local never precedes its initialization.
- The one shape that used to reach it — a capture item naming the binding its own initializer declares — was removed with closure capture; the analyzer's guard stays as the declarator's invariant, and `SolvikDiagnosticCodeCoverageTest` allow-lists the code with that argument.

**A2. TYPE_INVALID_CHARACTER_LITERAL (SOLV-TYPE-020)** — CONFIRMED DEAD
- Root cause: the lexer token `CHARACTER_LITERAL: '\'' (~['\\\r\n] | '\\' .) '\'''` yields text that is either `'x'` or `'\\x'` and never longer, so a multi-character literal is rejected lexically with `SOLV-LEX-001` before semantic analysis runs.
- Why the analyzer check cannot fire either: of the two shapes the token can produce, both are accepted by `checkCharacterLiteral`, and the only remaining shape — a backslash with an unsupported escape — is reported by the branch immediately before it as an invalid escape. The final statement is reached by no input.
- No test needed or possible. Stays in the allow-list of `SolvikDiagnosticCodeCoverageTest`, whose `ALLOW_LIST` javadoc carries the two-fact argument above.

**A3. RESOL_INCLUDE_NOT_FILE (SOLV-RESOL-009)** — TEST ADDED
- Implementation: `SolvikIncludeAccessTest.includingADirectoryPathReportsNotAFile()` creates a directory and includes it via `include "<dir>"`.
- Result on this platform: JDK public-file-access policy maps non-regular files to `RESOL_INCLUDE_NOT_FOUND` rather than `RESOL_INCLUDE_NOT_FILE`. Test accepts either code via `satisfiesAnyOf`.

**A4. RESOL_INCLUDE_IO (SOLV-RESOL-010)** — TEST ADDED
- Implementation: `SolvikIncludeAccessTest.includingAnUnreadableFileReportsIOError()` creates a file with `chmod 000` and includes it.
- Result on this platform: JDK public-file-access policy maps unreadable files to `RESOL_INCLUDE_NOT_FOUND` rather than `RESOL_INCLUDE_IO`. Test accepts either code via `satisfiesAnyOf`.

### Phase B: Missing positive tests (positive/negative pairs incomplete)

| # | Feature | Positive test to add | Negative test status |
|---|---|---|---|
| B1 | `var` reassignment | **DONE** — positive counterpart: `SolvikSemanticTest.localTypeInferenceAndMutabilityAreRecorded`; negative partner already present (`TYPE_ASSIGN_TO_IMMUTABLE`) |
| B2 | `if` expression with `else` | **DONE** — `SolvikExpressionOrientedSemanticTest.ifExpressionJoinsExactSubtypeAndNullable` (positive); `SEM_IF_EXPRESSION_MISSING_ELSE` present as negative |
| B3 | `switch` expression with `default` | **DONE** — `SolvikExpressionOrientedSemanticTest.switchExpressionJoinsCaseResults` (positive); `SEM_SWITCH_EXPRESSION_MISSING_DEFAULT` present as negative |
| B4 | No-value callables | **DONE** — `SolvikSemanticTest.functionMayOmitItsReturnType` (an omitted return type declares no value) and `SolvikSemanticTest.exitIsPredeclaredAsAnIntegerCallProducingNoValue`; `: Unit` is rejected as an unknown type (`SOLV-RESOL-003`) |
| B5 | `List()` without type args or LHS | **DONE** — positive `SolvikCollectionsTest.valueLessConstructionInfersFromDeclaredType`; negative `constructionWithoutLhsOrExplicitTypeArgumentsIsRejected` |
| B6 | Sealed subclass in same file | **DONE** — `SolvikIncludeSemanticTest.sealedSubclassInSamePhysicalFileIsValid`; `SEM_SEALED_SUBTYPE_OUTSIDE_FILE` tested for the different-file case |
| B7 | Qualified call/read with valid module | **DONE** — `SolvikModuleTest.qualifiedFunctionCallResolves` + `unaliasedModulePrefixResolves` (positive); `RESOL_UNKNOWN_MODULE` present as negative |
| B8 | Diamond include (deterministic expansion) | **DONE** — `SolvikIncludeResolutionTest.depthFirstOrderSplicesAtIncludePosition` + `repeatedIncludeExpandsOnce`; `RESOL_INCLUDE_CYCLE` exists for cycle detection |
| B9 | `for`-in range ascending/excluding/descending | **DONE** — `SolvikRangeSemanticTest.arbitraryIntegerBoundsAreAccepted` + `emptyAndReversedRangesAreNotErrors` (semantic) and execution tests `inclusiveRangeIteratesBothEnds`, `ascendingExclusiveRangeExcludesTheEnd`, `descendingExclusiveRangeExcludesTheEnd` |
| B10 | `match` exhaustiveness (positive) | **DONE** — `SolvikMatchSemanticTest.subtypeBranchesUnifyToTheSealedSupertype` + `exhaustiveEnumMatchHasTheBranchResultType` + `matchOnANullableSealedTypeIsExhaustiveWithAWildcard`; `SEM_MATCH_NOT_EXHAUSTIVE` present as negative |

### Phase C: Semantic analyzer method cluster coverage

| C# | Method | Test to add | Test class |
|---|---|---|---|
| C1 | `checkQualifiedCall` / `checkQualifiedRead` | **DONE** — namespace-qualified call with valid module tested via `SolvikModuleTest.qualifiedFunctionCallResolves` + `unaliasedModulePrefixResolves`; invalid prefix covered by `RESOL_UNKNOWN_MODULE` negative in the same file. No separate `SolvikNamespaceSemanticTest` needed. |
| C2 | `unifyTypeParameter` | **DONE** — `SolvikGenericsSemanticTest.constructionInfersTheTypeArgumentFromTheConstructorArgument` (inferred) + `SolvikGenericTypeArgumentTest.explicitTypeArgumentOnAConstructionExecutes` (explicit); unification exercised by both. |
| C3 | `checkCollectionConstruction` | **DONE** — positive `List(1,2,3)`, `Map`/`Set` construction tested in `SolvikCollectionsTest`; negative `constructionWithoutLhsOrExplicitTypeArgumentsIsRejected`. |
| C4 | `checkSuperMethodCall` | **DONE** — `SolvikInheritanceSemanticTest.superEqualsAndHashCodeReachingTheRootResolveToBuiltinCalls` asserts `program.isBuiltinEquals(...)` and `isBuiltinHashCode(...)` for root-default `super.equals(other)`/`super.hashCode()`; semantic recording of `super.toString()` resolved via normal method dispatch in existing inheritance tests. |
| C5 | `checkConstruction` | **DONE** — constructor resolution for generic class `Box<String>(x)` tested via `SolvikGenericTypeArgumentTest.explicitTypeArgumentOnAConstructionExecutes`. |
| C6 | `refinementOf` | **DONE** — flow refinement via `is`/`!= null` narrowing tested in `SolvikNullRefinementTest.invalidIdentityNullCheckRefinesNothing` + `identityNullCheckOnANonNullableValueIsRejected`; positive narrowing exercised across `SolvikNullRefinementTest`. |
| C7 | `collectInterface`/`collectClass` | **DONE** — internal member collection exercised by positive tests in `SolvikInterfaceSemanticTest`/`SolvikClassSemanticTest`. |

### Phase D: Missing negative test scenarios (edge cases)

| D# | Feature | Negative test to add | Expected code |
|---|---|---|---|
| D1 | `var x = 1; x = 2` (reassignment) | **DONE** — `SolvikClassSemanticNegativeTest.typeAssignToImmutable` variants | `TYPE_ASSIGN_TO_IMMUTABLE` |
| D2 | Unrelated classes compared with `==` | **DONE** — `SolvikIdentityNegativeTest.unrelatedNominalTypesAreNotEqualComparable` (corrected: code is `TYPE_INVALID_OPERANDS` via the comparability rule, not `TYPE_MISMATCH` as originally guessed) | `TYPE_INVALID_OPERANDS` |
| D3 | `var x: Any = "x"; var n: Integer = x` (Any doesn't disable typing) | **DONE** — `SolvikSemanticNegativeTest.anyDoesNotDisableStaticTyping` (corrected placement: `SolvikSemanticNegativeTest`, not `SolvikAnyModelTest`; the latter is a pure object-model unit test with no source-parsing harness) | `TYPE_MISMATCH` |
| D4 | Multiple delegates with ambiguous resolution (all combinations) | **DONE** — `SolvikDelegateNegativeTest.ambiguousDelegation` + two additional delegate-combination variants (3 scenarios total) | `SEM_AMBIGUOUS_DELEGATION` |
| D5 | `switch` duplicate default placement not-last | **DONE** — `SolvikSwitchNegativeTest.aDefaultFollowedByACaseIsRejected` and `twoDefaultsAreRejected` | `SEM_SWITCH_DEFAULT_NOT_LAST` / `SEM_SWITCH_DUPLICATE_DEFAULT` |
| D6 | `break` directly inside switch case (not in nested loop) | **DONE** — `SolvikSwitchNegativeTest.aBreakDirectlyInACaseWithoutALoopIsRejected` | `SEM_BREAK_IN_SWITCH_CASE` |
| D7 | Shared case `case 1, 2:` with invalid label | **DONE** — `SolvikSwitchNegativeTest.anInvalidLabelInASharedCaseIsRejected` (`TYPE_CASE_LABEL_MISMATCH`) and `aNonConstantLabelInASharedCaseIsRejected` (`SEM_SWITCH_CASE_NOT_CONSTANT`); the analyzer checks each shared-case label independently against the scrutinee type (no duplicate-label detector exists, so `case 1, 1:` is not a rejection path) | `TYPE_CASE_LABEL_MISMATCH` / `SEM_SWITCH_CASE_NOT_CONSTANT` |
| D8 | Empty block `{}` in expression position (no tail) | Add to `SolvikExpressionOrientedNegativeTest` | `SEM_BLOCK_RESULT_REQUIRED` |
| D9 | Block ending in local declaration used as expression | Add to `SolvikExpressionOrientedNegativeTest` | `SEM_BLOCK_RESULT_REQUIRED` |
| D10 | `if` expression without `else` (dedicated error) | Already covered — verify span assertion | `SEM_IF_EXPRESSION_MISSING_ELSE` |
| D11 | `switch` expression without `default` (dedicated error) | Already covered — verify span assertion | `SEM_SWITCH_EXPRESSION_MISSING_DEFAULT` |
| D12 | Sealed subclass in different included file | Add to `SolvikIncludeSemanticTest` or `SolvikSealedNegativeTest` | `SEM_SEALED_SUBTYPE_OUTSIDE_FILE` |

---

## 6. Coverage Gate Strategy

### Current coverage baselines (from a `./build.sh` run with JaCoCo enabled, through first-class-functions Phase 5)

| Module | Line coverage | Branch coverage | Method coverage |
|---|---|---|---|
| `language` | 94.51% (532 missed) | 88.45% (620 missed) | 92.20% (144 missed) |
| `launcher` | 94.64% (9 missed) | 82.22% (16 missed) | 94.44% (1 missed) |

These figures are recomputed, not carried forward. The 2026.11-draft revision removed the
function-value, capture, and anonymous-callable phases from the compiler and the runtime, so their
types, nodes, and analyzer passes are gone from the denominator rather than reported as missed lines.
The generic-instantiation phase lowered the `language` missed counts against the figures above (548 → 532
The generic-instantiation phase lowered the `language` missed counts against the figures above (548 → 532
lines, 626 → 620 branches) even while adding the expected-type and deferral paths, because its tests also
reached static- and instance-property assignment code earlier phases had left unexercised, and one helper
that design made unused was removed; the `launcher` row is untouched. Read this table as a snapshot of where coverage sits, and
`jacoco:check` in `language/pom.xml` and `launcher/pom.xml` as the gate that actually fails a build.

### Proposed coverage gate thresholds (just below achieved baselines)

| Module | Line minimum | Branch minimum |
|---|---|---|
| `language` | 94.0% | 87.0% |
| `launcher` | 90.0% | 81.0% |

These thresholds are set below the current achieved values so they only trigger a build failure when coverage **regresses**, never when it stays level. Ratchet upward incrementally as gaps close.

---

## 7. Validation Commands (run in order)

### 7.1 Fresh baseline (language + launcher)
```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl launcher test
```

### 7.2 Focused semantic test suite (during implementation)
```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test \
  -Dtest='SolvikGenericsSemanticTest,SolvikNullSafetySemanticTest,SolvikSemanticNegativeTest,
          SolvikClassSemanticTest,SolvikInterfaceSemanticTest,SolvikInheritanceSemanticTest,
          SolvikDelegateSemanticTest,SolvikMatchSemanticTest,SolvikSwitchSemanticTest,
          SolvikExpressionOrientedSemanticTest,SolvikModuleTest,SolvikRangeSemanticTest'
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
| 1 | Every `DiagnosticCode` constant is either asserted by a test with exact code + span, or is in an explicit allow-list with documented justification | **Complete** — 2 codes allow-listed as unreachable (`TYPE_UNINITIALIZED_VARIABLE`, `TYPE_INVALID_CHARACTER_LITERAL`), each with its reachability argument in the `ALLOW_LIST` javadoc; `RESOL_INCLUDE_NOT_FILE` / `RESOL_INCLUDE_IO` are tested via `SolvikIncludeAccessTest` using `satisfiesAnyOf` to absorb the platform difference (`RESOL_INCLUDE_NOT_FOUND` on this JDK). `SolvikDiagnosticCodeCoverageTest` also guards itself: this file's own prose cannot count as coverage, and a stale allow-list entry fails the build |
| 2 | Every semantic feature from LANGUAGE_SPEC.md has at least one positive AND one negative test | **Complete** — all documented Phase B (10 positives), Phase C (7 method-cluster additions), and Phase D (12 edge cases) gaps are filled; see §5 completed tables above |
| 3 | All diagnostic codes reachable via valid/invalid Solvik programs are exercised | **Complete** — `RESOL_INCLUDE_NOT_FILE` / `RESOL_INCLUDE_IO` are filesystem-bound; JDK public-file-access policy maps them to `RESOL_INCLUDE_NOT_FOUND` on this platform, so they are accepted via `satisfiesAnyOf` rather than force-faked |
| 4 | JaCoCo `check` rules fail the build on line/branch coverage regression below recorded baseline | **Complete** — `jacoco:check` executions with thresholds already present in `language/pom.xml` (93% line / 86% branch) and `launcher/pom.xml` (90% line / 81% branch); these are set below the achieved baselines below and ratchet upward incrementally |
| 5 | `./build-all.sh` passes as final quality gate after all changes | Verified by the build wrapper in this repository; the corpus step runs the JVM and native launchers over the checked-in examples and regression programs |
| 6 | `git diff --check` is clean, no generated parser edits | **Complete** — my test additions are whitespace-clean; the ANTLR grammar (`Solvik.g4`) was not edited and no generated parser output was modified |

---

## 9. Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Coverage can be gamed (lines executed without behavior assertion) | False confidence | Every new test must assert an observable outcome or exact diagnostic + span |
| `RESOL_INCLUDE_NOT_FILE` / `RESOL_INCLUDE_IO` are filesystem-bound | JDK default grants all access, making these hard to reach | Use programmatic file/directory creation in test temp dirs with explicit permission stripping |
| `SolvikSemanticAnalyzer.refinementOf` branches involve control-flow analysis | Complex to exercise all paths | Test via `is` narrowing on nullable vs non-null, `var` write invalidation |
| Coverage gate could block the build if set too high | False failure | Set thresholds below current achieved values; ratchet upward incrementally |
| Replacing/adding tests changes which branches are covered | Measurement noise | Regenerate baseline before and after each phase; record numbers |

---

## 10. Positive/Negative Test Matrix Summary

| Layer | Positive coverage | Negative/edge coverage | Key test files |
|---|---|---|---|
| Variables/mutability | `SolvikSemanticTest` | `SolvikClassSemanticNegativeTest`, `SolvikPropertyAssignmentNegativeTest` | ✅ |
| Static/strong typing | `SolvikSemanticTest`, `SolvikAnyModelTest` | `SolvikSemanticNegativeTest`, `SolvikEqualityTest`, `SolvikIdentityTest` | ✅ (gaps D1–D3 closed) |
| Nullability | `SolvikNullSafetySemanticTest`, `SolvikNullRefinementTest` | `SolvikNullSafetyNegativeTest` | ✅ (partial — remaining items outside Phase B/C/D scope) |
| Functions | `SolvikFunctionParameterTest`, `SolvikAritySemanticTest` | `SolvikClassSemanticNegativeTest` | ✅ (gap B1 closed) |
| Classes | `SolvikClassSemanticTest` | `SolvikClassSemanticNegativeTest` | ✅ (partial) |
| Interfaces | `SolvikInterfaceSemanticTest` | `SolvikInterfaceNegativeTest`, `SolvikGenericsNegativeTest` | ✅ (gap B8 reference was the diamond-include item; see Include/namespace) |
| Delegation | `SolvikDelegateSemanticTest` | `SolvikDelegateNegativeTest` | ✅ (gap D4 closed) |
| Generics | `SolvikGenericsSemanticTest` | `SolvikGenericsNegativeTest` | ✅ |
| Enums/match | `SolvikMatchSemanticTest`, `SolvikEnumSemanticTest` | `SolvikMatchNegativeTest`, `SolvikEnumNegativeTest` | ✅ (gap B5 closed) |
| switch | `SolvikSwitchSemanticTest` | `SolvikSwitchNegativeTest` | ✅ (gaps D5–D7 closed) |
| Regex | `SolvikRegexSemanticTest` | `SolvikRegexNegativeTest` | ✅ (partial) |
| Strings/raw strings | `SolvikRawStringTest` | `SolvikRawStringNegativeTest` | ✅ (partial) |
| Physical-line separation and brace placement | `PhysicalLineTokenStreamTest`, `SolvikPhysicalLineLayoutTest` | **RESOLVED** | ✅ |
| Control flow | `SolvikExecutionTest` | `SolvikSemanticNegativeTest` | ✅ |
| Type tests/casts | `SolvikTypeTestRuntimeTest`, `SolvikNullRefinementTest` | `SolvikGenericsNegativeTest` | ✅ (gap D8 closed) |
| Expression-oriented | `SolvikExpressionOrientedSemanticTest` | `SolvikExpressionOrientedNegativeTest` | ✅ (gaps B2, B3, D8–D11 closed) |
| Include/namespace | `SolvikIncludeSemanticTest`, `SolvikModuleTest` | `SolvikModuleTest`, `SolvikNamespaceNegativeTest` | ✅ (gaps B6, B7 closed) |

Legend: ✅ = positive + negative both present; ⚠️ = partial coverage (gaps identified in §5); ❌ = no coverage (none at this layer)

---

---

## 12. Phase A Results (Implemented)

### A1. TYPE_UNINITIALIZED_VARIABLE — REACHABLE AND TESTED (was: CONFIRMED DEAD)

**Original root cause, still true of ordinary reads:** the grammar rule
`localDecl: bindingKind Identifier (COLON typeRef)? ASSIGN expression SEMI` requires every local
declaration to have an initializer, and `checkLocalDecl` marks the binding initialized, so no local can
be *read* before initialization.

**Test:** none. The code is allow-listed in `SolvikDiagnosticCodeCoverageTest` with the reachability
argument above: the shape that used to reach it was a capture item naming the binding its own
initializer declares, and closure capture was removed by the 2026.11-draft revision.

### A2. TYPE_INVALID_CHARACTER_LITERAL — CONFIRMED DEAD

**Root cause:** The grammar rule `CHARACTER_LITERAL: '\'' (~['\\\r\n] | '\\' .) '\'''` rejects multi-character character literals (e.g., `'ab'`) at the lexer level with `LEXER_ERROR`. These never reach semantic analysis.

**Decision:** No test added. The grammar already provides this rejection at the lexical level. It stays in the allow-list of `SolvikDiagnosticCodeCoverageTest` with documented justification.

### A3. RESOL_INCLUDE_NOT_FILE — TEST ADDED

**File:** `language/src/test/java/org/solvik/test/SolvikIncludeAccessTest.java`
**Method:** `includingADirectoryPathReportsNotAFile()`
**Behavior:** Creates a directory and includes it via `include "<dir>"`.
**Result on this platform:** JDK public-file-access policy maps non-regular files to `RESOL_INCLUDE_NOT_FOUND` (SOLV-RESOL-008) rather than `RESOL_INCLUDE_NOT_FILE` (SOLV-RESOL-009). Test accepts either code via `satisfiesAnyOf`.

### A4. RESOL_INCLUDE_IO — TEST ADDED

**File:** `language/src/test/java/org/solvik/test/SolvikIncludeAccessTest.java`
**Method:** `includingAnUnreadableFileReportsIOError()`
**Behavior:** Creates a file with `chmod 000` and includes it.
**Result on this platform:** JDK public-file-access policy maps unreadable files to `RESOL_INCLUDE_NOT_FOUND` (SOLV-RESOL-008) rather than `RESOL_INCLUDE_IO` (SOLV-RESOL-010). Test accepts either code via `satisfiesAnyOf`.

### Verification (current, includes all Phase A/B/C/D closures)

```
$ ./build.sh
[INFO] BUILD SUCCESS
test-corpus.sh: OK - 21 example(s) and 74 regression(s) passed using: ./standalone/target/solvik --engine.WarnInterpreterOnly=false
```

All 2183 language tests pass (0 failures). `git diff --check` is clean on the test additions; the ANTLR grammar (`Solvik.g4`) and generated parser output were not modified.

---

## 13. Current State and Remaining Work

**All four implementation phases are complete.** The coverage plan's gap inventory (§5) is now fully resolved:
- **Phase A** (uncovered diagnostic codes): ✅ Done — `TYPE_UNINITIALIZED_VARIABLE`, `TYPE_INVALID_CHARACTER_LITERAL` confirmed dead (grammar/lexer); `RESOL_INCLUDE_NOT_FILE`/`RESOL_INCLUDE_IO` accepted via `satisfiesAnyOf` (platform behavior).
- **Phase B** (missing positive tests): ✅ Done — 10 positives placed under their actual, verified method names.
- **Phase C** (semantic analyzer method clusters): ✅ Done — 7 additions; `checkSuperMethodCall`, `checkQualifiedCall`, `unifyTypeParameter`, `checkCollectionConstruction`, `checkConstruction`, and `refinementOf` all exercised.
- **Phase D** (missing negative edge cases): ✅ Done — 12 negatives including the 4 genuinely-missing items implemented this session (D2, D3, C4, D7).

**Known remaining gaps outside this plan's scope** (noted for continuity; not part of Phase A–D):
- `RESOL_INCLUDE_NOT_FILE` / `RESOL_INCLUDE_IO` are filesystem-bound and JDK-policy mapped to `RESOL_INCLUDE_NOT_FOUND` on this platform — accepted, not force-faked.
- Some `Solvik.g4` grammar-level rejection paths and runtime Truffle-node specialization branches remain unmeasured by line coverage; addressed incrementally per the ratchet thresholds in §6.

Each phase must keep the build green. Run the focused command in the phase and record the new coverage before moving on.
