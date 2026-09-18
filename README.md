<p align="center">
  <img src="https://img.shields.io/badge/license-Apache%202.0%20%2F%20UPL%201.0-blue.svg" alt="License Apache 2.0 / UPL 1.0"/>
  <img src="https://img.shields.io/badge/version-1.0.0--SNAPSHOT-blue.svg" alt="Version 1.0.0-SNAPSHOT"/>
  <img src="https://img.shields.io/badge/runtime-GraalVM%20Truffle-000000.svg" alt="GraalVM Truffle"/>
</p>

<h1 align="center">solvik</h1>

<p align="center">
  <em>A strongly and statically typed object-oriented language implemented on GraalVM/Truffle.</em>
</p>

---

## Overview

Solvik is a strongly and statically typed, nominally typed, object-oriented language for
GraalVM/Truffle. It has familiar TypeScript/Kotlin-like syntax, explicit mutability, final-by-default
classes, single inheritance with controlled `open`/`override`, multiple interfaces with default
methods, composition through delegation, null safety with flow narrowing, nominal generics,
value-carrying enums, sealed types with exhaustive `match`, a non-fallthrough `switch`, first-class
`Regex`, Rust-style raw strings, universal `toString` display with `..` concatenation, range
`for`-in loops, and Go-style semicolon insertion.

