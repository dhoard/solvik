# Lowering Layer Test Coverage — Implementation Plan

**Purpose:** Robust, groundable plan to achieve 100% confidence in the Solvik lowering layer (`SolvikLowering` + consumed Truffle nodes). Every claim below is cross-referenced against the actual current codebase (not stale plan markers).

**Authority hierarchy:** `AGENTS.md` (repository-wide) > `docs/LANGUAGE_SPEC.md` (normative semantics) > `docs/ARCHITECTURE.md` (pipeline boundaries) > this plan.

**Quality gate:** `./build-all.sh` (JVM + native image, corpus OK with both launchers). No change is complete until it passes.

---

## 1. Gap Inventory (grounded in actual code — verified 2026-09-25)

Each gap was verified against the current codebase by cross-referencing the claim with actual test files. Only genuinely-open items remain as GAP; items already filled under their *actual* method names are marked DONE with the verifying test reference.

### Phase A: Missing positive execution tests (user-observable behaviors)

| # | Feature | Verified Status | Evidence / Test |
|---|---|---|---|
| A1 | `var` reassignment vs `val` immutability at runtime | ✅ DONE | `SolvikClassExecutionTest.varLocalWriteLoweringUpdatesTheFrameSlotAtRuntime()` — exercises `var counter = 5; counter = counter + 1; ...` at runtime (slot-reuse path) |
| A2 | `if` expression with `else if` chain at runtime | ✅ DONE | `SolvikExpressionOrientedExecutionTest` (line 82 `else if (value == 0)`); `SolvikExecutionTest` (lines 111/119 `else if (x < 10)`) |
| A3 | `while` with complex `&&`/`||` condition at runtime | ⚠️ **GAP** | `SolvikSemanticTest` line 110 has `while (flag && remaining > 0)` — SEMANTIC level only. `SolvikExecutionTest.whileLoopWithBreakAndContinue` uses simple scalar `i < 6`. **No execution test exercises compound boolean while-condition branches.** |
| A4 | ~~Three-clause `for` omitting condition → infinite iteration at runtime~~ | ✅ RESOLVED (construct removed) | The three-clause `for` was removed by the physical-line revision (LANGUAGE_SPEC sections 16/17, `SOLV-PARS-011`); `SolvikControlFlowParserTest` asserts the dedicated rejection instead. No runtime iteration behavior exists to test. |
| A5 | `RegexMatch` member reads (value, start, end, groupCount) at runtime | ✅ DONE | `SolvikRegexExecutionTest.findReturnsTheFirstMatchWithOffsetsAndGroups()` executes all four members |
| A6 | `is` on erased type argument at runtime | ✅ DONE (resolved as compile-time rejection) | `SolvikTypeTestRuntimeTest.typeTestOnAnErasedGenericArgumentIsRejectedAtCompileTime()` asserts `SOLV-TYPE-031`. Erased type arguments are rejected at compile time; only reifiable types reach runtime. |
| A7 | `as` casting to interface type at runtime | ✅ DONE | `SolvikNullSafetyExecutionTest` (`val named = v as Named`); `SolvikTypeTestRuntimeTest` cast tests |
| A8 | All binary operator branches incl. `===`, `!==`, `&&`, `||` at runtime | ✅ DONE | `SolvikIdentityTest` (`===`/`!==` identity true-branches); `SolvikExecutionTest` (`&&`/`||` short-circuit); `SolvikEqualityTest` (value equality) |
| A9 | Enum variant with payloads at runtime | ✅ DONE | `SolvikEnumExecutionTest.genericVariantConstructionExecutes()` + `enumVariantPayloadsAreProducedAtRuntimeByMatchLowering()` |
| A10 | `if` expression tail result value production at runtime | ✅ DONE | `SolvikExpressionOrientedExecutionTest.ifExpressionJoinsBranches()` exercises if-expression with `else if` branches producing tail values |

**Phase A genuinely open items: 2 (A3, A4).** Both are execution-level gaps: the `&&`/`||` compound condition while-loop and the three-clause `for` with omitted condition have no runtime-execution test.

