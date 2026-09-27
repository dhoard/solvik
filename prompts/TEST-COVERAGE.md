# Plan: Test Coverage Analysis and Improvement

**Scope:** plan only — do not modify production code, tests, examples, build files, or normative
documentation while executing this plan. The implementing agent must record measured values rather
than trusting the numbers in this document.

**Verdict:** coverage tooling is already configured and the baseline is strong (language: 94.2 % of
lines, 87.5 % of branches). The remaining work is to (a) make the measurement reproducible and
enforceable, (b) replace the two coarse `.sol` golden loops with a granular JUnit/AssertJ harness,
(c) close the concrete behavioral gaps named in section 4, and (d) resolve the small amount of
diagnostic and collection code that no test or production path reaches.

---

## 1. Objective and Acceptance Criteria

### Objective

Improve test coverage in a way that is reproducible, reviewable, and tied to observable language
behavior, using JUnit 5 + AssertJ unit tests for internal components and a `.sol`-file execution
harness with golden outputs for user-visible semantics.

### Observable completion criteria

1. A single documented command produces per-module and aggregate JaCoCo coverage, and the plan
   records a fresh baseline before any test is added.
2. Every `org.solvik.diagnostic.DiagnosticCode` constant is either exercised by a test that asserts
   the exact code and a source span, or is removed as genuinely dead (section 4.2).
3. The `.sol` harness runs each program as an individually named JUnit test (dynamic or
   parameterized), supports positive and negative expectations, and has an executable example for
   every language-specification area that a user can observe (section 7).
4. The largest missed-line and missed-branch clusters listed in section 4.3–4.6 each have positive
   and negative coverage.
5. JaCoCo `check` rules fail the build on a line/branch coverage regression below the recorded
   post-change baseline.
6. `./build-all.sh` passes as the final quality gate; it runs the clean JVM package and the native
   package for every change.
7. `git diff --check` is clean and no checked-in generated parser source is edited.

### Explicit non-goals

- No language syntax or semantic change; no grammar change except removing a diagnostic that is
  proven unreachable and approved for removal.
- No relaxing of static typing, and no runtime check used in place of a compile-time guarantee.
- No weakening or deletion of existing tests.
- No duplicate harnesses: the improved `.sol` harness replaces the two current loops rather than
  sitting beside them.
- No speculative production abstraction beyond the minimal launcher seam in slice 7.

### Normative behavior

This plan changes no normative behavior. It changes test code, coverage configuration, and (only
where a diagnostic is proven dead) removes unused declarations.

---

## 2. Authority and Repository Evidence

| Requirement or fact | Classification | Source |
|---|---|---|
| Every semantic feature needs positive and negative tests | required | `AGENTS.md` "Development Principles"; `README.md` "Testing" |
| Both the clean JVM package and the native package must pass before completion | required | `AGENTS.md` "Build" |
| This plan uses `./build-all.sh` as the single gate that runs both packages | required (plan decision) | user instruction; `build-all.sh` |
| `./build-all.sh` is currently untracked and must be checked in for the gate to be reproducible | observed | `git status --short` |
| Compiler stages and static analysis must not be bypassed | required | `docs/ARCHITECTURE.md` "Required Pipeline", "AST Policy" |
| JUnit 6 + AssertJ + Maven Surefire are the test stack | required | `README.md` "Testing"; `pom.xml` dependency management |
| `.sol` files under `language/tests/` are executable examples with golden output | observed | `README.md` "Testing"; `language/tests/` |
| JaCoCo agent and `report` goal are configured for `language` | observed | `language/pom.xml` jacoco plugin |
| JaCoCo agent and `report` goal are configured for `launcher` | observed | `launcher/pom.xml` jacoco plugin |
| Generated parser package is excluded from coverage | observed | `language/pom.xml` jacoco `excludes` |
| No aggregate report and no `check` goal exist today | observed | repository search for `report-aggregate` / `check` || A `solvik-coverage` module runs `jacoco:report-aggregate` last in the reactor | implemented (slice 0) | `coverage/pom.xml` depends on `solvik` and `solvik-launcher`; `verify` binds `report-aggregate` | one cross-module report |
| The aggregate excludes `org/solvik/parser/generated/**` | implemented (slice 0) | `<excludes>` on the `report-aggregate` execution | otherwise generated ANTLR classes inflate the numbers |
| Fresh aggregate baseline: 94.1 % line (452 missed), 87.5 % branch (520 missed), 91.8 % method (124 missed) | measured (slice 0) | `coverage/target/site/jacoco-aggregate/jacoco.csv` | recorded in section 4.1 |

