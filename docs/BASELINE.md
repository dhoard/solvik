# Phase 0 Baseline — Reproducible Build and Migration Inventory

Recorded on 2026-09-16 from commit `f3c2589` (`chore: Initial Solvik files`).
All commands were run from the repository root. No code changes were required to make
the baseline reproducible.

## Toolchain

| Item | Value |
|---|---|
| GraalVM | Oracle GraalVM 25.3.4.1+1.1 (`/opt/graalvm`) |
| Java | `java version "25.0.4.1" 2026-08-18 LTS`, JVMCI 25.3-b22 |
| Maven wrapper | Apache Maven 3.9.16 (`./mvnw`), JDK runtime `/opt/graalvm-25.3.4.1+1.1` |
| Host OS | Linux x86_64 (`7.0.0-31-generic`) |
| ANTLR tool | ANTLR Parser Generator Version 4.13.2 (`antlr-4.13.2-complete.jar`, checked into repo root) |
| ANTLR runtime dependency | `org.antlr:antlr4-runtime:4.13.2` (`antlr4-runtime.version` in root `pom.xml`) |
| Truffle/GraalVM dependency | `graalvm.version` = `25.3.4.1` (root `pom.xml`) |
| Java source/target | 25 (`maven.compiler.source/target` in root `pom.xml`) |
| Parent POM | `com.oracle:simplelanguage-parent:1.0.0-SNAPSHOT` |

Version commands:

```text
$ JAVA_HOME=/opt/graalvm /opt/graalvm/bin/java -version
java version "25.0.4.1" 2026-08-18 LTS
Java(TM) SE Runtime Environment Oracle GraalVM 25.3.4.1+1.1 (build 25.0.4.1+1-LTS-jvmci-25.3-b22)
Java HotSpot(TM) 64-Bit Server VM Oracle GraalVM 25.3.4.1+1.1 (build 25.0.4.1+1-LTS-jvmci-25.3-b22, mixed mode, sharing)

$ JAVA_HOME=/opt/graalvm ./mvnw -version
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: ~/.m2/wrapper/dists/apache-maven-3.9.16-bin/4200fbc6/apache-maven-3.9.16
Java version: 25.0.4.1, vendor: Oracle Corporation, runtime: /opt/graalvm-25.3.4.1+1.1
```

## Build Commands and Results

| Command | Result |
|---|---|
| `./build.sh` (= `JAVA_HOME=/opt/graalvm ./mvnw clean package`) | BUILD SUCCESS; language tests: 438 run, 0 failures, 0 errors, 1 skipped (`SLSeparatedClassLoadersTest`, inherited environment-dependent skip); TCK: 15,180 run, 0 failures, 0 errors, 0 skipped |
| `./build-native.sh` (= `JAVA_HOME=/opt/graalvm ./mvnw clean package -Pnative`) | BUILD SUCCESS in 01:05 min; same test totals (438 language tests, 1 inherited skip; 15,180 TCK tests, 0 skipped); `native-image` completed and produced `standalone/target/slnative` |
| `JAVA_HOME=/opt/graalvm /opt/graalvm/bin/native-image --version` | `native-image 25.0.4.1 2026-08-18`, Substrate VM 25.3.4.1+1.1 |

Reactor modules (root `pom.xml`): `simplelanguage-parent` (root), `simplelanguage`
(`language/`), `sl-tck` (`tck/`), `launcher` (`launcher/`), `standalone` (`standalone/`).

Build wrappers hard-code `JAVA_HOME=/opt/graalvm` and prepend `$JAVA_HOME/bin` to `PATH`;
no host JDK is used. The `-Pnative` profile adds `native-maven-plugin` configuration
(module `org.graalvm.sl.launcher/org.graalvm.sl.launcher` per root `pom.xml`
`<launcherClass>`).

## Language Registration and Detection

- `language/src/main/java/com/oracle/truffle/sl/SLLanguage.java`:
  `@TruffleLanguage.Registration(id = SLLanguage.ID, name = "SL", defaultMimeType = SLLanguage.MIME_TYPE, characterMimeTypes = SLLanguage.MIME_TYPE, contextPolicy = SHARED, fileTypeDetectors = SLFileDetector.class)`;
  `ID = "sl"`, `MIME_TYPE = "application/x-sl"` (constant at ~line 233).
- Options include `UseBytecode` (default `false`) and `ForceBytecodeTier` (~lines 248–276),
  selecting the interpreter backend per context.
- `SLFileDetector.java`: maps files ending in `.sl` to `application/x-sl`.
- Native-image resource config: `language/src/main/java/META-INF/native-image/org.graalvm.truffle/truffle-sl/native-image.properties`.

