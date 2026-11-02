# Implementation Plan — <short outcome-focused title>

**Status:** Draft / Ready / In Progress / Implemented / Blocked / Superseded  
**Request:** <issue, user request, or reference>  
**Design:** <path to governing `.design.md`, or Not required — reason>  
**Design decision:** Proposed / Accepted / Explicitly approved / Not required — <reason>  
**Implementation authorization:** Planning only / <explicit user request authorizing execution>  
**Scope:** <modules, components, files, integrations, documentation affected>  
**Related:** <PR, issue, dependent changes, or none>

> **Authoring instructions — delete this block from the finished plan.** Read applicable [AGENTS.md](AGENTS.md)
> files, the root [PROJECT.md](PROJECT.md), the current project directory, relevant project contracts,
> and the linked design. Resolve companion files from the intended project root, not a VCS root or
> invocation directory. Missing configuration permits safe discovery, not guesses about commands or
> permission. Save as `plans/<descriptive-kebab-case-name>.impl.md` unless `PROJECT.md` or the user
> specifies otherwise. Use [DESIGN.md](DESIGN.md) for a separate design when needed. This document defines
> **how to build, test, and verify** the specified behavior, then records **what actually happened**.
> Do not re-decide design questions silently. A plan is not authorization to edit code. Replace all
> placeholders with verified project-specific details before treating the plan as ready. Retain all
> numbered sections, marking inapplicable sections with a reason. Distinguish planned commands
> from actual results.
>
> **Length and detail:** Aim for 1,500–2,000 words or fewer for the initial plan; small changes
> should need less. This is a target, not a hard cap: exceed it only where needed to resolve
> ambiguity or preserve a critical contract, and briefly explain the need. Keep all nine numbered
> sections, concrete files and changes, regression coverage, exact verification commands, blockers,
> and rollback considerations. Reference the governing design's R-xx and D-xx identifiers instead
> of restating its requirements, decisions, and rationale. State each fact or planned command once,
> then reference its section, step, or verification row elsewhere. Keep table cells concise and
> avoid repeating their contents in surrounding prose. Expand implementation substeps only where
> needed to make execution unambiguous. The execution record may grow as actual results accumulate;
> retain the evidence needed for an accurate handoff.

## 1. Design contract and readiness

**Design baseline:** <Link exact design document and any required approval/acceptance evidence. If no design document is
needed for an explicitly authorized small fix, state the rationale and behavior being protected.>

**Implementation target:** <What has to be true after the change, in terms of the governing design.>

**Requirements / decisions to honor:** <List R-xx / D-xx from the design and applicable PROJECT.md
instructions, constraints, and authoritative references; verify them against current evidence.>

**Out of scope:** <No unrelated refactors or speculative changes.>

**Preconditions / blockers:** <Toolchain, dependencies, permissions, decisions, baselines, or none.
If behavior-defining design issues are unresolved, do not mark the plan Ready.>

## 2. Verified starting state and change inventory

Reinspect the current project directory before implementation. The design's source references may have aged. Git, CI, and even automated tests may be absent; record what verification mechanisms actually exist.

| Evidence | Current location / command | Verified finding | Impact on plan |
| --- | --- | --- | --- |
| Code path / test / config / spec | <path and symbol or exact command> | <observed fact, not guess> | <step affected> |
| Build / existing test baseline | <exact command, or not run> | <actual result / unknown> | <risk or dependency> |

**Existing user changes and protected artifacts:** <What must be preserved, including pre-existing
files/edits, generated paths, or external resources. Identify VCS if present; otherwise record a
proportionate file inventory or content baseline.>

**Affected change inventory:**

| Component / path | Planned modification | Owning contract / requirement |
| --- | --- | --- |
| <concrete file/symbol; if new, exact proposed path> | <specific change> | R-01 / D-01 |
| <test/fixture/doc path> | <coverage or document update> | R-02 |

Do not invent a source file, tool, test framework, or command before inspecting the project.

## 3. Ordered implementation steps

Each step has a bounded deliverable, its prerequisite, a regression or verification check,
and a completion condition. Implement from the lowest appropriate boundary outward.

| Step | Prerequisites | Concrete change | Coverage / evidence | Done when |
| --- | --- | --- | --- | --- |
| I-01 | <baseline> | <test/fixture proving R-01> | <specific test path or check> | <fails for the confirmed old defect, or pins the new contract> |
| I-02 | I-01 | <implementation paths and symbols> | <focused test> | <new behavior passes and invariants hold> |
| I-03 | I-02 | <consumers, docs, migration/packaging as needed> | <integration/compatibility check> | <all touched boundaries agree> |

For substantial work, expand the table into explicit substeps with dependency order, rollback
considerations, expected artifacts, and handoff points. Avoid vague steps such as “implement the
feature” or “add tests.” Keep optional improvements out of the required critical path.

## 4. Requirements-to-changes traceability

Every **required** design criterion must have an implementation owner and a way to verify it.

| Requirement / decision | Step(s) | Files / components | Verification evidence planned |
| --- | --- | --- | --- |
| R-01 / D-01 | I-01, I-02 | <concrete paths> | <test / probe / inspection> |
| R-02 | I-03 | <concrete paths> | <regression / compatibility check> |