| Baseline language coverage: 94.2 % line (445/7694 missed), 87.5 % branch (520/4153 missed), 91.8 % method (124/1515 missed) | observed | `language/target/site/jacoco/jacoco.csv` |
| Baseline launcher coverage: 44.2 % line (24/43 missed), 20.0 % branch (16/20 missed) | observed | `launcher/target/site/jacoco/jacoco.csv` |
| Current launcher coverage: 90.9 % line (4/44 missed), 81.8 % branch (4/22 missed) — gate set just below these (90 % line, 81 % branch) | measured (slice 8) | `launcher/target/site/jacoco/jacoco.csv` |
| Launcher baseline is undercounted because `main`/`parseOption` run only in the child JVM process test | observed | `launcher/src/test/java/.../SolvikMainProcessTest.java` |
| 102 diagnostic codes are defined | observed | `language/src/main/java/org/solvik/diagnostic/DiagnosticCode.java` |
| 5 codes are never referenced by a test: `RESOL_INCLUDE_IO`, `RESOL_INCLUDE_NOT_FILE`, `TYPE_INVALID_CHARACTER_LITERAL`, `TYPE_MEMBER_ACCESS_UNSUPPORTED`, `TYPE_UNINITIALIZED_VARIABLE` | observed | diff of `DiagnosticCode` constants vs `DiagnosticCode.<NAME>` references under `*/src/test/java` |
| `TYPE_MEMBER_ACCESS_UNSUPPORTED` is never emitted by production code | observed | repository-wide search; only the enum declaration matches |
| `BuiltinCollectionTypes.byName` is never called by production code and has 0/5 branches covered | observed | search for `byName(`; `jacoco.xml` |
| Current `.sol` harnesses are single `@Test` methods that loop all files | observed | `SolvikExamplesTest.java`, `SolvikDifferentialRegressionTest.java` |
| 19 top-level examples each have a `.output`; regression has 68 programs, 47 with `.output` and 21 expected to be rejected | observed | `language/tests/` listing |
| Native-image profile and ci gate exist but native execution is not a JUnit test | observed | `standalone/pom.xml` `native` profile; `ci.jsonnet` |

---

## 3. Current Test Infrastructure Trace

### 3.1 Layers that already exist

| Layer | Representative tests | Notes |
|---|---|---|
| Lexer / raw strings | `SolvikRawStringTest`, `SolvikRawStringNegativeTest`, `SolvikCharacterLiteralExecutionTest` | Positive + negative present |
| Semicolon insertion | `SolvikSemicolonInsertionTest`, `SolvikSemicolonTokenStreamTest` | Token-stream level |
| Parser + AST | `SolvikParserTest`, `SolvikParserNegativeTest`, `SolvikAstStructureTest`, `SolvikControlFlowParserTest` | Span assertions via `SolvikTestSupport` |
| Semantic analysis | per-feature `Solvik*SemanticTest` and `Solvik*SemanticNegativeTest` | Asserts `DiagnosticCode` + span in the negative classes |
| Lowering / execution | per-feature `Solvik*ExecutionTest`, `Solvik*RuntimeTest` | Runs through a polyglot `Context` |
| Golden `.sol` programs | `SolvikExamplesTest`, `SolvikDifferentialRegressionTest` | Single loop per suite |
| Diagnostics framework | `SolvikDiagnosticFrameworkTest`, `SolvikLineColumnTest` | Infrastructure, not every code |
| Truffle integration | `SolvikInstrumentationTest`, `SolvikInteropTest`, `SolvikLanguageRegistrationTest`, `SolvikRuntimeStructureTest` | Instrumentation + interop preserved |
| Launcher JVM | `SolvikMainTest` (in-process `executeSource`) | Covers evaluation and exit codes |
| Launcher process | `SolvikMainProcessTest` (child JVM) | Covers `main`, option parsing, stdin; not instrumented |
| Type model | `SolvikTypeModelTest`, `SolvikTypeJoinTest`, `SolvikTypeHierarchyPropertyTest` | Compiler type model |

### 3.2 Coverage mechanics

- `language/pom.xml` and `launcher/pom.xml` run `jacoco:prepare-agent` and `jacoco:report`.
- The report is written to `<module>/target/site/jacoco/{jacoco.csv,jacoco.xml,index.html}`.
- The generated ANTLR parser package `org/solvik/parser/generated/**` is excluded.
- There is no `check` execution, no `report-aggregate`, and no documented coverage command in
  `README.md`.
- Surefire must see `**/*Test.java` (language) or the default naming (launcher); `@TestFactory` and
  `@ParameterizedTest` methods run under the same plugin.

### 3.3 Golden `.sol` harness behavior

`SolvikExamplesTest`:
- resolves `tests` relative to the module working directory, else `language/tests`;
- lists all `*.sol` at the top level, requires a sibling `.output` for each, runs each through a
  fresh `solvik` context, and compares output byte-for-byte.

`SolvikDifferentialRegressionTest`:
- lists `tests/regression/*.sol`;
- a sibling `.output` means "must succeed and match"; no `.output` means "must be rejected with no
  output".

Limitations to fix:
- one test method per suite, so the first mismatch aborts the remaining files and the report names
  only the suite;
- rejection is asserted only for "some failure", never the diagnostic code or location;
- no harness for compile-error-only programs outside `regression`;
- library/helper `.sol` files are executed as standalone programs (they rely on an empty golden),
  which is incidental rather than explicit.

---

## 4. Gap Analysis (from the recorded baseline)

### 4.1 Fresh baselines (recorded after slices 1, 2, 9)

Regenerated with `./mvnw clean verify` on GraalVM JDK 25. The `org/solvik/parser/generated/**`
package is excluded from every report.

| Module / report | Line | Branch | Method | Instruction |
|---|---|---|---|---|
| `language` | 94.4 % (428 missed) | 87.8 % (504 missed) | 91.9 % (122 missed) | — |
| `launcher` | 90.9 % (4 missed) | 81.8 % (4 missed) | 100 % (0 missed) | — |
| **aggregate** (`solvik-coverage`) | 94.1 % (452 missed) | 87.5 % (520 missed) | 91.8 % (124 missed) | 94.1 % (2267 missed) |

- Command: `JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw clean verify` produces the aggregate at
  `coverage/target/site/jacoco-aggregate/`.
- The launcher baseline is low only because `SolvikMainProcessTest` runs in a separate, non-
  instrumented JVM; slice 7 adds an in-process seam to close that gap.
