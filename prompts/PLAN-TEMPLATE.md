# Solvik Planning Template

Use this prompt to produce an implementation-ready plan for a requested Solvik change. The plan is
advisory: it does not define language semantics and does not override repository instructions.

## Invocation

Provide a concrete task with this prompt:

> Follow `prompts/PLAN-TEMPLATE.md` exactly. Plan only; do not implement. Task: <task>

Unless the invocation names an output file, return the plan in the final response. If it names an
output file, write only that plan file. Do not modify implementation, test, example, build, or
normative documentation files.

## Role and Boundary

Produce a plan that another agent can execute from a fresh context without guessing. You may inspect
the repository and run read-only discovery commands. Do not edit source files, generate code, stage
changes, commit, or push. Do not claim that a test or build passed unless the invocation explicitly
authorized running it and it actually passed.

Planning quality comes from repository evidence, not detail invented to fill a template. Distinguish:

- **required** behavior stated by an authoritative document;
- **observed** behavior or structure found in the current repository;
- **proposed** implementation choices that the implementing agent must verify;
- **unresolved** questions or conflicts that prevent a safe implementation.

## Authority

Read these sources in order before planning:

1. `AGENTS.md`;
2. `docs/LANGUAGE_SPEC.md` when syntax, typing, diagnostics, or runtime semantics may change;
3. `docs/ARCHITECTURE.md` when compiler stages, dependencies, lowering, or runtime boundaries may
   change;
4. `README.md`, `CONTRIBUTING.md`, existing tests, examples, build files, and implementation code
   as non-normative evidence.

If the request conflicts with a higher-authority source, or requires a semantic decision that the
authoritative documents do not make, stop and report the exact conflict or missing decision. Do not
invent semantics or edit the specification to conceal the conflict.

## Required Investigation

Before writing the plan:

1. Inspect `git status --short`. Treat existing changes as user-owned and plan around them; never
   recommend discarding or overwriting them.
2. Restate the requested outcome and identify what is explicitly out of scope.
3. Trace the current implementation end to end for every affected behavior:

   ```text
   source
     -> lexer / semicolon insertion
     -> parser
     -> language AST
     -> symbol and name resolution
     -> static type analysis
     -> semantic validation
     -> typed/lowered representation
     -> Truffle AST execution
   ```

4. Inspect the relevant tests, diagnostics, examples, public documentation, module descriptors,
   registration metadata, launcher behavior, and native-image configuration.
5. Identify GraalVM/Truffle infrastructure that should remain and any obsolete or incompatible
   behavior that must be removed.
6. Verify all named files, symbols, test classes, commands, and extension points exist. Mark a new
   file or symbol explicitly as proposed.
7. Check whether grammar changes are needed. The grammar source is
   `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`; generated ANTLR sources belong
   under `target/generated-sources/antlr4` and must never be edited or checked in.

## Planning Rules

- Prefer small, ordered vertical slices that remain buildable and testable.
- Preserve the required front-end-to-Truffle pipeline; do not bypass static analysis.
- Never weaken static typing or defer a compile-time error to runtime for convenience.
- Preserve primitive Truffle representations and specialization where applicable.
- Preserve instrumentation and interoperability unless the requested behavior requires a documented
  change.
- Do not introduce SimpleLanguage syntax, dynamic semantics, compatibility modes, or legacy paths.
- Include removal work when a new implementation supersedes an old path.
- Name concrete files and symbols. Do not use vague steps such as “update the parser” or “add tests.”
- Tie every implementation step to a requirement, acceptance condition, or demonstrated defect.
- Do not broaden the task with unrelated cleanup or speculative abstractions.
- Do not prescribe manual edits to generated files.
- When alternatives remain, recommend one with repository-based reasons and state the tradeoff.
- If a section is genuinely irrelevant, write “Not applicable” with one sentence explaining why.

## Required Plan Format

# Plan: <concise task name>

## 1. Objective and Acceptance Criteria

- State the user-visible or internal outcome.
- List observable completion criteria.
- List explicit non-goals.
- State whether the request changes normative language behavior.

## 2. Authority and Repository Evidence

Provide a table:

| Requirement or fact | Classification | Source |
|---|---|---|
| What must be true | required / observed / proposed / unresolved | Document section, file and symbol, or test |

Use paths and section or symbol names. Add line numbers only when useful; do not rely on line numbers
as the sole identifier.

## 3. Current Implementation Trace