Explicitly state **Not applicable — <reason>** for any common concern that is materially irrelevant
(e.g., migration, public API, concurrency, release packaging). Do not omit a required design
criterion merely because it is hard to test.

## 5. Regression and edge-case matrix

Add deterministic positive and negative checks for relevant boundaries. Record the required
absence of side effects, not just the expected success output.

| Scenario | Expected outcome / prohibited behavior | Test or verification boundary | Covers |
| --- | --- | --- | --- |
| Normal behavior | <required result> | <test path and case> | R-01 |
| Invalid, empty, or boundary input | <safe rejection / exact behavior> | <test path and case> | R-01 |
| Failure, interruption, cancellation | <safe rollback/recovery; no partially applied state> | <test/controlled probe> | R-01 |
| Retry, parallelism, or repeated action | <idempotency/ordering; no duplicate effect> | <test/controlled probe> | R-02 |
| Existing consumer / version / migration | <compatibility preserved or intentional break> | <integration/contract test> | R-02 |

Use owned temporary state, hermetic fixtures where practical, and explicit event coordination
rather than timing-dependent sleeps. Real network/services, credentials, and destructive test state
need clear justification and explicit authorization.

## 6. Planned verification commands (not execution results)

Use **only verified commands from PROJECT.md and the project's scripts, CI (if any), or documented
tooling**. Resolve stale or conflicting configuration before dependent checks. Replace
all placeholders with actual commands and target names. State prerequisites, working directory,
and side effects. If a verification gate cannot be run here, mark it as planned-but-unavailable.

| Order | Exact command or manual procedure | Purpose / acceptance gate | Prerequisite |
| --- | --- | --- | --- |
| 1 | `<focused test command>` | <specific regression> | <toolchain> |
| 2 | `<integration / contract test command>` | <cross-boundary behavior> | <build artifacts> |
| 3 | `<project-defined full verification command>` | <required project gate> | <environment> |
| 4 | `<diff / static check command>` | <review / formatting / lint> | <tool> |

**Behavior probes:** <Exact executable invocation and expected observable output when automated
tests do not fully cover user-visible behavior. Do not run against real user state.>

**Pass criteria:** <Required gates and their expected observations, consistent with the design.>

## 7. Execution safety, integration, and rollback

**Change safeguards:** <User edits and files to preserve, optional VCS or non-VCS baseline, owned test directories, generated output rules,
external effects to avoid, secrets handling, and whether parallel edits may race.>

**Integration and release considerations:** <Dependency ordering, deployment, migration,
compatibility, documentation, and other affected consumers; or not applicable with reason.>

**Rollback / recovery:** <How to undo the code change and, if relevant, safely handle migrated
persistent state or artifacts. A source revert may not reverse a data-format change.>

**Scope control:** <How material design deviations will be escalated and recorded. Do not add
unrelated refactors or silently redefine the governing behavior.>

## 8. Risks and decisions needed during execution

| Risk / unknown | Consequence | Proposed mitigation / evidence needed | Blocking? |
| --- | --- | --- | --- |
| <risk> | <impact> | <mitigation and verification> | Yes / No |

For a new behavior-defining question, amend the governing design and obtain a decision when required by the project workflow or the authorized scope. A technical
obstacle or a failing gate belongs here until resolved; it does not count as acceptance.

## 9. Execution record and handoff

> **Fill this section with observed facts only when implementation is authorized and executed.**
> Leave it clearly marked “Not executed — plan only” otherwise. Never substitute expected results
> for actual command output.

**Execution status:** Not executed — plan only / In progress / Implemented / Blocked  
**Implementation authorization evidence:** <request authorizing code changes; or planning only>  
**Change tracking:** <VCS and revision(s) if available, or non-VCS paths/baseline; commits only if explicitly requested>  
**Implemented changes:** <Actual files and behavior changed; or none>  
**Deviations from plan/design:** <Actual deviations, approval reference, or none>  
**Documentation / migrations / generated artifacts:** <Actual updates or not applicable>

### Commands actually run and observed results

| Exact command / check | Actual result | Evidence / failure details |
| --- | --- | --- |
| <only commands actually executed> | Passed / Failed / Blocked | <observed result, logs, artifact path> |

**Not run:** <List required or planned checks not performed and why.>  
**Baseline failures:** <Pre-existing issues distinguished from newly introduced failures.>  
**Remaining work:** <Unmet requirements, blockers, deferred work, and risk owner.>

### Final acceptance checklist

- [ ] Required R-xx criteria are implemented and supported by tests or other concrete evidence.
- [ ] Relevant negative cases and preserved contracts have regression coverage.
- [ ] Affected consumers, docs, schemas, fixtures, and release artifacts have been updated as needed.
- [ ] Focused tests and applicable full project gates passed, or gaps are explicitly recorded above.
- [ ] Implementation contains no introduced stubs, temporary debug code, misleading success paths,
      or unrelated changes.
- [ ] Changes reviewed against the recorded baseline (VCS diff/status if available, otherwise file inspection/inventory); user edits and external state preserved.
- [ ] No material deviation from governing design lacks a recorded decision.
- [ ] All known unverified behavior, skipped gates, and limitations have been disclosed.

**Final assessment:** <Complete with evidence / Partial / Blocked, with specific explanation.
Never express an unverifiable “100% confidence” as a substitute for evidence.>