- Compared to the pre-change plan baseline (`language` 94.2 % line, `launcher` 44.2 % line):
  slices 1–2 raised the language line coverage from 94.2 % to 94.4 % (428 missed), and the
  aggregate is reproduced from a single reactor run.

### 4.2 Diagnostic-code gaps (highest value)

Five codes are never asserted by any test:

| Code | Emitted by | Testability |
|---|---|---|
| `TYPE_UNINITIALIZED_VARIABLE` (`SOLV-TYPE-008`) | `SolvikSemanticAnalyzer.checkName` | Direct: read a local before its initializer |
| `TYPE_INVALID_CHARACTER_LITERAL` (`SOLV-TYPE-020`) | `SolvikSemanticAnalyzer.checkCharacterLiteral` | Direct: a char literal with zero or multiple characters |
| `RESOL_INCLUDE_NOT_FILE` (`SOLV-RESOL-009`) | `TruffleIncludeSourceAccess` | Programmatic: include a directory |
| `RESOL_INCLUDE_IO` (`SOLV-RESOL-010`) | `TruffleIncludeSourceAccess` | Programmatic: an include path that cannot be read |
| `TYPE_MEMBER_ACCESS_UNSUPPORTED` (`SOLV-TYPE-015`) | nothing | Dead: only the enum declaration exists |

### 4.3 Semantic analyzer clusters (largest missed lines/branches)

`SolvikSemanticAnalyzer` is 95.9 % line / 89.0 % branch but has the largest absolute gap. Missed
lines cluster in:

| Method | Missed lines | Behavior to exercise |
|---|---|---|
| `checkQualifiedCall` | 19 | `prefix::call(...)` namespace-qualified calls |
| `unifyTypeParameter` | 7 | Generic argument unification edge cases |
| `checkCall` | 6 | Call classification fallbacks |
| `checkCollectionConstruction` | 5 | `List`/`Set`/`Map`/`Stack` construction and argument checking |
| `resolveTypeUncached` | 5 | Type-reference resolution cache miss paths |
| `checkSuperMethodCall` | 4 | `super.method(...)` |
| `checkConstruction` | 4 | Constructor resolution/inference |
| `checkQualifiedRead` | 4 | `prefix::value` reads |
| `refinementOf` | 4 | `x is T` / `x != null` flow refinement |
| `collectInterface`, `collectClass` | 4 each | Interface/class member collection edge cases |
| `checkAssign`, `checkName`, `checkCharacterLiteral`, `checkUnary`, `checkBinary`, `checkCoalesce`, `checkFor`, `checkForIn`, `checkSwitchCases`, `checkEnumPattern`, `isPatternCovered` | 1–4 each | Feature-level branches |

### 4.4 Lowering clusters

`SolvikLowering` is 95.0 % line / 89.3 % branch. Missed lines cluster in:

| Method | Missed lines | Behavior to exercise |
|---|---|---|
| `lowerCall` | 7 | Call forms not reached by current execution tests |
| `lowerAssign` | 4 | Property/local assignment lowering paths |
| `lowerPattern` | 3 | Pattern lowering variants |
| `runtimeHandle`, `lowerNameReference`, `decodeCharacter`, `lowerBinary`, `lowerRegexMemberCall`, `lowerEnumConstruction` | 2 each | Runtime handle cache misses, char decoding, numeric operator mapping, regex/ enum calls |

### 4.5 Runtime node and object clusters

| Class | Line | Branch | Notes |
|---|---|---|---|
| `SolvikNumericBinaryNode` | 82.5 % | 76.4 % | `intResult` is unreachable through lowering because `Integer` uses the specialized DSL nodes; remaining misses are defensive/overflow edges |
| `SolvikRegexMatchReadNode` | 56.5 % | 31.2 % | Safe-null reads, field mapping, `VALUE` error path |
| `SolvikList` | 79.1 % | 70.6 % | Arity errors, index bounds, non-Integer index |
| `SolvikMap` / `SolvikStack` / `SolvikSet` | 85–87 % | 73–86 % | Boundary and error paths |
| `SolvikStatementNode` | 82.4 % | 58.3 % | Shared statement behavior |
| `SolvikExpressionNode` | 27.8 % | — | Base-class hooks exercised only through subclasses |
| `SolvikWriteLocalVariableNode` | 23.1 % | — | Write path for non-Integer locals |
| `CheckedProgram` | 84.4 % | — | Accessor methods used only by some consumers |
| `SolvikClass`, `SolvikEnumVariant`, `SolvikEnumValue`, `SolvikContext` | 66–89 % | mixed | Runtime metadata accessors |
| `TYPE_MEMBER_ACCESS_UNSUPPORTED` sibling issue | — | — | `BuiltinCollectionTypes.byName` is dead with 0/5 branches |

### 4.6 Parser/diagnostic infrastructure clusters

| Class | Branch | Behavior to exercise |
|---|---|---|
| `SolvikErrorListener` | 68.2 % | Legacy-keyword detection, token-less errors, EOF messages, span clamping |
| `TruffleIncludeSourceAccess` | 76.5 % | Not-a-file and IO failures (`RESOL_INCLUDE_NOT_FILE`, `RESOL_INCLUDE_IO`), home-directory expansion |
| `FunctionSymbol` | 66.7 % | Convenience constructor, `signatureDeclaration`, `isOverrideCandidate`, `isConstructor`, `hasDeclaration` |
| `StringEscapes` | 78.8 % | Escape decoding branches |
| `SemicolonInsertingTokenSource` | 91.3 % | Maintained but not at 100 % |
| `SymbolTable`, `Scope` | 60–70 % | Duplicate/shadow/scope-lookup edge cases |
| `UnaryOperator.fromSpelling` | 0 % | No test calls it |
| `RegexSyntax` | 62.5 % | Unsupported construct rejection |

