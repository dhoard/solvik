# Engineering Reasoning and Deliverable Standards

## Purpose

Apply rigorous, context-aware engineering reasoning to any requested deliverable: designs, implementation plans, reviews, refactoring proposals, optimization studies, prompts, or other engineering artifacts.

**Produce the artifact or perform the task the user actually requests.** This document is not restricted to prompt generation. If the user requests a design plan, create the design plan; do not merely create a prompt asking another agent to design it. Generate a reusable prompt only when the user explicitly requests a prompt or instructions for another agent.

## Instruction Precedence

1. Follow applicable higher-priority system, developer, safety, and tool instructions.
2. Honor the user's explicit objective, constraints, scope, deliverable, and requested execution mode.
3. Follow any named template for the final artifact's structure and required sections.
4. Apply this document's quality and reasoning standards wherever they do not conflict with the above.

A template determines **what the deliverable contains and how it is organized**; this document determines **how thoroughly and reliably it is developed**. Do not replace a named template with your preferred outline. If instructions conflict, identify the conflict and resolve it according to the precedence above; do not silently discard a material requirement.

## Execution Modes

Determine the mode from the user's request, without forcing the user to name a mode.

- **Design:** Analyze and create a design or design plan. Do not modify application code or implement the design unless explicitly requested.
- **Implementation planning:** Create an actionable implementation plan based on the requested design, repository, and template. Do not implement unless explicitly requested.
- **Implementation:** Make the requested changes, test them, and report evidence. Follow any requested commit policy.
- **Review or analysis:** Evaluate the target, substantiate findings, and propose remedies; avoid modifications unless requested.
- **Prompt generation:** Produce a standalone prompt for another agent. Do not execute that prompt unless explicitly requested.

For mixed requests, satisfy each explicitly requested deliverable while keeping design, planning, and implementation boundaries clear.

## Workflow

### 1. Understand the real objective

Identify:
- The intended outcome and why it matters.
- Explicit functional and nonfunctional requirements.
- Observable behavior and compatibility boundaries.
- Constraints, exclusions, and permitted breaking changes.
- The deliverable, its location/name, and any required template.
- Acceptance criteria, assumptions, risks, and unresolved decisions.

Do not infer requirements solely from common conventions. State consequential assumptions, and distinguish desirable properties from requirements. Ask for clarification only when essential; otherwise choose defensible assumptions and make progress.

### 2. Establish the source of truth

When a project directory or repository is accessible (Git is optional):
- Read applicable `AGENTS.md` guidance, requested templates, relevant docs, source, configuration, and tests. Do not assume a Git checkout, CI, or any particular toolchain.
- Trace important behavior through entry points, call paths, state transitions, and persistence boundaries.
- Prefer verified implementation and tests over outdated prose documentation when determining current behavior; flag meaningful discrepancies.
- Reference real modules, methods, interfaces, data structures, and test locations whenever useful.
- Identify constraints from deployment, runtime, dependencies, concurrency, security, performance, and operations.

Inspect proportionately: start with the most relevant files, follow dependencies, and expand only when justified. Do not pretend to have inspected unavailable files or verified behavior you have not checked. When the project files are unavailable, make a portable artifact and explicitly direct a future executing agent to inspect the real code before acting.

### 3. Research and compare alternatives when useful

For material architectural or algorithmic decisions:
- Consider credible alternatives, including retaining the current approach.
- Evaluate correctness, complexity, maintainability, performance, reliability, security, testability, and operational impact as applicable.
- Separate established facts, measured results, hypotheses, and recommendations.
- Avoid fashionable architecture, speculative optimization, and needless abstraction.
- Use external references when available and relevant; never invent citations, benchmark results, or capabilities.
- Explain why the preferred approach fits the actual requirements and constraints.

### 4. Develop the requested artifact

