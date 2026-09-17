# Solvik Implementation Instructions

This repository is an in-place conversion of `graalvm/simplelanguage` into **Solvik**, a strongly and statically typed object-oriented language implemented on GraalVM/Truffle.

## Primary Rule

Do not treat this as a greenfield VM. Preserve useful GraalVM/Truffle infrastructure while replacing SimpleLanguage syntax, semantics, public identity, and tests.

SimpleLanguage source compatibility must be removed. Do not add a compatibility flag, dual parser, legacy language mode, or permanent SimpleLanguage execution path. Remaining upstream names are allowed only in copyright notices and historical attribution.

The compilation pipeline is:

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
3. `docs/ARCHITECTURE.md` for compiler/runtime boundaries.

If higher-authority documents conflict, or a requested change depends on an unresolved semantic choice, stop and report the exact conflict instead of inventing behavior.

## Source of Truth

Read these files before making language-design changes:

1. `docs/LANGUAGE_SPEC.md`
2. `docs/ARCHITECTURE.md`

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
- Use the Truffle AST backend as the Solvik backend. Do not introduce a second execution backend.

## Build

Use the repository wrappers. They select GraalVM for JDK 25 (from `GRAALVM_HOME`, a GraalVM `JAVA_HOME`, or `/opt/graalvm`) and the Maven wrapper:

```bash
./build.sh
./build-native.sh
```

Both wrappers run `./mvnw clean package` so validation cannot pass because of stale outputs. `./build-native.sh` is required after changes to runtime, registration, launcher, or native-image configuration. For focused Maven commands, set `JAVA_HOME` to GraalVM for JDK 25 and prepend `$JAVA_HOME/bin` to `PATH`. Do not fall back to the host JDK.

## Working Style

Before modifying a subsystem:

1. inspect the implementation;
2. identify infrastructure to retain and SimpleLanguage behavior to replace or remove;
3. implement one coherent change;
4. add positive and negative tests;
5. run targeted tests and the required build wrapper(s);
6. review the resulting diff and fix any problems found.

The ANTLR parser is generated from `Solvik.g4` during the build by the `antlr4-maven-plugin`; edit only the grammar, never generated parser output.