## Launcher and Standalone Packaging

- `launcher/src/main/java/com/oracle/truffle/sl/launcher/SLMain.java` plus `module-info.java`;
  main class `org.graalvm.sl.launcher/com.oracle.truffle.sl.launcher.SLMain`.
- `standalone/sl`: bash launcher script for the JVM distribution; copied by the `standalone`
  module to `standalone/target/sl` alongside module jars in `standalone/target/modules/`.
  Supports flags such as `-debug`, `-dump`, `-disassemble`, `-J*`.

## Grammar and Generated Parser

- Grammar: `language/src/main/java/com/oracle/truffle/sl/parser/SimpleLanguage.g4`
  (ANTLR 4 grammar; accepts `function`, requires explicit semicolons).
- Generation command (`generate_parser.sh`, with `JAVA_HOME=/opt/graalvm`):

  ```bash
  $JAVA_HOME/bin/java -cp antlr-4.13.2-complete.jar \
    org.antlr.v4.Tool -package com.oracle.truffle.sl.parser -no-listener -visitor \
    language/src/main/java/com/oracle/truffle/sl/parser/SimpleLanguage.g4
  ```

- Generated artifacts checked in beside the grammar: `SimpleLanguageLexer.java`,
  `SimpleLanguageParser.java`, `SimpleLanguageVisitor.java`, `SimpleLanguageBaseVisitor.java`,
  plus `.interp`/`.tokens` files. Parser generation is manual (`generate_parser.sh` run by
  hand), not a Maven phase — `language/pom.xml` depends only on `antlr4-runtime`; the ANTLR
  tool jar is vendored at the repo root.