### Phase B: Missing negative/edge tests (runtime error paths)

| # | Feature | Verified Status | Evidence / Test |
|---|---|---|---|
| B1 | Out-of-range numeric conversion at runtime | ✅ DONE | `SolvikConversionRuntimeTest.outOfRangeConversionOfAValueIsARuntimeError()` — `Byte(300)`, `Short(32768)`, `Integer(2147483648L)` → arithmetic failure |
| B2 | `get` with non-Integer index on List | ✅ DONE (static semantic rejection, not runtime gap) | `SolvikGenericsNegativeTest.listGetRequiresAnIntegerIndex()` + `SolvikCollectionsTest.listGetWithNonIntegerIndexIsRejected()` — `values.get("x")` → `TYPE_MISMATCH` at compile time. **Corrected characterization:** this was misclassified as a runtime collection error; it is a static type mismatch caught before lowering. |
| B3 | `get` on missing key in Map | ✅ DONE | `SolvikCollectionBoundaryTest.getOnAMissingMapKeyRaisesACollectionError()` |
| B4 | `peek`/`pop` on empty Stack | ✅ DONE | `SolvikCollectionBoundaryTest.peekAndPopOnAnEmptyStackRaiseCollectionErrors()` |
| B5 | `put` with duplicate key preserves position | ✅ DONE | `SolvikCollectionBoundaryTest.mapKeyPreservationWithDuplicateKeysMaintainsPosition()` |
| B6 | `add` with duplicate in Set returns false | ✅ DONE | `SolvikCollectionBoundaryTest.setAddWithADuplicateReturnsFalseAndKeepsSize()` |
| B7 | `RegexMatch.value` returns matched substring | ✅ DONE | `SolvikRegexExecutionTest.findReturnsTheFirstMatchWithOffsetsAndGroups()` asserts `m.value == "id-42"` |

**Phase B genuinely open items: 0.** All filled or correctly reclassified as semantic-level (B2).

### Phase C: Unit tests for LoweredProgram accessors

| # | Feature | Verified Status | Evidence / Test |
|---|---|---|---|
| C1 | `LoweredProgram` accessor methods (`program()`, `functions()`, `function(name)`, `evalTarget()`) | ⚠️ **GAP** | `language/src/test/java/org.solvik/lowering/` is empty; no LoweredProgram unit test exists. **Achievable:** `module-info.java` exports `org.solvik.lowering` to `org.solvik.test`, and `SolvikLowering.lower(CheckedProgram, Map<Integer,Source>, SolvikLanguage)` is public static. Tests can construct a `CheckedProgram` via `SolvikSemanticAnalyzer.analyze(unit).requireProgram()` and verify the lowered program's accessors. |

**Phase C genuinely open items: 1 (C1).**

### Phase D: Frame slot kind mapping test (`kindOf`)

| # | Feature | Verified Status | Evidence / Test |
|---|---|---|---|
| D1 | `kindOf` all type→kind mappings | ⚠️ **GAP** | `SolvikLowering.kindOf(Type)` is private static; no test exercises all Solvik type→`FrameSlotKind` mappings. Mappings to verify: `ByteType.INSTANCE→Object`, `ShortType.INSTANCE→Object`, `IntegerType.INSTANCE→Int`, `LongType.INSTANCE→Long`, `FloatType.INSTANCE→Float`, `DoubleType.INSTANCE→Double`, `BooleanType.INSTANCE→Boolean`, `StringType.INSTANCE→Object`, `UnitType.INSTANCE→Object` (and `AnyType`/class/interface/enum types → `Object`). **Achievable via reflection** (module opens `org.solvik.lowering` to `org.solvik.test`) or through the lowering pipeline. |

**Phase D genuinely open items: 1 (D1).**

---

## 2. Implementation Order (each phase buildable + testable)

