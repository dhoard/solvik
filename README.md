# Solvik

Solvik is a strongly and statically typed object-oriented language implemented on GraalVM/Truffle.
It has familiar TypeScript/Kotlin-like syntax, explicit mutability, final-by-default classes,
single inheritance with controlled `open`/`override`, multiple interfaces with default methods,
delegation, null safety with flow narrowing, nominal generics, value-carrying enums, sealed types
with exhaustive `match`, first-class regular expressions, Rust-style raw strings, and Go-style
semicolon insertion.

Solvik source files use the `.sol` extension. The language id is `solvik` and the MIME type is
`application/x-solvik`. There is no SimpleLanguage compatibility mode.

## Documentation

The authoritative documents are:

- `docs/LANGUAGE_SPEC.md` — normative syntax and semantics;
- `docs/ARCHITECTURE.md` — compiler/runtime boundaries.

`AGENTS.md` records the repository-wide implementation constraints.

## Build

Use the repository wrappers. They select GraalVM from `/opt/graalvm`, prepend it to `PATH`, and run
`./mvnw clean package`, so validation never passes because of stale outputs:

```bash
./build.sh          # JVM distribution and tests
./build-native.sh   # adds the native-image distribution
```

The JVM launcher is produced at `standalone/target/solvik` and the native launcher at
`standalone/target/solvik-native`. Both run a `.sol` source file:

```bash
JAVA_HOME=/opt/graalvm ./standalone/target/solvik language/tests/Hello.sol
JAVA_HOME=/opt/graalvm ./standalone/target/solvik-native language/tests/Hello.sol
```

To regenerate the ANTLR parser from `Solvik.g4`, export `JAVA_HOME=/opt/graalvm` and run
`./generate_parser.sh`. Generated parser sources are checked in and must be regenerated, never
hand-edited.

## License

New Solvik source is licensed under the Apache License, Version 2.0; see `LICENSE-APACHE`.
Files adapted from GraalVM SimpleLanguage remain under the upstream Universal Permissive License,
Version 1.0; see `LICENSE.md`, and carry the required Oracle attribution. The repository is derived
in place from GraalVM SimpleLanguage so that proven Truffle infrastructure could be reused; no
SimpleLanguage syntax or semantics are exposed. See `NOTICE` for attribution and the license split.