This repository is an in-place conversion of
[GraalVM SimpleLanguage](https://github.com/graalvm/simplelanguage). The inherited project supplies
proven Truffle integration, execution specialization, interop, tooling hooks, and build structure;
the SimpleLanguage grammar, dynamic semantics, object model, public identity, and tests are
replaced. There is no SimpleLanguage source compatibility mode: only the `solvik` language id and
MIME type are registered.

Solvik source files use the `.sol` extension. The language id is `solvik` and the MIME type is
`application/x-solvik`. New front-end code lives under the `org.solvik` packages and compiles
through an explicit pipeline:

```text
lexer / semicolon insertion
  -> parser
  -> syntax AST
  -> include / module resolution
  -> symbol collection, name and type resolution
  -> static type checking and flow analysis
  -> semantic validation
  -> typed / lowered representation
  -> Truffle AST execution
```

A program with compile-time errors never produces an executable call target.

The authoritative documents are:

- [`docs/LANGUAGE_SPEC.md`](docs/LANGUAGE_SPEC.md) — normative syntax and semantics;
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — compiler/runtime boundaries.

[`AGENTS.md`](AGENTS.md) records the repository-wide implementation constraints.

### What Solvik Looks Like

```solvik
interface Greeter {
    func greet(): String

    func greeting(): String {
        return "Hello, " .. greet()
    }
}

class Named implements Greeter {
    val name: String

    Named(name: String) {
        this.name = name
    }

    func greet(): String {
        return this.name
    }
}

class Service implements Greeter {
    delegate val greeter: Greeter

    Service(greeter: Greeter) {
        this.greeter = greeter
    }
}

func describe(greeter: Greeter?): String {
    if (greeter == null) {
        return "nobody"
    }
    return greeter.greeting()
}

println(describe(Service(Named("Solvik"))))
println(describe(null))
```

The executable top-level statements form the body of one implicit `main`, so a program needs no
boilerplate entry point.

### Run it

Build first (see [Building](#building)), then run a source file with the JVM launcher:

```sh
./standalone/target/solvik language/tests/Objects.sol
```

```text
Hello, Solvik
nobody
```

The native launcher runs the same program from a standalone binary:

```sh
./standalone/target/solvik-native language/tests/Objects.sol
```

A program can also be read from standard input by omitting the file argument.

### Multi-file programs

A top-level, compile-time `include` directive splices another `.sol` file into the same statically
checked program. Relative paths resolve against the including file, so a file-backed root gathers
its siblings; a root read from standard input resolves relative includes against the working
directory. Included declarations share the program's declaration scope, included top-level
statements join the one implicit `main`, and each canonical file is included once per compilation.

```solvik
// IncludeMain.sol
include "IncludeLibrary.sol"
println(greet("Solvik"))
```

```solvik
// IncludeLibrary.sol
func greet(name: String): String {
    return "Hello, " .. name .. "!"
}
```

A file may instead name its namespace with a `module` declaration and be referenced through a
file-local prefix, which prevents name collisions between included files:

```solvik
// ModulesMain.sol
module app_main

include "ModulesLib.sol" alias math

println(math::double(21))
```

```solvik
// ModulesLib.sol
module math_utils

func double(value: Int): Int {
    return value * 2
}
```

Module names use lowercase letters, digits, and single underscores (`com_example_util`), and the
`::` namespace separator distinguishes a qualified reference from `.` member access. An
`include "file.sol" alias prefix` binding is file-local and non-transitive. A file without a
`module` declaration keeps the flat default-module behavior. See
[`docs/LANGUAGE_SPEC.md`](docs/LANGUAGE_SPEC.md) section 20 for the full rules.

### Creating a standalone executable

`./build-native.sh` runs the native profile and produces `standalone/target/solvik-native` with
GraalVM `native-image`:

```sh
./build-native.sh
./standalone/target/solvik-native language/tests/Hello.sol
```

The native launcher embeds the language runtime and needs no JVM at run time. The JVM launcher
(`standalone/target/solvik`) wraps the module jars in `standalone/target/modules/`.

## Building

Building requires GraalVM for JDK 25, including `native-image`, plus network access the first time
Maven resolves dependencies. The wrappers locate GraalVM through `GRAALVM_HOME`, then a GraalVM
`JAVA_HOME`, then `/opt/graalvm`, and reject a non-GraalVM JDK so the host JDK is never used by
accident.

Use the repository wrappers. Both select GraalVM for JDK 25 and run `./mvnw clean package`, so
validation never passes because of stale outputs:

```sh
./build.sh          # JVM distribution and tests
./build-native.sh   # adds the native-image distribution
```

The native profile also works directly: `./mvnw package -Pnative` once `JAVA_HOME` points at
GraalVM for JDK 25. The JVM launcher is produced at `standalone/target/solvik` and the native
launcher at `standalone/target/solvik-native`.

The ANTLR parser is generated from
`language/src/main/java/org/solvik/parser/grammar/Solvik.g4` during the build by the
`antlr4-maven-plugin` into `target/generated-sources/antlr4`. Generated sources are never edited and
are not checked in; edit only the grammar.

## Testing

The test suite runs during `./build.sh` (Maven Surefire) and covers parsing, include/module
resolution, static typing, semantic validation, and end-to-end execution. It uses JUnit 6 and
AssertJ:

```sh
./build.sh    # clean build plus the complete test suite
```

The checked-in examples in `language/tests/` are the published syntax surface. Every `*.sol` file
has a matching `*.output` golden file and is executed end to end by `SolvikExamplesTest`, so a
behavior change must be reflected in both the example and its golden. Add positive and negative
tests for every semantic feature; see [`AGENTS.md`](AGENTS.md) and
[`CONTRIBUTING.md`](CONTRIBUTING.md).

## Repository layout

```text
pom.xml                     Maven aggregator (language, launcher, standalone)
language/                   Solvik language module (org.solvik)
  src/main/java/org/solvik/
    parser/                 lexer support, semicolon insertion, ANTLR parser, include/module resolution
      grammar/Solvik.g4     ANTLR grammar (the generated parser is never checked in)
    ast/                    syntax AST (declaration, expression, statement, pattern)
    type/                   compiler type model and built-in type metadata
    semantic/               symbols, name resolution, static type checking, semantic validation
    lowering/               typed/lowered representation feeding the Truffle AST
    diagnostic/             stable SOLV-... codes and source-located diagnostics
    regex/                  portable regex pattern dialect
    source/                 source files, spans, source catalog, string escapes
    truffle/                TruffleLanguage integration, executable nodes, runtime objects
  src/test/java/org/solvik/test/   JUnit 6 + AssertJ tests
  tests/                    checked-in .sol examples with .output goldens
launcher/                   command-line launcher (org.solvik.launcher)
standalone/                 JVM distribution, launcher template, native-image profile
docs/                       LANGUAGE_SPEC.md and ARCHITECTURE.md
AGENTS.md                   repository-wide implementation constraints
NOTICE.md                   attribution and the complete UPL 1.0 text
```

## Language highlights

- Strong, nominal static typing with no implicit `any`: two unrelated classes with identical
  members are not assignment-compatible, and `Any` still requires a checked cast or refinement.
- Explicit mutability: `val` for immutable bindings and properties, `var` for mutable ones.
- Classes are final by default; inheritance is single and opt-in with `open`/`extends`/`override`.
- Multiple interfaces with default methods, plus composition through `delegate` forwarding.
- Null safety: non-null types by default, `T?`, safe access `?.`, coalescing `??`, and flow
  narrowing through `!= null` and `is`; checked `as` casts and `is` type tests.
- Nominal generics with static checking and erasure, including built-in mutable `List<T>`,
  `Set<T>`, `Stack<T>`, and `Map<K, V>`.
- Value-carrying enums, `sealed` types, and exhaustive expression-oriented `match` over enum
  variants, sealed subtypes, and wildcards.
- Non-fallthrough `switch` with compile-time constant labels, `regex` pattern cases, and an optional
  trailing `default`.
- First-class `Regex` with a portable pattern dialect and immutable `RegexMatch` capture groups.
- Rust-style raw strings (`r"..."`, `r#"..."#`, `r##"..."##`, ...) whose opening delimiter fixes the
  exact closing delimiter.
- Go-style lexical semicolon insertion and TypeScript/Kotlin-style leading-dot member chains.
- Range `for`-in loops: inclusive `...`, ascending-exclusive `..<`, and descending-exclusive `..>`.
- Scope blocks that introduce a lexical scope for explicit local lifetime management.
- Root type hierarchy `Any`/`Object`/`Number` with `Byte`, `Short`, `Int`, `Long`, `Float`,
  `Double`, `Boolean`, `Char`, `String`, and `Unit`. Integral arithmetic is checked; conversions are
  explicit; no implicit numeric widening or narrowing.
- Universal `toString` display and `..` concatenation, with user classes able to override
  `toString`.
- Implicit `main` from top-level statements; predeclared `print`, `println`, and `exit`.
- Compile-time `include` and `module`/`alias` namespaces using the `::` separator.
- A Truffle AST backend with specialization, interop, instrumentation, and native-image support.
  The compiler front end stays backend-independent.

## Diagnostics and exit codes

Compile-time diagnostics are source-located and carry stable, machine-readable codes named
`SOLV-<layer>-<number>` by compiler stage:

| Family | Stage |
| ------ | ----- |
| `SOLV-LEX-nnn` | lexer and raw-string scanning |
| `SOLV-PARS-nnn` | parser |
| `SOLV-RESOL-nnn` | name, member, include, and module resolution |
| `SOLV-TYPE-nnn` | static type checking and flow analysis |
| `SOLV-SEM-nnn` | semantic validation |

Warnings are reported but do not prevent compilation or affect the exit code. The launcher returns:

| Exit | Meaning |
| ---- | ------- |
| 0    | success |
| 1    | compile-time error, runtime error, or uncaught Solvik exception |
| N    | status passed to the predeclared `exit(code: Int)` function |

## Contributing

Read [`AGENTS.md`](AGENTS.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md) before changing language
behavior. All contributions require a sign-off under the
[Developer Certificate of Origin](DCO.md) (DCO).

## License

New Solvik source is licensed under the Apache License, Version 2.0; see
[`LICENSE.md`](LICENSE.md). Files adapted from GraalVM SimpleLanguage remain under the upstream
Universal Permissive License, Version 1.0, and carry the required Oracle attribution. The full UPL
text and attribution are in [`NOTICE.md`](NOTICE.md). See also [`CONTRIBUTING.md`](CONTRIBUTING.md) for
the notice conventions used when adding files.
