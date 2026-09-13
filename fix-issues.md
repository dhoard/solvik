Analyze the entire project for bugs, defects, edge cases, inconsistencies, code-quality issues, and nits.

## Repository context

This repository is the Solvik language transpiler: a single-module Java 17
Maven project that lowers Solvik source to deterministic, package-free Java.
Apply the instructions in this file to:

* the production sources under `src/main/java/org/solvik/transpiler/`
  (`Lexer`, `Parser`, `SemanticAnalyzer`, the typed IR, `IrOptimizer`, the Java
  lowering in `JavaIr`/`JavaEmitter`, the `Transpiler` service, and the
  `SolvikTranspiler` CLI);
* the JUnit 5 tests under `src/test/java/org/solvik/transpiler/`
  (`FrontendTests`, `ConformanceTest`); and
* the language conformance fixtures under `test/cases/` (each directory holds
  `main.sol` plus expected `expected.out` and/or `expected.code`).

Consult `AGENTS.md` for repository conventions, and `LANGUAGE.md`,
`SEMANTICS.md`, `TRANSPILER_JAVA.md`, and `CONFORMANCE.md` for intended
behavior. Ignore historical Rust artifacts (deleted `vendor/`, `Cargo.*`,
`src/*.rs`, `tests/*.rs`) unless they are still present in the working tree.

This is a hardening and bug-fixing pass, not a feature pass: fixes must remain
behavior-preserving with respect to the existing grammar, semantics, and public
API.

Repository validation commands:

* `./build.sh` (or `./mvnw -B clean verify`) is the full quality gate: it
  compiles, runs all unit and conformance tests, and produces
  `target/solvik.jar`.
* `./mvnw test` runs the JUnit 5 unit and conformance suites.
* `./mvnw clean verify` is the direct full gate (sources compile with
  `-Xlint:all -Werror`, so the build must be warning-free).
* `./transpile.sh <file.sol> <Class>` runs the built transpiler.
* `./solvik.sh <file.sol> [args...]` transpiles and runs a program in one step.

The objective is to leave the project as close to **100% bug-free and defect-free** as reasonably possible. Do not ignore an issue because it appears minor, cosmetic, unlikely, or low impact.

For every potential issue you identify:

1. **Investigate the issue**

   * Understand the relevant code path and intended behavior.
   * Do not assume an issue is real based only on static inspection.

2. **Create a test that validates or invalidates the issue**

   * Prefer an automated regression test whenever practical.
   * The test must reproduce or otherwise demonstrate the suspected problem.
   * Run the test before changing the implementation.
   * If the test proves the suspected issue is not actually a defect, document that conclusion and move on.
   * Do not modify production code for an invalidated issue.

3. **Implement a focused fix for every validated issue**

   * Fix the root cause rather than masking the symptom.
   * Keep the change as small and localized as reasonably possible.
   * Avoid unrelated refactoring unless it is required for correctness.
   * Preserve existing intended behavior.
   * Do not inject new language syntax, features, or behavior as a side effect of resolving the issue. Fixes must be behavior-preserving with respect to the language and its existing grammar, semantics, and public API.

4. **Validate every fix**

   * Re-run the test that demonstrated the issue and confirm it now passes.
   * Run relevant surrounding tests to detect regressions.
   * Run the project's full test suite whenever practical.
   * Run applicable build, lint, formatting, static-analysis, type-checking, and validation tools.

5. **Continue searching**

   * Do not stop after finding and fixing the first few issues.
   * Continue reviewing the project until you can no longer identify reproducible bugs, defects, edge cases, inconsistencies, or meaningful nits.

Treat all severity levels as actionable:

* Critical issues
* Major bugs
* Minor bugs
* Edge-case failures
* Error-handling problems
* Concurrency or lifecycle issues
* Resource leaks
* Incorrect assumptions
* API inconsistencies
* Misleading behavior
* Dead or unreachable code
* Poor validation
* Brittle logic
* Test gaps
* Documentation/code mismatches
* Small correctness or maintainability nits

Do not merely produce an issue report. **Reproduce, fix, and validate issues as part of the task.**

Do not weaken, remove, skip, or modify tests simply to make the test suite pass. Tests may only be changed when the existing test itself is demonstrably incorrect or the intended behavior has legitimately changed.

Before finishing:

* Confirm all newly added regression tests pass.
* Confirm all existing tests pass.
* Confirm the project builds successfully.
* Confirm applicable lint/static-analysis/type-checking tools pass.
* Review the final diff for accidental, unrelated, or incomplete changes.
* Perform one final pass through the project looking for additional bugs and nits introduced or missed during the earlier analysis.

The final result should contain **no known reproducible bugs or unresolved meaningful nits**.

At completion, provide a concise summary containing:

* Issues discovered
* Which issues were validated or invalidated
* Root cause of each validated issue
* Fix implemented
* Tests added or modified
* Validation performed
* Any remaining known limitations or unresolved issues

If there are no remaining known issues, explicitly state that no additional reproducible bugs or meaningful nits were found during the final review.
