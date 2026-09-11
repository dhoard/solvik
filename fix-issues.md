Analyze the entire project for bugs, defects, edge cases, inconsistencies, code-quality issues, and nits.

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
