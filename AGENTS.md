# Solvik Implementation Instructions

This repository is an in-place conversion of `graalvm/simplelanguage` into **Solvik**, a strongly and statically typed object-oriented language implemented on GraalVM/Truffle.

## Primary Rule

Do not treat this as a greenfield VM. Preserve useful GraalVM/Truffle infrastructure while replacing SimpleLanguage syntax, semantics, public identity, and tests in controlled stages.

SimpleLanguage source compatibility must be removed. Do not add a compatibility flag, dual parser, legacy language mode, or permanent SimpleLanguage execution path. Transitional SimpleLanguage implementation code may remain only while an active phase still depends on it; it must not be presented as supported behavior and must be removed when its Solvik replacement is complete.

The target compilation pipeline is:

```text
Solvik source
  -> lexer / semicolon insertion
  -> parser
  -> language AST
  -> symbol/name resolution
  -> static type analysis
  -> semantic validation
  -> typed/lowered representation
  -> Truffle AST execution
```

The existing SimpleLanguage parser-to-executable-node path must not become the permanent Solvik architecture. Static semantic analysis must happen before executable Truffle nodes are produced.

## Authority

Use this precedence order:

1. `AGENTS.md` for repository-wide constraints;
2. `docs/LANGUAGE_SPEC.md` for Solvik syntax and semantics;
3. `docs/ARCHITECTURE.md` for compiler/runtime boundaries;
4. `docs/IMPLEMENTATION_PLAN.md` for phase order and exit criteria;
5. `docs/TEST_PLAN.md` for minimum acceptance coverage;
6. `docs/STATUS.md` for the active and next phase;
7. `prompts/*.md` for execution instructions only.

Prompts must not override or duplicate normative language semantics. If higher-authority documents conflict, or the active phase depends on an unresolved semantic choice, stop and report the exact conflict instead of inventing behavior.

## Source of Truth

Read these files before making language-design changes:

1. `docs/LANGUAGE_SPEC.md`
2. `docs/ARCHITECTURE.md`
3. `docs/IMPLEMENTATION_PLAN.md`
4. `docs/TEST_PLAN.md`
5. `docs/STATUS.md`

When a requested implementation conflicts with `docs/LANGUAGE_SPEC.md`, stop and report the conflict instead of silently inventing new semantics.

## Development Principles

- Keep each change buildable and testable.
- Prefer small vertical slices over broad unfinished scaffolding.
- Preserve GraalVM instrumentation and interoperability unless they fundamentally conflict with Solvik.
- Reuse Truffle specialization and primitive representations for performance.
- Do not box primitive values merely because Solvik exposes them as class types.
- Make compile-time diagnostics precise and source-located.
- Add positive and negative tests for every semantic feature.
- Never weaken static typing to make an implementation easier.
- Avoid runtime checks when the condition can be proven at compile time.
- Keep user-facing semantics independent from Java implementation details.
- Keep classes final by default.
- Keep class inheritance single only.
- Prefer composition/delegation for behavior reuse.
- Do not add implicit fallthrough to `switch`.
- Do not implement JavaScript-style ASI. Use the lexical semicolon insertion rules in the language spec.
- Raw strings must follow the Rust-style delimiter model specified in the language spec.
- Do not preserve SimpleLanguage syntax or dynamic-language behavior for compatibility.
- Use the Truffle AST backend as the only initial Solvik backend. Existing Bytecode DSL code may remain temporarily to keep staged work buildable, but do not port Solvik to it until the typed front end and AST backend are stable.

## Build

Use the repository wrappers. They select GraalVM from `/opt/graalvm` and the Maven wrapper:

```bash
./build.sh
./build-native.sh
```

Both wrappers run `./mvnw clean package` so validation cannot pass because of stale outputs. `./build.sh` is required for every phase. `./build-native.sh` is required in Phase 0, after changes to runtime/registration/launcher/native-image configuration, and in Phase 16. For focused Maven commands or parser generation, export `JAVA_HOME=/opt/graalvm` and prepend `$JAVA_HOME/bin` to `PATH`. Do not fall back to the host JDK.

## Working Style

Before modifying a subsystem:

1. read the active phase in `docs/STATUS.md`;
2. inspect the inherited implementation;
3. identify infrastructure to retain and SimpleLanguage behavior to replace or remove;
4. implement exactly one coherent phase;
5. add positive and negative tests;
6. run targeted tests and the required build wrapper(s);
7. update `docs/STATUS.md` with evidence;
8. do not begin a later phase in the same run.

Do not leave generated parser files manually edited if they are generated from a grammar. Modify the grammar/source generator inputs and regenerate them using the repository-supported mechanism.

`workflow.sh` may orchestrate multiple separate Pi runs. Each Pi run remains limited to one phase and receives a fresh model context. When a productive run leaves `NEXT` unchanged because the phase is incomplete, the workflow may continue that same phase in another fresh context. It must stop after the configured attempt limit, when a run makes no repository progress, or on a failed model run, failed validation, invalid phase transition, or blocked phase. After Phase 16 satisfies every exit criterion, set `NEXT` in `docs/STATUS.md` to `COMPLETE`.
