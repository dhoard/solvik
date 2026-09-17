# Solvik

Solvik is a strongly and statically typed object-oriented language implemented on GraalVM/Truffle.
It has familiar TypeScript/Kotlin-like syntax, explicit mutability, final-by-default classes,
single inheritance with controlled `open`/`override`, multiple interfaces with default methods,
delegation, null safety with flow narrowing, nominal generics, value-carrying enums, sealed types
with exhaustive `match`, first-class regular expressions, Rust-style raw strings, universal
`toString` display, `..` string concatenation, range `for`-in loops, and Go-style semicolon
insertion.

Solvik source files use the `.sol` extension. The language id is `solvik` and the MIME type is
`application/x-solvik`. There is no SimpleLanguage compatibility mode.

## Documentation

The authoritative documents are:

- `docs/LANGUAGE_SPEC.md` — normative syntax and semantics;
- `docs/ARCHITECTURE.md` — compiler/runtime boundaries.

`AGENTS.md` records the repository-wide implementation constraints.

## Prerequisites

Building requires GraalVM for JDK 25, including `native-image`, plus network access the first time
Maven resolves dependencies. The wrappers locate GraalVM through `GRAALVM_HOME`, then a GraalVM
`JAVA_HOME`, then `/opt/graalvm`.

## Build

Use the repository wrappers. They select GraalVM for JDK 25, prepend it to `PATH`, and run
`./mvnw clean package`, so validation never passes because of stale outputs:

```bash
./build.sh          # JVM distribution and tests
./build-native.sh   # adds the native-image distribution
```

The JVM launcher is produced at `standalone/target/solvik` and the native launcher at
`standalone/target/solvik-native`. Both run a `.sol` source file; run them with GraalVM for JDK 25
on `PATH` (or `JAVA_HOME` set to it):

```bash
./standalone/target/solvik language/tests/Hello.sol
./standalone/target/solvik-native language/tests/Hello.sol
```

A program may be split across files with the top-level, compile-time `include` directive. Relative
include paths are resolved against the including file, so a file-backed root gathers its siblings;
a root read from standard input resolves relative includes against the working directory.

```solvik
// main.sol
include "library.sol"
println(greet("Solvik"))
```

```solvik
// library.sol
func greet(name: String): String {
    return "Hello, " .. name .. "!"
}
```

Included declarations share the program's declaration scope, included top-level statements join the
one implicit `main`, and a canonical file is included once per compilation. A file may instead
name its namespace with a `module` declaration and be referenced through a prefix, which prevents
name collisions between included files:

```solvik
// main.sol
module app_main
include "library.sol" alias lib
println(lib::greet("Solvik"))
```

```solvik
// library.sol
module greet_lib
func greet(name: String): String {
    return "Hello, " .. name .. "!"
}
```

Module names use lowercase letters, digits, and single underscores (`com_example_util`), and an
`include "file.sol" alias prefix` binding is file-local and non-transitive; a qualified reference
uses the `::` namespace separator, for example `lib::greet("Solvik")` and `lib::Type`. A file
without a `module` declaration keeps the flat default-module behavior. See `docs/LANGUAGE_SPEC.md`
section 20 for the full rules.

To regenerate the ANTLR parser, edit
`language/src/main/java/org/solvik/parser/grammar/Solvik.g4` and build; the `antlr4-maven-plugin`
generates the parser into `target/generated-sources/antlr4` during `./mvnw package`. Generated
sources are never edited and are not checked in.

## License

New Solvik source is licensed under the Apache License, Version 2.0; see `LICENSE-APACHE`.
Files adapted from GraalVM SimpleLanguage remain under the upstream Universal Permissive License,
Version 1.0; see `LICENSE.md`, and carry the required Oracle attribution. The repository is derived
in place from GraalVM SimpleLanguage so that proven Truffle infrastructure could be reused; no
SimpleLanguage syntax or semantics are exposed. See `NOTICE` for attribution and the license split.