### 4.7 Dead or unreachable code to resolve

- `DiagnosticCode.TYPE_MEMBER_ACCESS_UNSUPPORTED` — never emitted.
- `BuiltinCollectionTypes.byName` — never called; its 5 branches are the entire 0 % branch result
  for that class.
- `SolvikNumericBinaryNode.intResult` — unreachable through lowering because `Integer` arithmetic lowers
  to the dedicated DSL nodes.

Per `AGENTS.md` ("Remove replaced code when it is no longer needed"), these should be removed unless
the implementing agent can point to a specification requirement that needs them. If removal is
chosen, it is a small production change and must be validated with the full build.

---

## 5. Change Surface

| File or proposed file | Symbols | Planned responsibility | Why it changes |
|---|---|---|---|
| `pom.xml` (parent) | jacoco plugin management | Add `report-aggregate` configuration/profile | Produce one cross-module report |
| `language/pom.xml` | jacoco plugin | Add `check` execution with line/branch minimums | Coverage gate |
| `launcher/pom.xml` | jacoco plugin | Add `check` execution | Coverage gate |
| `README.md` | Testing section | Document the coverage command and gate | Discoverability |
| `language/src/test/java/org/solvik/test/SolvikProgramTest.java` (proposed) | `@TestFactory` cases | Run every `language/tests/*.sol` as an individually named test against its `.output` | Replace the loop in `SolvikExamplesTest` |
| `language/src/test/java/org/solvik/test/SolvikRegressionProgramTest.java` (proposed) | `@TestFactory` cases | Run every `language/tests/regression/*.sol`, success-with-golden or rejection | Replace the loop in `SolvikDifferentialRegressionTest` |
| `language/src/test/java/org/solvik/test/SolvikDiagnosticProgramTest.java` (proposed) | fixture-driven cases | For each `language/tests/diagnostics/<CODE>.sol`, assert the emitted diagnostic code and span | New negative harness |
| `language/tests/diagnostics/` (proposed) | `<CODE>.sol`, optional `<CODE>.span` | One fixture per reachable diagnostic code | New negative corpus |
| `language/tests/*.sol` + `.output` (proposed additions) | Delegation, generics, enums/match, expression-oriented, interfaces | Publish executable examples for spec areas with none | Close example gap |
| `language/src/test/java/org/solvik/test/SolvikDiagnosticCodeCoverageTest.java` (proposed) | Meta-test | Assert every `DiagnosticCode` is covered by a fixture or is in an explicit allow-list | Prevent silent diagnostic gaps |
| `language/src/test/java/org/solvik/test/SolvikQualifiedCallTest.java` (proposed) | Namespace call tests | Cover `checkQualifiedCall`/`checkQualifiedRead` | Section 4.3 |
| `language/src/test/java/org/solvik/test/SolvikGenericsUnificationTest.java` (proposed) | Unification tests | Cover `unifyTypeParameter` edges | Section 4.3 |
| Existing `Solvik*SemanticNegativeTest` | new methods | Cover remaining diagnostic branches with code + span | Section 4.2–4.3 |
| Existing `Solvik*ExecutionTest` | new methods | Cover lowering and runtime paths | Section 4.4–4.6 |
| `language/src/test/java/org/solvik/test/SolvikCollectionBoundaryTest.java` | new methods | Cover `SolvikList`/`Map`/`Stack`/`Set` boundaries and arity errors | Section 4.5 |
| `language/src/test/java/org/solvik/test/SolvikTypeModelTest.java` | new methods | Cover `UnaryOperator.fromSpelling` and (if kept) `BuiltinCollectionTypes.byName` | Section 4.6–4.7 |
| `launcher/src/main/java/org/solvik/launcher/SolvikMain.java` | new package-private `run(...)`/`parseOptions(...)` seam | Make `main`'s logic testable in-process without `System.exit` | Section 4.1 launcher gap |
| `launcher/src/test/java/org/solvik/launcher/test/SolvikMainTest.java` | new methods | Cover option parsing, stdin/file selection, exit-code paths in-process | Section 4.1 |
| `language/src/main/java/org/solvik/diagnostic/DiagnosticCode.java` | remove `TYPE_MEMBER_ACCESS_UNSUPPORTED` (decision) | Remove dead diagnostic | Section 4.7 |
| `language/src/main/java/org/solvik/type/BuiltinCollectionTypes.java` | remove `byName` (decision) | Remove dead method | Section 4.7 |
| `language/src/main/java/org/solvik/truffle/nodes/SolvikNumericBinaryNode.java` | remove `intResult` (decision) | Remove unreachable method and its `instanceof Integer` arm | Section 4.7 |

---

## 6. Ordered Implementation Slices

Each slice must keep the build green. Run the focused command in the slice and record the new
coverage before moving on.

### Slice 0: Reproducible coverage baseline and aggregate report

