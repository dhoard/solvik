# Design Plan — <short outcome-focused title>

**Status:** Draft / Proposed / Approved / Deferred / Superseded  
**Request:** <issue, user request, or reference>  
**Scope:** <affected systems, components, and externally visible surfaces>  
**Related:** <existing designs, ADRs, specifications, and follow-ups; or none>  
**Decision authorization:** Pending / <explicit decision reference> / Not required by project workflow  
**Implementation plan:** <path when created; otherwise not yet created>

> **Authoring instructions — delete this block from the finished plan.** Read applicable [AGENTS.md](AGENTS.md)
> files and the root [PROJECT.md](PROJECT.md), then project specifications, relevant source, tests,
> and available build/verification guidance. Resolve companion files from the intended project root,
> not a VCS root or invocation directory. Missing configuration permits safe discovery, not guesses
> about commands or permission. Git or another VCS is not required. Save as
> `plans/<descriptive-kebab-case-name>.design.md` unless `PROJECT.md` specifies otherwise.
> This document defines **what and why**, not a file-by-file implementation schedule. A plan does
> not authorize code changes. Replace each placeholder with concrete evidence or an explicit
> unknown. Mark an irrelevant consideration **Not applicable — <reason>**. Retain all numbered
> sections, scaling their detail to the actual complexity. Never mark the design
> approved without explicit approval. Formal approval is not mandatory unless the user or project requires it.
>
> **Length and detail:** Aim for 1,500–2,000 words or fewer; small changes should need less.
> This is a target, not a hard cap: exceed it only where needed to resolve ambiguity or preserve
> a critical contract, and briefly explain the need. Keep all nine numbered sections and required
> evidence, decisions, acceptance criteria, failure behavior, and open questions.
> State each fact once, then reference its section, R-xx, or D-xx identifier elsewhere.
> Link to authoritative specifications, architecture, and project rules instead of reproducing
> them. Keep table cells concise and avoid repeating their contents in surrounding prose.
> Include only findings and alternatives that affect the proposed behavior or its evaluation.

## 1. Problem, goal, and observable outcome

**Problem / opportunity:** <Who experiences what problem? For defects, distinguish reported
symptoms from independently reproduced behavior.>

**Goal:** <The externally observable outcome; avoid implementation details.>

**Success looks like:** <A concise description of the observable change and how it can be checked.>

**Must remain unchanged:** <Behavior and guarantees that the change must preserve.>

## 2. Current state and evidence

Inspect the **current project directory**; do not assume an old plan reflects current behavior.

| Evidence type | Source / symbol / command | Observed finding | Verification state |
| --- | --- | --- | --- |
| Source / spec / test / reproducer | <exact reference> | <specific finding> | Observed / Not run / Hypothesis |
| Source / spec / test / reproducer | <exact reference> | <specific finding> | Observed / Not run / Hypothesis |

**Current flow and owning components:** <Briefly trace the relevant end-to-end behavior and
component boundaries. Link to the authoritative architecture rather than duplicating it.>

**Root cause or gap:** <Causal explanation backed by evidence; label hypotheses explicitly.>

**Baseline:** <Existing failures, environment assumptions, compatibility constraints, generated
artifacts, and pre-existing user files/edits to protect. State whether VCS exists; if not, use file
inventory or another proportionate baseline method. Say what has not been verified.>

## 3. Scope, requirements, and acceptance criteria

**In scope:** <Smallest coherent behavior change and all necessary affected consumers.>

**Out of scope:** <Deliberately excluded features, cleanup, migration, or optimization.>

**Dependencies / affected boundaries:** <Ownership, call paths, dependency direction, integrations,
and external users affected by the design.>

Use stable requirement identifiers so the implementation plan can trace each requirement to work
and tests. Requirements must describe **behavior**, not an implementation mechanism.

| ID | Requirement / preserved invariant | Observable acceptance criterion | Priority |
| --- | --- | --- | --- |
| R-01 | <required behavior> | <unambiguous pass/fail observation> | Required |
| R-02 | <preserved behavior> | <regression assertion or proof> | Required |

For stateful, asynchronous, distributed, or persistent changes, include a transition table. If not
relevant, state why.

| Starting state | Trigger / action | Expected result | Forbidden result |
| --- | --- | --- | --- |
| <state> | <event> | <observable transition> | <lost data, duplicate action, partial state, etc.> |

## 4. Proposed design and rationale

