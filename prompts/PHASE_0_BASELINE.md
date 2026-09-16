# Phase 0 — Reproducible Baseline and Migration Inventory

Execute only when `docs/STATUS.md` marks Phase 0 as `NEXT`.

## Scope

Inspect and record:

- Maven modules, Java/GraalVM/Truffle/ANTLR versions, and build commands;
- language registration, file detection, launcher, and standalone packaging;
- grammar and generated-parser workflow;
- direct Truffle AST and Bytecode DSL parser paths;
- runtime context, functions, objects, Shapes, built-ins, interop, instrumentation, and tests;
- every exposed SimpleLanguage surface listed in `docs/STATUS.md`.

Run:

1. `./build.sh`
2. `./build-native.sh`
3. `JAVA_HOME=/opt/graalvm /opt/graalvm/bin/java -version`
4. `JAVA_HOME=/opt/graalvm ./mvnw -version`
5. one inherited JVM-launcher smoke test using an existing fixture
6. one inherited native-launcher smoke test using an existing fixture

Use this `JAVA_HOME` for parser generation as well. Do not use the host JDK.

Create `docs/BASELINE.md` with concise command results, subsystem locations, retained infrastructure, replacement targets, and the parser-generation command. Do not paste full build logs.

## Boundaries

- Do not change guest-language syntax or semantics.
- Do not start the Solvik AST or grammar.
- Do not add compatibility behavior.
- Do not rename Java packages or Maven artifacts in this phase.
- Fix code only when necessary to make the inherited baseline reproducible; document every such fix.
- Treat successful SimpleLanguage execution only as migration evidence, never as a compatibility requirement.

## Exit Criteria

- JVM and native builds and inherited tests pass;
- both launcher smoke tests pass;
- exact tool versions and commands are recorded;
- `docs/BASELINE.md` inventories the relevant implementation;
- `docs/STATUS.md` records the evidence and sets Phase 1 as `NEXT`;
- no Phase 1 work has begun.