- **Dependencies:** none.
- **Implementation:**
  - Run the focused coverage command from section 9 and save the fresh `language` and `launcher`
    numbers into section 4.1 of this file.
  - Add a parent-level jacoco `report-aggregate` execution (typically a `coverage` module or the
    parent's `verify` phase) so one report spans `language` and `launcher`. Do not remove the
    existing per-module reports.
  - Document the command in `README.md` "Testing".
- **Removal/migration:** none.
- **Diagnostics:** not applicable.
- **Tests:** not applicable; this slice changes reporting only.
- **Focused validation:** section 9 commands 1 and 2; confirm the aggregate HTML exists.
- **Done when:** the fresh baseline is recorded and one command produces an aggregate report.

### Slice 1: Granular `.sol` test harness

- **Dependencies:** slice 0.
- **Implementation:**
  - Add `SolvikProgramTest` using `@TestFactory` with one `DynamicTest` per top-level `.sol` file,
    named by file. Keep the existing context-building helper (a fresh `solvik` `Context` with
    `out`/`err` captured) and the byte-for-byte `.output` comparison.
  - Add `SolvikRegressionProgramTest` with one `DynamicTest` per `tests/regression/*.sol`, choosing
    `success-and-match` when a `.output` exists and `reject-with-no-output` otherwise.
  - Delete `SolvikExamplesTest` and `SolvikDifferentialRegressionTest` once the new factory classes
    exist, so exactly one suite owns the `.sol` corpus. Do not leave two loops that both run it; if
    the old class names are kept for continuity, their methods must delegate and must not re-list
    and re-run the files.
  - Support an optional `<stem>.error` file next to a rejection fixture containing the expected
    stable diagnostic code; when present, assert the first diagnostic's code equals it and the span
    is within source bounds. Absence keeps the current "some rejection" semantics.
- **Removal/migration:** delete the old monolithic test methods (superseded, do not duplicate).
- **Diagnostics:** new assertions must read the code through `Diagnostic`/`DiagnosticCode`; do not
  match message text only.
- **Tests:** the harness is itself the test. Positive: all 19 examples and 47 golden regressions
  still pass. Negative: the 21 rejection programs still reject; add `.error` files for them where a
  stable code is known.
- **Focused validation:** section 9 command 3.
- **Done when:** Surefire reports one entry per `.sol` file and the suite is green.

### Slice 2: Diagnostic fixtures for the uncovered codes

- **Dependencies:** slice 1.
- **Implementation:**
  - Add `language/tests/diagnostics/` and a `SolvikDiagnosticProgramTest` that runs each
    `<CODE>.sol` and asserts the requested code plus span.
  - Add fixtures for `TYPE_UNINITIALIZED_VARIABLE`, `TYPE_INVALID_CHARACTER_LITERAL`, and any other
    reachable code that currently lacks a dedicated negative fixture.
  - Add programmatic JUnit cases (in `SolvikIncludeSemanticTest` or a new
    `SolvikIncludeAccessTest`) that create a directory and an unreadable path to drive
    `RESOL_INCLUDE_NOT_FILE` and `RESOL_INCLUDE_IO` through `TruffleIncludeSourceAccess`.
  - Add `SolvikDiagnosticCodeCoverageTest` as a meta-test: reflect over `DiagnosticCode.values()`
    and assert each constant appears in the fixture registry or the explicit allow-list (only codes
    that require programmatic setup or that are pending removal).
- **Removal/migration:** if `TYPE_MEMBER_ACCESS_UNSUPPORTED` is removed in slice 9, drop it from the
  allow-list.
- **Diagnostics:** assert exact `DiagnosticCode` and exact `SourceSpan` for the new fixtures.
- **Tests:** positive (fixture exists and parses to the expected code) and negative (a fixture with
  the wrong expected code must fail — verify by temporarily editing one).
- **Focused validation:** section 9 command 4.
- **Done when:** every non-allow-listed code has a fixture, and the meta-test fails if a new code is
  added without coverage.

### Slice 3: Semantic analyzer clusters

- **Dependencies:** slice 2.
- **Implementation:** add positive and negative tests, nearest to the existing per-feature classes:
  - namespace-qualified calls/reads for `checkQualifiedCall`/`checkQualifiedRead`;
  - generic unification edges for `unifyTypeParameter`;
  - collection construction and argument checking for `checkCollectionConstruction`;
  - `super.method(...)` and constructor resolution for `checkSuperMethodCall`/`checkConstruction`;
  - flow refinement for `refinementOf`;
  - class/interface member collection edge cases.
- **Removal/migration:** none.
- **Diagnostics:** each new negative asserts code and span.
- **Tests:** a `.sol` positive example plus a JUnit negative per named method; prefer `.sol` when the
  behavior is user-observable, JUnit when it is diagnostic-only.
- **Focused validation:** section 9 command 5 for the touched test classes.
- **Done when:** the semantic analyzer's missed lines/branches in section 4.3 are materially reduced
  and no diagnostic regresses.

### Slice 4: Lowering clusters

- **Dependencies:** slice 3.
- **Implementation:** add execution tests for the call, assignment, pattern, char, regex-member, and
  enum-construction forms that currently miss `SolvikLowering`. Prefer `.sol` golden programs when
  output is observable; use `SolvikExpressionOrientedRuntimeTest`-style in-process execution
  otherwise.
- **Removal/migration:** none.
- **Diagnostics:** not applicable unless a lowering path surfaces a compile error.
- **Tests:** positive execution per form, plus a negative for each form that must be rejected before
  lowering.
- **Focused validation:** section 9 command 5.
- **Done when:** the lowering gaps in section 4.4 are closed or shown unreachable.

### Slice 5: Runtime nodes and objects

- **Dependencies:** slice 4.
- **Implementation:**
  - `SolvikNumericBinaryNode`: cover every supported numeric type's add/sub/mul/div, overflow, and
    divide-by-zero through `.sol` or in-process execution. Decide slice 9 for `intResult`.
  - `SolvikRegexMatchReadNode`: cover safe-null reads, every `Field`, and the `VALUE`-as-Integer error.
  - `SolvikList`/`Map`/`Stack`/`Set`: cover arity errors, index bounds, non-Integer index, and empty
    operations.
  - `CheckedProgram`/`SolvikClass`/enum runtime metadata: cover accessors through the tests that
    consume them.
- **Removal/migration:** none in this slice.
- **Diagnostics:** not applicable; these are runtime errors with stable messages.
- **Tests:** extend `SolvikNumericRuntimeTest`, `SolvikRegexExecutionTest`,
  `SolvikCollectionBoundaryTest`; add a `.sol` example if the behavior is worth publishing.
- **Focused validation:** section 9 command 5.
- **Done when:** the section 4.5 clusters are materially reduced and runtime error messages are
  asserted.

### Slice 6: Parser/diagnostic infrastructure

- **Dependencies:** slice 2.
- **Implementation:**
  - `SolvikErrorListener`: cover legacy-keyword detection, token-less errors, EOF spelling, and
    span clamping.
  - `StringEscapes`: cover each escape branch.
  - `SemicolonInsertingTokenSource`: cover remaining branch paths.
  - `FunctionSymbol`, `SymbolTable`, `Scope`: cover duplicate/shadow/lookup edges.
  - `UnaryOperator.fromSpelling`: add a `SolvikTypeModelTest` case.
  - `RegexSyntax`: cover unsupported construct rejection.
- **Removal/migration:** none.
- **Diagnostics:** parser tests must assert the emitted `PARSER_*` code.
- **Tests:** JUnit, nearest existing class.
- **Focused validation:** section 9 command 5.
- **Done when:** the section 4.6 clusters are materially reduced.

### Slice 7: Launcher coverage and instrumentation

- **Dependencies:** slice 0.
- **Implementation:**
  - Extract the body of `SolvikMain.main` into a package-private `static int run(String[] args,
    InputStream in, PrintStream out, PrintStream err, Map<String,String> options)` (or
    `parseArgs`), leaving `main` as the `System.exit` wrapper. This is the smallest seam that makes
    argument/file/stdin selection testable in-process.
  - Add `SolvikMainTest` cases for: no `--` prefix, exactly `--`, `--key`, `--key=value`, file vs
    stdin selection, and every exit-code branch.
  - Investigate whether JaCoCo can instrument the child JVM in `SolvikMainProcessTest` (propagate the
    agent argument from `jacocoArgLine`); if not, keep the process test for end-to-end behavior and
    rely on the in-process seam for instrumented coverage.
- **Removal/migration:** update `SolvikMainProcessTest` only if the seam changes its invocation; the
  process test remains the end-to-end guard.
- **Diagnostics:** assert that a compile error writes the stable code to `err` and returns 1.
- **Tests:** in-process positive and negative plus the existing process-level cases.
- **Focused validation:** section 9 command 6.
- **Done when:** launcher line coverage rises above the recorded baseline and `main` has no
  unverifiable branch.

### Slice 8: Coverage gate

- **Dependencies:** all test slices.
- **Implementation:** add jacoco `check` executions to `language/pom.xml` and `launcher/pom.xml`
  with `LINE` and `BRANCH` minimums set just below the freshly measured values (start from the
  achieved number, not from this document). Keep the generated-parser exclusion. Ratchet the
  minimums upward only when the tests actually raise the measured value.
- **Removal/migration:** none.
- **Diagnostics:** not applicable.
- **Tests:** not applicable.
- **Focused validation:** section 9 command 7; deliberately lower a threshold and confirm the build
  fails, then restore it.
- **Done when:** `./build-all.sh` fails on a coverage regression and passes at the recorded baseline.

### Slice 9: Resolve dead code

- **Dependencies:** slices 2 and 5.
- **Implementation:** remove `DiagnosticCode.TYPE_MEMBER_ACCESS_UNSUPPORTED`,
  `BuiltinCollectionTypes.byName`, and `SolvikNumericBinaryNode.intResult` (and its
  `instanceof Integer` arm) unless the agent documents a specification requirement. Update the
  slice 2 allow-list and any type-model test accordingly.
- **Removal/migration:** this slice is removal; do not leave commented-out code.
- **Diagnostics:** remove the now-stale allow-list entry.
- **Tests:** if `byName` is kept instead of removed, add direct unit tests for all four lookups plus
  the empty case.
- **Focused validation:** section 9 commands 5 and 8.
- **Done when:** no test-unreachable declaration remains without a documented reason.

---

## 7. Test Matrix

| Layer | Positive coverage | Negative/edge coverage | Test file or proposed file |
|---|---|---|---|
| Lexer / escapes / raw strings | Existing + escape branches | Mismatched delimiters, bad escapes, invalid char literal | `SolvikRawStringTest`, `SolvikRawStringNegativeTest`, `SolvikCharacterLiteralExecutionTest` |
| Semicolon insertion | Statement termination examples | Line-boundary edge cases | `SolvikSemicolonInsertionTest`, `SolvikSemicolonTokenStreamTest` |
| Parsing / AST | Every construct parses with a span | Unexpected token, incomplete input, legacy syntax | `SolvikParserTest`, `SolvikParserNegativeTest`, `SolvikAstStructureTest` |
| Name/type resolution | Namespaced reads and calls | Unknown name/type/member/module, alias rules | `SolvikSemanticNegativeTest`, `SolvikNamespaceNegativeTest`, `SolvikModuleTest` |
| Static typing / flow | Generics unification, refinement, coalesce | Type mismatch, nullable deref, uninitialized read | `SolvikGenericsSemanticTest`, `SolvikNullSafetySemanticTest`, `SolvikSemanticNegativeTest` |
| Semantic validation | Inheritance, interfaces, delegation, match exhaustiveness | Every `SEM_*` code | `Solvik*SemanticTest`, `Solvik*NegativeTest`, new diagnostic fixtures |
| Typed lowering | Call/assign/pattern/collection/enum forms | Rejected programs never reach lowering | `Solvik*ExecutionTest`, `SolvikExpressionOrientedRuntimeTest` |
| Truffle runtime | Numeric, regex-match, collections, enum metadata | Overflow, divide-by-zero, bounds, arity, safe-null | `SolvikNumericRuntimeTest`, `SolvikRegexExecutionTest`, `SolvikCollectionBoundaryTest` |
| Golden programs | All `language/tests/*.sol` against `.output` | All `regression` rejections, diagnostic fixtures | `SolvikProgramTest`, `SolvikRegressionProgramTest`, `SolvikDiagnosticProgramTest` (proposed) |
| Instrumentation / interop | Tags, sources, values | Unsupported interop values | `SolvikInstrumentationTest`, `SolvikInteropTest`, `SolvikLanguageRegistrationTest` |
| Launcher / distribution | File, stdin, options, exit codes | Compile error, unknown option, missing file, missing/not-file/IO include | `SolvikMainTest`, `SolvikMainProcessTest` |
| Diagnostics infrastructure | Every `DiagnosticCode` reachable | Meta-test on the code set | `SolvikDiagnosticFrameworkTest`, `SolvikDiagnosticCodeCoverageTest` (proposed) |
| Native image | `Hello.sol` via `solvik-native` | Native build failure | `ci.jsonnet` gate / documented manual check |

---

## 8. Examples and Documentation

- **Add `.sol` examples** (with `.output`) for specification areas that have no executable example:
  composition/delegation (§9), generics (§11), enums/sealed types and `match` (§12),
  interfaces/default methods (§8), and expression-oriented block/`if`/`switch` expressions (§21).
  Any new example must use only published syntax and must be run by `SolvikProgramTest`.
- **Add negative fixtures** under `language/tests/diagnostics/` for the codes in section 4.2. Each
  fixture starts with a comment naming the expected stable code.
- **Update `README.md` "Testing"** to document the coverage command, the aggregate report location,
  and the `check` gate.
- **Do not change** `docs/LANGUAGE_SPEC.md` or `docs/ARCHITECTURE.md`; this plan changes no language
  behavior and no compiler boundary.
- **Preserve** existing copyright and license headers on every touched file.

---

## 9. Validation Commands

Run in order. Set `JAVA_HOME` to GraalVM for JDK 25 for focused Maven commands, or use the wrappers.
Do not rely on stale outputs.

1. **Fresh baseline (language + launcher).** Regenerates `target/site/jacoco` from a clean test run.
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl launcher test
   ```
   Catches: stale coverage and unexpected test failures. Record the numbers into section 4.1.

2. **Aggregate report smoke.** After slice 0:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw verify
   ```
   Catches: a misconfigured `report-aggregate` execution; confirm the aggregate HTML exists.

3. **Golden harness.** After slice 1:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test \
     -Dtest='SolvikProgramTest,SolvikRegressionProgramTest'
   ```
   Catches: a `.sol` behavior or golden drift; confirm one Surefire entry per file.

4. **Diagnostic fixtures.** After slice 2:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test \
     -Dtest='SolvikDiagnosticProgramTest,SolvikDiagnosticCodeCoverageTest,SolvikIncludeSemanticTest'
   ```
   Catches: a missing fixture, a wrong code/span, or a new code added without coverage.

5. **Focused feature tests.** After slices 3–6, run the touched classes, for example:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language test \
     -Dtest='SolvikGenericsSemanticTest,SolvikNullSafetySemanticTest,SolvikSemanticNegativeTest,SolvikNumericRuntimeTest,SolvikRegexExecutionTest,SolvikCollectionBoundaryTest,SolvikTypeModelTest'
   ```
   Catches: regressions in the newly covered behavior.

6. **Launcher.** After slice 7:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl launcher test
   ```
   Catches: option/file/stdin/exit-code regressions; inspect launcher coverage in the report.

7. **Coverage gate.** After slice 8:
   ```bash
   JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language,launcher verify
   ```
   Catches: a coverage drop below the threshold. Verify the gate actually fails by temporarily
   raising a threshold.

   **Config format.** The check goal's `<rule>` carries its thresholds under `<limits>`/`<limit>`;
   it is *not* a bare `<element>`/`<minimum>`. Each `<limit>` names the counter (`<counter>LINE</counter>`
   or `<counter>BRANCH</counter>`), the coverage type (`<value>COVEREDRATIO</value>`; `MISSED` and
   `COVERED` are also valid), and the threshold (`<minimum>93.0%</minimum>`). Example:
   ```xml
   <rules>
     <rule>
       <limits>
         <limit>
           <counter>LINE</counter>
           <value>COVEREDRATIO</value>
           <minimum>93.0%</minimum>
         </limit>
         <limit>
           <counter>BRANCH</counter>
           <value>COVEREDRATIO</value>
           <minimum>86.0%</minimum>
         </limit>
       </limits>
     </rule>
   </rules>
   ```
   The `<element>` attribute form (`<element LINE><minimum>...`) and the shorthand `<rule><element>` with
   a direct `<minimum>` are both rejected by `jacoco-maven-plugin:0.8.15:check` — the thresholds belong in
   the nested `<limit>`. This was discovered during slice 8's blocker investigation.

8. **Final quality gate (required).**
   ```bash
   ./build-all.sh
   ```
   Runs the clean JVM package (compilation, full test suite, coverage gate) and the native package.
   Catches: compilation, regression, coverage-gate, integration, and native-image configuration
   failures across all modules.

9. **Launcher smoke (observable behavior).**
   ```bash
   ./standalone/target/solvik language/tests/Hello.sol
   ./standalone/target/solvik-native language/tests/Hello.sol
   ```
   Catches: packaging and native launch regressions.

10. **Final diff hygiene.**
    ```bash
    git diff --check
    git status --short
    git diff
    ```
    Catches: whitespace errors, stray files, generated parser edits, and unintended production
    changes.

---

## 10. Risks, Decisions, and Open Questions

### Risks

- **Coverage can be gamed.** Adding tests that execute a line without asserting behavior inflates
  the number. Every new test must assert an observable outcome or an exact diagnostic and span.
- **Defensive branches may be unreachable.** Some missed branches (for example the false arm of each
  `instanceof` chain in `SolvikNumericBinaryNode`, or the `IllegalStateException` fallbacks) cannot
  be reached by valid programs. Do not force them with invalid casts; either remove the code or
  document it as invariant-protected. Adding a test that reaches an internal error state through an
  artificial API is worse than removing the code.
- **Child-JVM coverage.** `SolvikMainProcessTest` runs in a separate JVM without the JaCoCo agent,
  so `main` will remain apparently uncovered until slice 7 provides an in-process seam or the agent
  is propagated. Prefer the seam; it also makes behavior easier to assert.
- **Coverage gate could block the build.** Set thresholds from measured values and ratchet upward;
  never place the gate above achievable coverage.
- **`.sol` harness replacement.** Deleting the old loops and adding dynamic tests must preserve the
  exact context setup (fresh context, `out`/`err` captured, `allowAllAccess`) or unrelated tests
  will start failing.
- **Native image.** Native behavior is validated by `ci.jsonnet` and manual smoke commands, not by
  JUnit; coverage of the native path is inherently limited. Do not claim native coverage from the
  JVM report.

### Decisions required

- **D1.** Remove or keep `DiagnosticCode.TYPE_MEMBER_ACCESS_UNSUPPORTED`. It is currently dead. If
  the specification intends a "member access unsupported" rejection, identify the required case and
  emit it; otherwise remove it.
- **D2.** Remove or test `BuiltinCollectionTypes.byName`. It has no production caller. If it is
  intended public API, test it; otherwise remove it.
- **D3.** Remove or keep `SolvikNumericBinaryNode.intResult` and its `instanceof Integer` arm, which
  lowering never reaches because `Integer` uses the specialized DSL nodes.

### Assumptions the implementing agent must verify

- The two `.sol` harnesses can be replaced without changing observable test semantics.
- `@TestFactory` dynamic tests and `@ParameterizedTest` are discovered by the configured Surefire
  includes.
- `RESOL_INCLUDE_NOT_FILE` and `RESOL_INCLUDE_IO` are reachable through the existing include access
  abstraction with a directory path and an unreadable path.

### Open questions

- Should the coverage gate live in the default `./build-all.sh` path or only in a dedicated profile?
  Recommendation: default path, so regressions cannot merge unnoticed.
- Should the diagnostic fixture directory be organized by family (`lex/`, `pars/`, `resol/`,
  `type/`, `sem/`) or flat? Recommendation: flat with the wire code as the filename for a trivial
  meta-test.

---

## 11. Completion Checklist

| Acceptance criterion | Slice | Positive test | Negative/edge test | Docs/examples | Final validation |
|---|---|---|---|---|---|
| Fresh baseline recorded | 0 | n/a | n/a | README coverage command | command 1 |
| Aggregate report produced | 0 | n/a | command 2 | README | command 2 |
| Every target program is its own test | 1 | `SolvikProgramTest` | `SolvikRegressionProgramTest` | — | command 3 |
| Every reachable diagnostic has a fixture | 2 | `SolvikDiagnosticProgramTest` | `SolvikDiagnosticCodeCoverageTest` | fixtures | command 4 |
| Semantic clusters covered | 3 | feature tests | feature negatives | — | command 5 |
| Lowering clusters covered | 4 | execution tests | rejection tests | — | command 5 |
| Runtime clusters covered | 5 | runtime tests | error-path tests | optional `.sol` | command 5 |
| Parser/infra clusters covered | 6 | infra tests | parser negatives | — | command 5 |
| Launcher seam + coverage | 7 | `SolvikMainTest` | option/error cases | — | command 6 |
| Coverage gate enforced | 8 | command 7 | deliberate threshold raise | README | command 7, then 8 |
| Dead code resolved | 9 | n/a | n/a | — | commands 5, 8 |
| No spec/architecture change | all | — | — | unchanged | command 10 |
| Final quality gate (`build-all.sh`) | all | — | — | — | command 8 |
| Launcher smoke | all | — | — | — | command 9 |

Final status: **READY** — the plan is evidence-backed and contains no unresolved blocker. Decisions
D1–D3 are isolated in slice 9 and do not block slices 0–8.
