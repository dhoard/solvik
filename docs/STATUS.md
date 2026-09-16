# Solvik Implementation Status

This file is the phase handoff. Update it only after running the commands required by the active phase.

## Phase

- `NEXT`: Phase 0 — Reproducible Baseline and Migration Inventory
- Completed phases: none
- Last verified commit: none
- Last clean JVM build: `./build.sh` passed on 2026-09-16
- Last clean native build: `./build-native.sh` passed on 2026-09-16

An implementation run must execute only `NEXT`. It must not start the following phase.

The recorded builds ran from the documentation/tooling working tree: 438 language tests passed with one inherited skip, 15,180 TCK tests passed, and the native image was generated successfully. Phase 0 remains incomplete until its inventory and launcher smoke tests are recorded.

## Current Repository State

The repository is still the inherited SimpleLanguage implementation:

- the grammar accepts `function`, requires explicit semicolons, and discards newlines;
- the parser creates executable Truffle AST or Bytecode DSL nodes directly;
- language registration uses id `sl` and MIME type `application/x-sl`;
- the launcher, samples, tests, Java packages, and artifacts still use SimpleLanguage names;
- Solvik governing documents exist, but the Solvik compiler pipeline has not been implemented.

This state is migration input, not supported Solvik behavior.

## Required Phase 0 Evidence

Record before marking Phase 0 complete:

- `/opt/graalvm/bin/java -version`;
- `JAVA_HOME=/opt/graalvm ./mvnw -version`;
- `./build.sh`;
- `./build-native.sh`;
- inherited JVM and native launcher smoke tests;
- parser-generation command and ANTLR version;
- relevant module and subsystem inventory;
- exact failures, if any.

Do not mark Phase 0 complete unless the full baseline build and tests pass.

## SimpleLanguage Removal Inventory

| Inherited surface | Required disposition |
|---|---|
| Grammar and generated parser | Replace with Solvik grammar and parser during Phases 1–3; remove the old production parser path in Phase 5 |
| Direct parser-to-Truffle lowering | Replace with AST, semantic analysis, typed lowering, and Truffle AST lowering by Phase 5 |
| Bytecode DSL selection and parser | Do not port to Solvik; remove exposed selection and execution paths by Phase 5 |
| Dynamic typing and implicit local creation | Remove as Solvik static typing lands in Phase 4 |
| Dynamic object member insertion/removal | Remove from supported semantics in Phase 6 |
| `sl` language id, MIME type, launcher behavior, and launcher filenames | Replace with `solvik`, `application/x-solvik`, and Solvik-only launchers in Phase 5 |
| SimpleLanguage samples and semantic tests | Replace phase by phase; remove remaining examples and tests by Phase 16 |
| Java packages, Maven artifact names, scripts, and filenames | Rename incrementally; finish by Phase 16 |
| Copyright and historical attribution | Preserve |

Do not add a compatibility mode to ease this removal.

## Phase Completion Template

When a phase passes:

1. list files changed;
2. list semantics and architecture implemented;
3. list positive and negative tests;
4. record targeted-test and required build-wrapper results;
5. list remaining transitional SimpleLanguage code;
6. set `NEXT` to the immediately following phase;
7. stop.