- Reproducibility check (2026-09-16): regenerating into a scratch directory with the recorded
  command and ANTLR 4.13.2 succeeded, but the regenerated sources differ from the checked-in
  ones in three ways: (1) checked-in files add a UPL license header wrapped in
  `//@formatter:off` / `// Checkstyle: stop` pragmas; (2) `@SuppressWarnings` annotations
  were narrowed (e.g. `{"all", "this-escape"}` vs the generator's longer list); (3)
  checked-in rule-context field assignments use the legacy `_localctx.field` form while
  ANTLR 4.13.2 emits `((XxxContext)_localctx).field` casts, i.e. the checked-in generated
  sources are stale relative to the current grammar plus tool version. This does not break
  the baseline build (the checked-in sources compile and all tests pass), but Phase 1 must
  regenerate from the grammar and reapply the header/warning postprocessing, and Phase 0
  made no changes to these files.

## Parser-to-Executable Paths (to replace)

`SLLanguage.parsePE` (~lines 419–422) selects between two direct ANTLR-to-executable paths:

- `SLNodeParser` → executable Truffle AST nodes (default path);
- `SLBytecodeParser` → Bytecode DSL nodes (`SLBytecodeRootNode`, gated by `UseBytecode`).

Both bypass any separate language AST, symbol resolution, or static type analysis.

## Subsystem Inventory (language/)

| Area | Location | Notes |
|---|---|---|
| Core | `com/oracle/truffle/sl/` | `SLLanguage`, `SLFileDetector`, `SLEvaluateLocalNode`, `SLException` |
| Parser | `.../sl/parser/` | ANTLR grammar + generated lexer/parser/visitors; `SLBaseParser`, `SLNodeParser`, `SLBytecodeParser`, `SLParseError` |
| AST nodes | `.../sl/nodes/` | Root nodes (`SLAstRootNode`, `SLEvalRootNode`, `SLRootNode`, `SLBuiltinAstNode`, `SLUndefinedFunctionRootNode`), `SLBinaryNode`, `SLExpressionNode`, `SLStatementNode`, `SLTypes` |
| Control flow | `.../sl/nodes/controlflow/` | `SLIfNode`, `SLWhileNode`/`SLWhileRepeatingNode`, `SLBlockNode`, `SLBreak/Continue/Return` nodes + exceptions, `SLDebuggerNode`, `SLFunctionBodyNode` |
| Expressions | `.../sl/nodes/expression/` | arithmetic/comparison/logical nodes, literals (`SLLongLiteralNode`, `SLBigIntegerLiteralNode`, `SLStringLiteralNode`), `SLInvokeNode`, `SLFunctionLiteralNode`, property read/write |
| Locals | `.../sl/nodes/local/` | `SLScopedNode`, read/write local variable nodes, `SLReadArgumentNode` |
| Interop helpers | `.../sl/nodes/interop/`, `.../sl/nodes/util/` | `NodeObjectDescriptor(+Keys)` (object-model descriptors), `SLToBooleanNode`, `SLToMemberNode`, `SLToTruffleStringNode`, `SLUnboxNode` |
| Runtime | `.../sl/runtime/` | `SLContext`, `SLLanguageView`, `SLFunction`, `SLFunctionRegistry`, `SLObject` (Truffle `DynamicObject` + `@ExportLibrary(InteropLibrary.class)` — Shapes-based), `FunctionsObject`, `SLNull`, `SLType`, `SLBigInteger`, `SLStrings` |
| Builtins | `.../sl/builtins/` | 22 builtins incl. `println`, `readln`, `nanoTime`, `defineFunction`, `eval`, `importModule`, `javaType`, `newObject`, `exit`, `stackTrace`, `typeof`, interop/wrapping helpers |
| Bytecode DSL | `.../sl/bytecode/` | `SLBytecodeRootNode`, `SLBytecodeScopeExports`, `SLBytecodeSerialization` (trivial; do not port) |

## Tests

- Guest fixtures: 37 positive `language/tests/*.sl` programs with matching `.output` golden
  files (e.g. `Fibonacci.sl`, `Add.sl`, `Object.sl`) plus 19 negative fixtures under
  `language/tests/error/` (`InvalidAssignment*.sl` etc.) run by `SLParseErrorTest`. One stale
  orphan golden, `language/tests/HelloWorld.output`, has no matching `.sl` source.
- JUnit tests: `language/src/test/java/com/oracle/truffle/sl/test/` — suites
  `SLTestSuiteAST`, `SLTestSuiteBytecode{Cached,Uncached}`, TCK wiring, interop, debug,
  instrumentation, code-sharing, parse-error, Java-interop, and classloader-isolation tests.
- TCK module `tck/`: `SLTCKLanguageProvider` + resources (`Fibonacci.sl`, `Ackermann.sl`,
  invalid-syntax fixtures) registering the `sl` provider via
  `META-INF/services/org.graalvm.polyglot.tck.LanguageProvider`.

## Exposed SimpleLanguage Surfaces

- Language id `sl`, name `SL`, MIME type `application/x-sl`, `.sl` file extension.
- Launcher scripts/artifacts named `sl`; Maven artifacts `simplelanguage-parent`,
  `simplelanguage`, `sl-tck`, `launcher`, `standalone`; Java packages `com.oracle.truffle.sl.*`;
  module names `org.graalvm.sl.*`.
- All `language/tests/*.sl` fixtures and `SL*` test names use SimpleLanguage syntax.

## Retained vs Replaced Infrastructure

Retain (with renaming/adaptation in later phases): Truffle language registration and
context-policy plumbing, `TruffleFile` detection mechanism, native-image/build configuration
scaffolding, standalone packaging mechanism, launcher mechanism, Truffle DSL specialization
patterns, `DynamicObject`/Shapes usage for objects, interop library exports, instrumentation
and TCK harnesses, testing infrastructure patterns, UPL licensing headers.

Replace: `SimpleLanguage.g4` and its generated parser (Phases 1–3), direct
ANTLR→Truffle/Bytecode lowering (`SLLanguage.parsePE` paths) with language AST → name
resolution → static type analysis → semantic validation → typed lowering → Truffle AST
(Phases 4–5), dynamic typing and implicit locals (Phase 4), dynamic object member semantics
(Phase 6), `sl`/`SL` identifiers and launchers (Phase 5 for user-visible, Phase 16 final),
sample programs and tests progressively.

Remove without porting: Bytecode DSL parser/root-node selection and exposed `UseBytecode`
execution path (by Phase 5).

## Smoke Tests

### JVM launcher smoke test

```bash
JAVA_HOME=/opt/graalvm ./standalone/target/sl language/tests/Fibonacci.sl
```

Exit code 0. Output matches `language/tests/Fibonacci.output` (lines `1: 1` … `10: 55`)
after removing the launcher banner line
(`== running on Engine[id=..., Oracle GraalVM, version=25.3.4.1, ...]`), which is standard
launcher output, not program output. JVM warnings about `System::load` native access appear
on stderr and are non-fatal GraalVM/JDK 25 advisories.

### Native launcher smoke test

The `-Pnative` build (`standalone/pom.xml`, `make_native` execution invoking
`${JAVA_HOME}/bin/native-image -o ${project.build.directory}/slnative`) produces the native
launcher at `standalone/target/slnative`.

```bash
JAVA_HOME=/opt/graalvm ./standalone/target/slnative language/tests/Fibonacci.sl
```

Exit code 0, empty stderr. Output matches `language/tests/Fibonacci.output` exactly after
removing the same launcher banner line. Both smoke tests verified byte-identical program
output against the golden file.