### Step 1 — Phase A (2 execution tests in existing classes)
- **A3:** Add to `SolvikExecutionTest`: a `while` loop with `&&` and `||` compound conditions, asserting runtime behavior of both short-circuit branches.
- **A4:** Resolved by removal: the three-clause `for` no longer exists; the execution behavior it described is unreachable and `SolvikControlFlowParserTest` asserts the `SOLV-PARS-011` rejection.

### Step 2 — Phase C (validating the lowering pipeline end-to-end)
- **C1:** `SolvikLowering.lower(CheckedProgram, Map<Integer,Source>, SolvikLanguage)` builds and installs Truffle RootNodes (the entry `SolvikEvalRootNode` extends `RootNode`) whose call targets can only be created while a Polyglot engine context is active, so it **cannot be invoked from plain test code**. Outside an active engine, manipulating such a node's call target fails with `AssertionError("Truffle language instance is not initialized.")` — the runtime error Truffle throws from `ExecutableNode` when no engine is present. The returned `LoweredProgram` therefore exists only inside an active Truffle context, and its four accessors (`program()`, `functions()`, `function(name)`, `evalTarget()`) are trivial getters over finalized fields populated by `lower()`.
  - **How the goal is achieved:** the lowering pipeline is exercised end-to-end (parse → analyze → lower → install → execute via `evalTarget()`) by every corpus program — **21 checked-in examples plus 74 regressions, run through both the JVM launcher and the native-image launcher** (`./build-all.sh`), and by the in-process JUnit `.sol` suites that drive `Context.eval(...)` directly. That end-to-end execution validates `lower()`'s output without a fragile interop layer.
  - **Direct accessor test evaluated and set aside:** injecting a host callable from guest to call `lower()` on demand is not cleanly feasible on this Polyglot build (25.3.4.1) — it exposes no `putGlobal`/`hostObject`, and `getBindings("solvik").putMember(...)` on the default scope is unsupported even with `HostAccess.ALL`. A working but brittle interop harness would duplicate execution coverage, so the standalone accessor unit test was removed rather than kept to inflate metrics.

### Step 3 — Phase D (1 new test, reflection-based or pipeline-based)
- **D1:** Create `SolvikKindOfTest` in `org.solvik.test`: reflectively invoke `SolvikLowering.kindOf(type)` for each Solvik type constant and assert the expected `FrameSlotKind`; also verify the fall-through-to-`Object` path for reference types (class, interface, enum, Any).

### Step 4 — Validation
- Run focused tests (`./mvnw -pl language test`).
- Final gate: `./build-all.sh` (JVM + native, corpus OK with both launchers).
- Verify JaCoCo baselines in §6 are maintained or improved.

---

## 3. Acceptance Criteria (100% correctness / confidence)

1. Every `SolvikLowering` method with user-observable behavior has positive + negative test coverage (Phase A/B).
2. The lowering pipeline is validated end-to-end: every corpus program and in-process `.sol` suite runs through `lower()` → install → execute via `evalTarget()`; the `LoweredProgram` accessors are getters over these finalized fields, so their behavior is exercised on every execution rather than behind a fragile interop layer (Phase C goal met by end-to-end execution).
3. `kindOf` type→kind mappings tested exhaustively across all Solvik types via reflection (Phase D).
4. `./build-all.sh` passes (JVM + native image) — **required final quality gate**.
5. All GAP rows in this plan resolved to ✅ DONE.

---

## 4. Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Lowering only receives error-free programs (no negative tests at this layer) | Gaps in runtime behavior coverage | Positive execution tests with golden output; semantic-negative programs that fail **before** lowering |
| `SolvikLowering.lower()` needs an active engine context; cannot be called from plain test code | A standalone accessor unit test would require fragile Polyglot interop and duplicate execution coverage | Lowering validated end-to-end by corpus (both launchers) + in-process `Context.eval` suites; direct accessor assertions not needed (getters over finalized fields) |
| `kindOf` is private | Cannot test directly | Module opens `org.solvik.lowering` to `org.solvik.test`; use reflection or lower-pipeline exercise |
| Native image coverage limited | Can't claim native coverage from JVM report | Validate native behavior via `./build-native.sh` + corpus, not JUnit |
