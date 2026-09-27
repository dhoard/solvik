# Solvik Prompts

Reusable agent prompts for this repository. They do not define language semantics and do not
override `AGENTS.md`, `docs/LANGUAGE_SPEC.md`, or `docs/ARCHITECTURE.md`.

| Prompt | Use it for |
|--------|------------|
| [PLAN-TEMPLATE.md](PLAN-TEMPLATE.md) | Produce an implementation-ready plan without editing code. |
| [fix-issues.md](fix-issues.md) | Careful, single-defect QA loop: prove a bug with a regression test, fix the root cause, verify with the required gates. |
| [fix-issues-adhd.md](fix-issues-adhd.md) | Same constraints as `fix-issues.md`, but the search starts from a randomly picked part of the project (ADHD-style exploration with a tangent Parking Lot). |
| [TEST-COVERAGE.md](TEST-COVERAGE.md) | Plan for coverage measurement and test-gap closure. |
| [INCLUDE-HASH-PLAN.md](INCLUDE-HASH-PLAN.md) | Plan for include/hash handling. |
