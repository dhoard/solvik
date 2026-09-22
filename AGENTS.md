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

## Confidence Goal

The confidence goal for any and all work is **100%**. Never report a task as complete unless you have full confidence that it is correct, tested, and validated. If confidence is below 100%, continue investigating, fixing, and re-validating until certainty is achieved.

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

After the Maven package, each wrapper verifies the built distribution the way an end user runs it:
`./build.sh` runs `./test-corpus.sh` against `standalone/target/solvik` (JVM launcher) and
`./build-native.sh` runs it against `standalone/target/solvik-native` (native binary). The corpus is
the checked-in `.sol` examples plus the regression corpus, compared against the golden `.output`
files, with rejection cases required to exit non-zero with empty stdout. This is in addition to the
in-process JUnit `.sol` suites, which exercise the embedded `Context.eval` API rather than the
shipped entry points. A distribution that stops running real programs correctly fails the build.
Set `SOLVIK_SKIP_CORPUS=1` to skip the corpus step (for example, a fast compile-only check).

### Final Quality Gate

`./build-all.sh` is the final quality gate. It runs `./build.sh && ./build-native.sh` and must pass before any work is considered complete. No change is done until `./build-all.sh` succeeds.

## Working Style

Before modifying a subsystem:

1. inspect the implementation;
2. identify infrastructure to retain and SimpleLanguage behavior to replace or remove;
3. implement one coherent change;
4. add positive and negative tests;
5. run targeted tests and the required build wrapper(s);
6. review the resulting diff and fix any problems found;
7. run `./build-all.sh` as the final quality gate — work is not complete until this passes.

The ANTLR parser is generated from `Solvik.g4` during the build by the `antlr4-maven-plugin`; edit only the grammar, never generated parser output.
