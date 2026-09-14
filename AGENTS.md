# Repository Guidelines

## Project Structure & Module Organization

This repository is a single-module Maven project implementing the Solvik
language as a Java 17 transpiler. Production sources live under
`src/main/java/org/solvik/transpiler/`: the frontend (`Lexer.java`,
`Parser.java`, `Ast.java`), `SemanticAnalyzer.java`, the typed Solvik IR
(`SolvikProgram.java`, `SolvikStmt.java`, `SolvikIr.java`) and its
`SolvikLowerer.java` / `IrOptimizer.java`, the backend package
`src/main/java/org/solvik/transpiler/backend/` (`JavaProgram.java`,
`JavaLowerer.java`, `JavaIr.java`, `JavaIrOptimizer.java`, `JavaEmitter.java`,
`JavaRuntime.java`, `RuntimeFeature.java`), the backend-neutral language model
`language/Language.java`, the reusable `Transpiler.java` service, and the
`SolvikTranspiler.java` CLI. JUnit 5 tests live under
`src/test/java/org/solvik/transpiler/` (`FrontendTests.java`,
`CompilerPhaseTests.java`, `ConformanceTest.java`). Language conformance
fixtures are individual directories under `test/cases/`, with files such as
`main.sol`, `expected.out`, and `expected.code`. Manual Java benchmarks are in
`benchmarks/`; design and reference material is in the root Markdown files and
`docs/`; editor support is in `sublime/`.

## Compiler Architecture

The compiler is an explicit pipeline with one responsibility per phase:

```text
.sol -> Lexer -> Parser -> AST -> SemanticAnalyzer -> SolvikLowerer
     -> SolvikProgram/SolvikStmt/SolvikIr -> IrOptimizer -> JavaProgram
     -> JavaLowerer -> JavaIr -> JavaIrOptimizer -> JavaRuntime -> JavaEmitter
     -> .java
```

Dependencies run one way: `frontend -> semantic -> Solvik IR -> Java backend`.
`SemanticAnalyzer` is the only authority for Solvik meaning, `SolvikLowerer` is
the only phase that reads the parser AST, and `JavaLowerer` owns every Java
representation decision. Backend phases consume typed Solvik IR and must not
walk the AST or re-derive semantic decisions. Runtime reachability is tracked
structurally through `RuntimeFeature`, never by scanning rendered Java. Keep
this separation when adding features: prefer extending the typed IR and the
lowerer over adding logic to `JavaEmitter`, which should stay a deterministic
renderer.

## Build, Test, and Development Commands

- A passing `./build.sh` is the final quality gate: it runs
  `./mvnw -B clean verify`, which compiles the sources, runs all unit and
  conformance tests, and produces `target/solvik.jar`.
- `./mvnw test` runs the JUnit 5 unit and conformance suites.
- `./mvnw package` builds the executable transpiler JAR.
- `./mvnw clean verify` is the direct full quality gate.
- `./transpile.sh example.sol ExampleProgram` runs the built transpiler from
  any working directory; it fails with a clear message until `./build.sh` has
  produced the JAR.
- `./solvik.sh example.sol [args...]` transpiles and runs a program in one
  step, forwarding arguments to the Solvik program.

The Maven Wrapper (`./mvnw`) is the project interface; a global Maven
installation is not required.

## Coding Style & Naming Conventions

Java 17 is the language level. Production and test sources compile with
`-Xlint:all -Werror`, so the build must be warning-free. Follow idiomatic Java
naming: `snake_case` does not apply here — use `camelCase` for methods and
fields, `UpperCamelCase` for types, and `UPPER_SNAKE_CASE` for constants.
Solvik examples use four-space indentation, lowercase dotted package names, and
`UpperCamelCase` type names. Keep generated Java deterministic and package-free.

## Testing Guidelines

Name JUnit tests descriptively in `camelCase` and place unit tests beside the
implementation. New language behavior should include a conformance fixture.
Golden outputs must be deterministic; fixture comparison checks successful
programs' `expected.out` exactly and all programs' exit codes. Run the
narrowest relevant test while iterating, then finish with a passing
`./build.sh`.

## Commit & Pull Request Guidelines

Recent commits use short categorized subjects such as `fix: ...` and
`chore: ...`. Use the same concise convention and describe the user-visible
behavior when applicable. Pull requests should summarize the change, identify
any compatibility or diagnostic impact, link related issues, list validation
commands (especially `./build.sh`), and update relevant language or transpiler
documentation. Screenshots are unnecessary unless documentation presentation
is affected.