Describe the existing path through only the affected compiler/runtime stages. Identify:

- the entry point for the behavior;
- representations passed between stages;
- where validation currently occurs;
- where executable Truffle nodes are created;
- current diagnostics and source-span handling;
- existing tests and examples;
- infrastructure to retain;
- behavior to replace or remove.

Do not repeat unaffected architecture.

## 4. Change Surface

Provide a table:

| File or proposed file | Symbols | Planned responsibility | Why it changes |
|---|---|---|---|

Include production code, tests, examples, documentation, module descriptors, metadata, and build or
native-image files when relevant.

## 5. Ordered Implementation Slices

For each slice, provide:

### Slice <n>: <outcome>

- **Dependencies:** Prior slices or existing facilities required.
- **Implementation:** Exact files, symbols, data flow, and invariants to change.
- **Removal/migration:** Superseded paths or behavior to delete.
- **Diagnostics:** Codes, messages, severity, and source locations to add or preserve.
- **Tests:** Specific positive, negative, edge, and regression cases added with this slice.
- **Focused validation:** Exact command or test selector that gives fast feedback.
- **Done when:** Observable criteria for completing the slice.

Sequence grammar, AST, semantic, lowering, and runtime work according to their actual dependencies.
Avoid broad scaffolding that cannot be exercised by the slice's tests.

## 6. Test Matrix

Provide a table:

| Layer | Positive coverage | Negative/edge coverage | Test file or proposed test file |
|---|---|---|---|

Consider each applicable category:

- lexical and semicolon insertion;
- parsing and AST shape;
- name resolution and static typing;
- semantic diagnostics, including exact diagnostic code and source span;
- typed lowering;
- JVM execution and observable output;
- inheritance, interfaces, delegation, nullability, generics, or pattern matching interactions;
- Truffle instrumentation and polyglot interoperability;
- launcher, distribution, registration, and native-image behavior;
- regression coverage for adjacent existing features.

Every semantic feature needs positive and negative coverage. Prefer extending the nearest focused test
class; propose a new class only when it creates a clear test boundary.

## 7. Examples and Documentation

State exactly what should change:

- Add or update `language/tests/*.sol` and matching `*.output` golden files when the behavior is
  useful as an executable example.
- Update `README.md` or `standalone/README.md` for user-facing commands, artifacts, or behavior.
- Update `docs/LANGUAGE_SPEC.md` only when the requested change intentionally changes normative
  semantics and the user has authorized that design change.
- Update `docs/ARCHITECTURE.md` only when compiler/runtime boundaries actually change.
- Preserve required copyright, license, and attribution notices.

Document not only additions but obsolete documentation or examples that must be removed.

## 8. Validation

List commands in execution order:

1. focused tests for each slice;
2. `./build.sh` for final JVM validation;
3. `./build-native.sh` when runtime, registration, launcher, distribution, native-image
   configuration, or native-sensitive behavior changes;
4. direct JVM/native launcher examples when observable command-line behavior changes;
5. final `git diff --check`, repository-status review, and diff review.

Use the Maven wrapper and GraalVM selected by the repository wrappers. If a focused Maven command is
needed, explicitly set a GraalVM for JDK 25 as required by `AGENTS.md`. Do not rely on stale build
outputs.

For each command, state what failure it is intended to catch.

## 9. Risks, Decisions, and Open Questions

List:

- architectural or semantic risks;
- interactions with existing features;
- performance or Truffle-specialization concerns;
- source-compatibility implications;
- assumptions the implementing agent must verify;
- unresolved questions requiring user or specification input.

Do not put a blocking unresolved question into the implementation sequence as though it were decided.

## 10. Completion Checklist

Map every acceptance criterion to:

- the implementing slice;
- its positive test;
- its negative or edge test;
- its documentation/example impact;
- its final validation command.

End with one of:

- **READY** — the plan is evidence-backed and has no unresolved blocker;
- **BLOCKED** — followed by the exact conflict or decision needed before implementation.

## Final Audit

Before returning the plan, verify:

- every requested outcome has an implementation step and validation;
- every applicable compiler stage was considered;
- positive, negative, edge, and regression tests are concrete;
- examples and documentation were considered explicitly;
- generated files are not scheduled for manual editing;
- required build wrappers are included;
- no SimpleLanguage compatibility path is introduced;
- no unsupported semantic decision is presented as fact;
- no unrelated work is included;
- the plan can be executed without relying on hidden conversation context.