**Overview:** <Proposed end-to-end behavior, responsibilities, control/data flow, and interfaces.
Use concise diagrams only if they clarify the proposal.>

**Contracts and invariants:** Identify relevant items, stating **unchanged** or **not applicable**
where appropriate:

- Public APIs, command surface, schemas, wire formats, protocols, and error/exit behavior.
- Data ownership, lifecycle, persistence, migrations, backup/recovery, and compatibility.
- Ordering, concurrency, retries, idempotency, timeouts, cancellation, and resource limits.
- Security, permissions, trust boundaries, secrets, and failure isolation.
- Determinism, performance, reliability, accessibility, and supported environments.
- Documentation, tooling, deployment, distribution, and downstream consumers.

**Design decisions** (add one row per material decision):

| ID | Decision | Chosen approach | Rationale and rejected alternative |
| --- | --- | --- | --- |
| D-01 | <design question> | <specific decision> | <trade-off and evidence> |

**Alternatives considered:** <Meaningful options, why they were not selected, and any circumstances
under which the decision should be revisited. Avoid invented benchmarking or unsupported claims.>

## 5. Edge cases and failure behavior

Select relevant cases and explicitly justify material exclusions. Define behavior independently
from test class names or test commands.

| Scenario | Required result | Forbidden side effect / preserved invariant | Requirement |
| --- | --- | --- | --- |
| Normal usage | <result> | <none or constraint> | R-01 |
| Invalid / boundary input | <diagnostic or safe behavior> | <no corruption or misleading success> | R-01 |
| Failure / cancellation / interruption | <recovery behavior> | <no partial published state> | R-01 |
| Retry / concurrency / duplicate event | <exact behavior> | <no lost or duplicate effects> | R-02 |
| Upgrade / restart / compatibility | <durable contract> | <no silent incompatibility> | R-02 |

## 6. Compatibility, migration, and operational impact

**Compatibility:** <Backward/forward compatibility, existing data or users, versions and public
contracts. If unaffected, explain why.>

**Migration / rollout / rollback:** <Whether coordinated changes, feature flags, data migrations,
or reversible cutovers are required; if not, say why.>

**Operational considerations:** <Observability, logging, metrics, supportability, security, deploy
ordering, resource usage, or documentation where relevant.>

## 7. Validation strategy

Describe **what evidence would demonstrate correctness**, leaving exact test commands, file edits,
and execution results to the implementation plan.

| Requirement | Proof / test boundary | Negative or preservation check |
| --- | --- | --- |
| R-01 | <unit / contract / integration / end-to-end / inspection> | <failure mode or invariant> |
| R-02 | <specific behavior to verify> | <must stay unchanged> |

**Project-specific gates:** <Reference PROJECT.md and verify its applicable CI, build, quality,
compatibility, and release requirements against current evidence. State unknowns or justified
non-applicability; do not invent a stack-specific command or assume CI/VCS exists.>

**Limitations of proposed validation:** <What cannot be verified in the current environment.>

## 8. Risks, assumptions, and open decisions

| Item | Impact | Evidence / mitigation / decision needed | Blocking? |
| --- | --- | --- | --- |
| <risk, assumption, or open question> | <consequence> | <how it will be resolved> | Yes / No |

Separate unresolved **behavior-defining** decisions from acceptable implementation freedom.
Do not describe open questions as settled. State whether rollback is constrained by persistent
state, public contracts, or schema changes.

## 9. Decision and handoff

**Decision status:** <Draft / Proposed / Approved / Deferred / Superseded>  
**Decision evidence:** <Explicit decision/approval if required; otherwise proposed or not required>  
**Selected approach:** <Short summary of D-xx decisions and key non-goals>  
**Required implementation constraints:** <Critical R-xx, D-xx, and project rules to carry forward>  
**Unresolved blockers:** <None, or specific questions that must be answered first>  
**Next artifact:** `plans/<descriptive-kebab-case-name>.impl.md` after the design is accepted under the applicable workflow, or
<explicit reason a separate implementation plan is unnecessary>.

> **Handoff boundary:** Acceptance or approval of a design does not by itself authorize editing
> production code. The implementation plan must reference this design, preserve its governing
> decisions, and report proposed deviations explicitly. Do not assume Git or a formal review process.
> Use the root `IMPLEMENT.md` for the implementation-plan structure; adjust any generated-plan links to their actual locations.
