# Solvik

[![build](https://github.com/dhoard/solvik/actions/workflows/build.yml/badge.svg)](https://github.com/dhoard/solvik/actions/workflows/build.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

**A small, statically typed language that compiles to standalone Java 25 source.**

Solvik transpiles to Java, compiles that with `javac`, and runs the result directly on the JDK.
There is no Solvik runtime jar, no interpreter loop, and no reflective dispatch in generated code:
a program depends only on the JDK and on the Java libraries it binds explicitly.

```solvik
module app {

    pub type Account {
        pub owner: String = ""
        mut balance := 0

        pub func Account(owner: String, opening: Integer) {
            self.owner = owner
            balance = opening
        }

        pub func deposit(amount: Integer) {
            balance = balance + amount
        }

        pub func summary() -> String {
            return owner .. ": " .. balance .. " credits"
        }
    }
}

account := app::Account("Ada", 10)
account.deposit(25)
solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(account.summary())
```

Running it prints:

```text
Ada: 35 credits
```

## Highlights

- **Checked before anything runs.** Bindings are immutable unless declared `mut`, ordinary types are
  non-null, `T?` is nullable and `Null` is a singleton type and value, and every expression is
  type-checked before a single class file is written.
- **Java without the ceremony.** Signature-based function, method, and constructor overloading;
  nominal traits with required functions and stateless defaults; modules that map to Java packages;
  `include` blocks for multi-file programs and file-local `alias` blocks for convenient spellings and
  short module names; generics; `switch` with regex cases; exceptions with stable codes.
- **Ordinary Java bindings.** Bundled `native` declarations resolve against real Java classes at
  compile time, so standard I/O, files, collections, and concurrency are library types rather than
  built-in syntax. Only packaged library sources may declare `native` members.
- **No hidden runtime.** Generated Java imports only the JDK and the Java libraries the program
  binds explicitly, uses no reflection, and is emitted as deterministic, warning-free Java 25 source
  that compiles with `javac --release 25`.
- **Designed for iteration.** `run` caches compiled programs by content, so an unchanged program is
  executed straight from cached classes; `check` compiles the whole program without executing it and
  leaves no cache state behind.

## Requirements

| Requirement | Version | Notes |
| --- | --- | --- |
| JDK | 25 | The compiler and every generated program target Java 25 (`--release 25`). |
| Maven | none | `./mvnw` downloads Maven 3.9.11 on first use. |
| Network | first build only | Needed once to fetch the Maven distribution and test dependencies. |

## Quick start

```bash
git clone https://github.com/dhoard/solvik.git
cd solvik

# Build the compiler, run every test, enforce coverage thresholds, package target/solvik.jar.
./mvnw verify

# Transpile, compile, and run a program; the result is cached.
./solvik run example.sol

# Compile the whole program without running it: exit status 0 means it compiles.
./solvik check example.sol
```

The launcher builds `target/solvik.jar` when it is missing or older than the compiler sources, so a
fresh checkout can run `./solvik run app.sol` without a separate build step. The packaged jar is
runnable on its own:

```bash
java -jar target/solvik.jar run app.sol
```

## Example program

`example.sol` is the language corpus: an example program that exercises every implemented construct,
with `include.sol` and `include-extra.sol` supplying included declarations, a diamond include, and
file-local aliases. Running it prints a transcript of the language surface:

```bash
./solvik run example.sol
```

## Command line

```text
usage: solvik [--keep-sources] [--cp <jar-list>] run|check <source file>

run               transpile, compile with javac, and run the source file
check             transpile and compile with javac, without running the file
--keep-sources    retain the generated .java files in the cache entry (run only)
--cp <jar-list>   ordered, explicit dependency JARs (';' on Windows)
```

The command word is required and must be the first positional argument; global options may appear on
either side of it. A source file whose path is exactly a command word must be spelled as a path
(`./run`, `./check`).

`check` is a one-off compile. It checks the whole program — the entry file, every file it includes
transitively, and the packaged standard library — so a diagnostic in an included file fails the
check and is reported against that file. Nothing is executed: a program that writes output, calls
`exit`, or would abort at run time still checks successfully. Generated Java and classes go into a
temporary, randomly keyed entry under the cache root, which is deleted when the check ends, so a
check reads no published entry, writes none, and leaves `~/.cache/solvik/` as it found it.

Exit codes: `0` success, `1` compile error or runtime abort, `2` bad usage (unknown option, unknown
command, missing command, or the wrong number of operands).

The cache root is resolved from `-Dsolvik.cache.dir`, then `$SOLVIK_CACHE_DIR`, then
`$XDG_CACHE_HOME/solvik`, then `~/.cache/solvik`. Published entries belong to `run`; they are keyed
by the loaded source text of the whole program, the supplied classpath, the `--keep-sources` mode,
and the compiler build, so a compiler rebuild invalidates them automatically.

`--cp` takes explicit, ordered JARs — no directories, wildcards, or manifest-based resolution. The
same JARs are used to resolve bindings, to compile the generated Java, and to run the program.

## How it works

1. `ProgramLoader` loads the packaged standard library — every `.sol` resource in the compiler's own
   resources — and then the entry file and the files its `include` blocks name, transitively. An
   include names a file; only a packaged resource may declare the reserved `solvik` root namespace.
2. `Lexer` and `Parser` produce the AST in `Ast`.
3. `TypeChecker` resolves names, validates modules and signatures, resolves bundled `native`
   declarations against the compiler's binding registry, and records a type for every expression and
   binding.
4. `JavaLowerer` turns the checked AST into a typed Java IR (`dev.solvik.compiler.jir`), which
   `JavaSourceRenderer` renders as deterministic Java 25 source. `Transpiler` emits one Java file
   per reached class; `GeneratedIntrinsics` declares the reachable JDK-only support members as IR,
   and `IoSupport` supplies the generated binding adapter when a native binding is used.
5. The CLI publishes the generated sources and classes atomically in a content-addressed cache;
   `run` then executes the program in an isolated class loader, and `check` stops after `javac`.

Diagnostics carry stable codes (`SOL-L###`, `SOL-P###`, `SOL-T###`, `SOL-I###`, `SOL-R###`) and are
rendered with the offending source line and a caret.

## Standard library

The standard library is packaged as `.sol` sources inside the compiler jar, so every program sees
it without an include, and only the types a program actually reaches are emitted into the generated
Java.

| Namespace | Types |
| --- | --- |
| `solvik::collection` | `List`, `Map`, `Stack` |
| `solvik::concurrency` | `Task`, `Thread` — `Thread.start` starts a virtual thread; `await()` and `await(milliseconds)` join it |
| `solvik::util` | `UUID` — version 7 (RFC 9562), written in Solvik rather than bound to Java |
| `solvik::io` | `Channel`, `Stdio`, `TextReader`, `TextWriter`, `ByteReader`, `ByteWriter`, `File`, `IoError` |

## Project layout

```text
src/main/java/dev/solvik/compiler/       compiler: CLI, lexer, parser, checker, bindings, backend
src/main/java/dev/solvik/compiler/jir/   typed Java IR, its validator, and the source renderer
src/main/resources/solvik/               packaged standard library (.sol sources, loaded into every
                                         program)
src/test/java/                           unit, language-consistency, and execution tests
example.sol                              example program: exercises every implemented construct
include.sol, include-extra.sol           included declarations and file-local aliases used by
                                         example.sol
solvik                                   launcher script (builds and runs target/solvik.jar)
pom.xml                                  Maven build; ./mvnw is the wrapper
LANGUAGE.md                              language guide (authoritative)
JAVA_BACKEND.md                          Java representation and lowering contract
LICENSE                                  MIT license
plans/                                   design and implementation records (local, not versioned)
```

## Building and testing

`./mvnw verify` is the definition of a healthy build and the same command CI runs:

- `javac` with `-Xlint:all -Werror` for main and test sources, including every generated adapter.
- 900+ unit tests (`maven-surefire-plugin`) and packaged-JAR integration tests
  (`maven-failsafe-plugin`), including running programs from `target/solvik.jar` with an empty
  dependency classpath.
- JaCoCo coverage thresholds of 85% instruction and 80% branch coverage over the whole bundle.

`LanguageConsistencyTest`, `BundledIoExecutionTest`, `BundledFileExecutionTest`,
`StaticInitializerExecutionTest`, and the other `*ExecutionTest` suites compile and run the Java
they generate. `JavaIrTest`, `JavaSourceRendererTest`, and the renderer's `javac` fixture test
cover the Java IR, precedence, imports, and formatting independently of Solvik; `JavaLoweringTest`
covers lowering edge cases.

Continuous integration runs on GitHub Actions with JDK 25 (Temurin) for pushes to `main` and for
pull requests, and uploads the test and coverage reports.

## Documentation

| Document | Contents |
| --- | --- |
| [LANGUAGE.md](LANGUAGE.md) | The language guide: bindings, nullability, control flow, generics, traits, modules, Java interoperability, diagnostics |
| [JAVA_BACKEND.md](JAVA_BACKEND.md) | The Java representation, lowering rules, and code-generation contract |
| [PROJECT.md](PROJECT.md) | Project priorities, contracts, and the rules a change must preserve |
| [AGENTS.md](AGENTS.md), [DESIGN.md](DESIGN.md), [IMPLEMENT.md](IMPLEMENT.md) | Engineering workflow: design plans, implementation plans, and verification |

`LANGUAGE.md` is authoritative for language behavior. Where documentation and code disagree, the
code wins and the documentation is the bug.

## Versioning and compatibility

Solvik is pre-1.0 (`0.1.0-SNAPSHOT`). The language, the CLI, and the shape of the generated Java may
change between releases, and each such change updates `LANGUAGE.md` or `JAVA_BACKEND.md` together
with the regression that proves it. Diagnostic codes (`SOL-…`) are stable identifiers; the CLI exit
codes are fixed at `0` success, `1` compile error or runtime abort, and `2` bad usage.

## Contributing

Issues and pull requests are welcome.

1. Open an issue before starting anything larger than a bug fix.
2. Branch from `main` and make the smallest coherent change.
3. Run `./mvnw verify`; it must pass before a pull request is reviewed.
4. Update the authoritative documentation for any behavior change, add the focused regression that
   proves it, and update `example.sol` when the language surface changes.
5. Describe the behavior change and the verification you ran in the pull request.

Java sources use four-space indentation, LF line endings, and a single ASCII-sorted import block
after any `import static` lines. Code, comments, and documentation use USA English spellings.

## Reporting issues

Open an issue at <https://github.com/dhoard/solvik/issues>. Please include your `java -version`,
the Solvik source that reproduces the problem, the exact command you ran, and the diagnostic or exit
code you saw.

## License

Solvik is released under the MIT License. The full text, including the copyright notice, is in
[LICENSE](LICENSE).
