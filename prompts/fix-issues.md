# Solvik QA Bug-Fix Prompt

Act as a careful QA engineer and maintainer. Find one real defect, prove it with a regression test,
and fix it with the smallest safe change. Work autonomously, but do not invent requirements or
change the language design.

## Goal

Complete this exact loop:

1. Find a plausible defect in the current repository.
2. Write a focused regression test before changing production code.
3. Run the test and use the result to confirm or reject the defect.
4. If confirmed, fix the root cause.
5. Prove the fix with focused tests and both required clean-build quality gates.

Deliver one confirmed, fixed issue. If a candidate is invalid, discard only the test changes made for
that candidate and investigate another candidate. Never force a fix for an unproven suspicion.

## Non-Negotiable Constraints

- Follow `AGENTS.md`. It overrides this prompt.
- Treat `docs/LANGUAGE_SPEC.md` as the authority for syntax and semantics and
  `docs/ARCHITECTURE.md` as the authority for compiler/runtime boundaries.
- Do not change language design, normative semantics, or documented intended behavior.
- Do not add a feature, compatibility mode, legacy SimpleLanguage path, second execution backend,
  or speculative abstraction.
- A bug fix may change only the demonstrated incorrect behavior needed to restore behavior already
  required by authoritative documentation or an established repository invariant.
- Do not edit `docs/LANGUAGE_SPEC.md` or `docs/ARCHITECTURE.md` to justify a fix.
- Do not edit generated ANTLR output. Edit `Solvik.g4` only if the confirmed implementation bug is in
  the grammar and the existing specification already defines the required behavior.
- Preserve GraalVM/Truffle infrastructure, primitive representations, instrumentation, and
  interoperability unless the confirmed defect is specifically in that infrastructure.
- Do not weaken assertions, broaden expected results, disable tests, catch and ignore failures, or
  move a compile-time failure to runtime.
- Do not modify unrelated files or clean up unrelated code.
- Do not stage, commit, push, or discard user-owned changes.
- `./build.sh` and `./build-native.sh` are mandatory final quality gates. Both must pass.

If the authoritative documents conflict, or they do not decide behavior needed to determine whether
a candidate is a bug, stop and report the exact conflict or missing decision. Do not guess.

## Phase 1: Establish a Safe Baseline

Before editing:

1. Run `git status --short` and record the existing changed and untracked files. They are user-owned.
2. Read `AGENTS.md`, `docs/LANGUAGE_SPEC.md`, and `docs/ARCHITECTURE.md`.
3. Inspect the relevant production code and nearby tests before selecting a candidate.
4. Determine the normal focused-test command from the Maven configuration and existing tests. Use
   the repository wrappers for final validation and GraalVM for JDK 25 for any focused Maven command.
5. Run the nearest existing focused test class before editing and record whether it passes. This
   separates a new reproduction from a pre-existing test failure.
6. Do not run a full build merely to search for a defect. Start with source inspection and focused
   tests.

## Phase 2: Find One High-Confidence Candidate

Look for a narrow, reachable mismatch between the implementation and an existing requirement or
invariant. Good candidates include:

- a documented edge case implemented differently from adjacent cases;
- a compiler stage dropping or misinterpreting information from an earlier stage;
- a missing compile-time validation that is explicitly required;
- an incorrect source span or diagnostic classification with an established expected form;
- a lowering/runtime mismatch for an already-supported language construct;
- a launcher, native-image, interop, or instrumentation path that disagrees with its JVM path;
- an unhandled boundary value, state transition, or error path in existing behavior.

Do not select a candidate based only on a `TODO`, code style, an optimization opportunity, a stale
name, unsupported behavior, or personal preference. Do not treat a feature absent from the
specification as a bug.

For each candidate, write a short hypothesis before editing:

```text
Candidate: <one-sentence suspected defect>
Expected: <behavior required by exact document section, test precedent, or invariant>
Actual: <implementation path that appears to violate it>
Test: <smallest observable reproducer>
Likely cause: <file and symbol>
```

Trace the candidate only through affected stages of this pipeline:

```text
source -> lexer/semicolon insertion -> parser -> language AST -> name resolution
       -> static type analysis -> semantic validation -> typed/lowered representation
       -> Truffle AST execution
```

## Phase 3: Test Before Fixing

Add the smallest regression test in the nearest existing test class. The test must:

- exercise the public or subsystem boundary where the defect is observable;
- have one clear expected result grounded in existing authority;
- fail on the current implementation for the predicted reason;
- avoid depending on generated files, stale build output, timing, test order, or environment quirks;
- include positive and negative/edge coverage when both are needed to distinguish the defect from a
  valid rejection;
- follow existing test helpers and assertion style.

Run only the new test, or the narrowest relevant test class, before editing production code.

Classify the candidate from evidence:

- **Confirmed:** the new test fails because production behavior contradicts the established oracle.
- **Invalid:** the test passes, the expectation is unsupported, or the failure is caused by the test,
  environment, or unrelated existing changes.
- **Blocked:** the oracle is ambiguous or conflicts with higher-authority documentation.

Do not change production code unless the candidate is **Confirmed**. For an invalid candidate,
remove only the edits you introduced for it, preserve all prior user changes, and return to Phase 2.
For a blocked candidate, report the blocker rather than choosing semantics.

## Phase 4: Fix the Confirmed Root Cause

Once the regression test fails as predicted:

1. Identify the root cause, not merely the failing branch.
2. Make the smallest coherent production change that satisfies the existing requirement.
3. Keep validation in the correct compiler stage. Do not bypass static analysis or patch only the
   Truffle runtime when the condition is statically knowable.
4. Preserve source locations and existing diagnostic conventions.
5. Avoid broad refactors. If a refactor is necessary for correctness, explain why and keep it local.
6. Add only adjacent regression cases needed to cover the same root cause.
7. Do not update the test expectation to match the old bug or the proposed implementation.

## Phase 5: Validate in This Order

Run and record each result:

1. The exact regression test that previously failed. It must now pass.
2. The nearest relevant test class or module test suite.
3. `./build.sh`
4. `./build-native.sh`
5. `git diff --check`
6. `git status --short` and a complete review of `git diff`

Both build wrappers are required even if the change appears unrelated to native image. They invoke
clean builds; do not replace them with direct Maven commands or claim success from stale outputs.

If a gate fails, diagnose it. Fix failures caused by your change and rerun the failed gate plus every
later gate. Do not alter unrelated user work. If an unrelated pre-existing failure prevents
completion, report the exact command and failure evidence without claiming success.

During final diff review, verify:

- every changed line is necessary for the confirmed issue;
- the regression test would fail again if the production fix were removed;
- no intended language behavior or public contract changed;
- no generated file, build artifact, or unrelated formatting change is included;
- existing user changes remain intact.

## Final Response

Report only evidence-backed results using this structure:

```text
Issue: <concise confirmed defect>
Oracle: <authoritative requirement or established invariant>
Reproduction: <test name and the observed pre-fix failure>
Root cause: <file/symbol and concise explanation>
Fix: <files changed and what was corrected>
Coverage: <new or updated tests, including edge coverage>
Validation:
- <focused test>: PASS/FAIL
- <relevant suite>: PASS/FAIL
- ./build.sh: PASS/FAIL
- ./build-native.sh: PASS/FAIL
- git diff --check: PASS/FAIL
Scope: <confirmation that no language design or unrelated behavior changed>
```

Never say an issue was confirmed unless its regression test failed before the production fix. Never
say a command passed unless you ran that exact command successfully in this worktree.
