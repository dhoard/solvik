# Implement Solvik — One Phase

Convert this repository from its inherited SimpleLanguage implementation into Solvik. Reuse valuable GraalVM/Truffle infrastructure, but do not preserve SimpleLanguage source compatibility, dynamic semantics, public identity, or a legacy execution mode.

## Read First

Read these files in full, in this order:

1. `AGENTS.md`
2. `docs/LANGUAGE_SPEC.md`
3. `docs/ARCHITECTURE.md`
4. `docs/IMPLEMENTATION_PLAN.md`
5. `docs/TEST_PLAN.md`
6. `docs/STATUS.md`
7. the phase prompt selected below, if one exists

The authority order in `AGENTS.md` applies. This prompt controls execution but does not define language semantics.

## Select Exactly One Phase

Read the `NEXT` value in `docs/STATUS.md`. Implement that phase only.

| Phase | Additional prompt |
|---|---|
| 0 | `prompts/PHASE_0_BASELINE.md` |
| 1 | `prompts/PHASE_1_FRONTEND.md` |
| 2 | `prompts/PHASE_2_SEMICOLONS.md` |
| 3 | `prompts/PHASE_3_RAW_STRINGS.md` |
| 4–16 | Use the exact scope and exit criteria in `docs/IMPLEMENTATION_PLAN.md` |

Do not implement, scaffold, rename for, or partially prepare a later phase. Do not change `NEXT` until every exit criterion for the current phase passes.

## Required Workflow

1. Inspect the inherited implementation relevant to the active phase.
2. State which infrastructure will be retained, adapted, replaced, or removed.
3. Implement the smallest complete change satisfying the phase.
4. Modify grammar or generator inputs, never generated parser files by hand.
5. Add positive and negative tests required by `docs/TEST_PLAN.md`.
6. Run focused tests.
7. Run `./build.sh`.
8. Run `./build-native.sh` when required by `AGENTS.md`.
9. Fix failures caused by the phase.
10. Remove production SimpleLanguage paths superseded by the completed Solvik work.
11. Update `docs/STATUS.md` with commands, results, limitations, and the immediately following phase as `NEXT`.
12. Stop.

If the baseline is already failing, distinguish pre-existing failures from regressions with evidence. Do not claim completion while a required build or test fails.

## Non-Negotiable Boundaries

- Solvik source must follow the pipeline in `docs/ARCHITECTURE.md`.
- Executable Truffle nodes are not the source AST or type checker.
- A program with compile-time errors must not be lowered or executed.
- The Truffle AST backend is the only initial Solvik backend.
- Do not port Solvik to the inherited Bytecode DSL during initial development.
- Do not add a SimpleLanguage compatibility flag, parser, mode, or supported source path.
- Do not weaken static typing or use `Any` as an escape hatch.
- Preserve primitive specialization, instrumentation, interop, source locations, and licensing where compatible with Solvik.

## Stop Conditions

Stop without modifying code and report exact file and line references when:

- authoritative documents conflict;
- the active phase requires unspecified semantics;
- a requested change belongs to a later phase;
- generated-source tooling cannot be reproduced;
- required build prerequisites are unavailable.

Do not resolve these conditions by guessing.

## Completion Report

Report only:

1. phase completed;
2. files changed;
3. behavior implemented and removed;
4. tests added;
5. commands and results;
6. remaining transitional code or limitations;
7. next phase recorded in `docs/STATUS.md`.