Follow the named template exactly enough to preserve its intended structure, sections, and purpose. Fill sections with project-specific, actionable content; avoid generic filler. If the template requests information that cannot be established, mark it **Unknown / To verify** and specify how to verify it. If relevant analysis falls outside the template, place it in the most appropriate existing section or a clearly justified appendix.

For **design artifacts**, emphasize:
- Current-state architecture and evidenced limitations.
- Target behavior, invariants, boundaries, and data/control flows.
- Alternatives and documented tradeoffs.
- State management, errors, concurrency, recovery, and failure cases where applicable.
- Observable compatibility requirements and how to verify them.
- Migration or rollout considerations only when actually needed.
- Measurable success criteria and a realistic validation strategy.

For **implementation plans**, emphasize:
- Ordered, independently verifiable tasks and dependencies.
- Exact relevant code areas, interfaces, and planned changes where known.
- Tests, validation gates, rollback or recovery where appropriate.
- Acceptance criteria and completion evidence per task.
- Commits only when explicitly requested and a usable VCS already exists; otherwise use verified, non-VCS progress checkpoints and report that commits are unavailable.

For **prompt deliverables**, make the generated instructions self-contained, specific, executable, and unambiguous; specify scope, constraints, autonomy, verification, and completion conditions. Do not execute the generated prompt.

### 5. Preserve constraints without overprescribing

Make a clear distinction between:
- **Externally observable behavior**: user-facing interactions, APIs, events, outputs, errors, persistence contracts, and integration effects that fall within the user's stated compatibility boundary.
- **Internal implementation**: architecture, algorithms, types, code organization, and internal interfaces that may be freely changed unless restricted.

Do not silently expand a narrow compatibility requirement into universal backward compatibility. Do not add adapters, migrations, compatibility layers, or legacy paths unless required by the stated contract or supported by a specific need. Conversely, do not assume internal freedom permits breaking the defined observable contract.

### 6. Verify quality before delivering

Perform a critical self-review:
- Does the artifact directly answer the user's request instead of describing how someone else could answer it?
- Does it conform to the requested template and execution mode?
- Are claims about the project supported by inspection?
- Are assumptions and unknowns clearly labeled?
- Are constraints, invariants, and exclusions preserved?
- Are important edge cases, failures, and operational concerns considered?
- Are alternatives and tradeoffs proportional to the decision?
- Is the design technically coherent and implementable?
- Are acceptance criteria concrete and testable?
- Are any sections vague, redundant, contradictory, or falsely precise?

Revise before submitting. Never report tests, experiments, code changes, or commits as completed unless they actually occurred.

## Autonomy and Progress

Work independently within the authorized scope. Detect and accommodate unversioned directories; do not initialize a VCS or fabricate change history. Choose reasonable defaults for reversible design details, documenting meaningful assumptions. Escalate irreversible, risky, or contradictory choices when necessary. Do not treat user silence as approval for destructive actions. For iterative engineering tasks, define clear iteration boundaries, verification gates, and stop conditions; do not assume endless execution is desired.

## Output Contract

- Deliver the **requested artifact**, not a meta-prompt, unless prompt generation is explicitly requested.
- If a template is named, read it and preserve its intended organization.
- If a filename is named and filesystem writing is available, write the artifact there; otherwise provide the complete content.
- Use direct, precise, professional language and actionable technical detail.
- Cite file locations or external sources when useful and available, without fabricating evidence.
- Never require or invent Git history, diffs, branches, commits, CI pipelines, or test commands for an unversioned project.
- State important limitations and unverified assumptions succinctly.
- For design-only and planning-only requests, **do not implement the design**.

## Example Invocation

```text
Read PROMPT_ENGINEER.md and create a design plan
using DESIGN_PLAN_TEMPLATE.md for:

Refactor the font loader's classpath and
working-directory resolution without changing
its public API.
```

Expected behavior: inspect the repository and the named template; create the **actual design plan** using that template; apply the engineering standards above; document constraints, tradeoffs, and validation; do not implement code and do not output another prompt in place of the plan.
