# Solvik

Solvik is a small, statically typed language that compiles to standalone Java 25 source.
A generated program depends only on the JDK and on the Java libraries it binds explicitly;
there is no Solvik runtime jar and no interpreter loop.

The language declares bindings with `name := value`, `name: Type = value`, and
`name? := value`, and keeps them immutable unless declared `mut`; `Null` is a singleton
type and value, ordinary types are non-null, and `T?` is nullable. It supports
signature-based function, method, and constructor overloading plus nominal traits with
required functions and stateless defaults. Its packaged concurrency API
accepts explicit task objects and always starts Java 25 virtual threads, with indefinite and
timed `await` operations.

I/O is ordinary standard-library API use: the bundled `Channel`, `Stdio`, `TextReader`,
`TextWriter`, `ByteReader`, `ByteWriter`, and `File` types are declared in packaged `.sol` sources
with checked native bindings. There are no output built-ins; `TextWriter.writeLine` appends the
published platform terminator `TextWriter.LINE_ENDING`, and `write` translates nothing. `File`
owns a single-direction channel, and the two byte wrappers borrow one.

Concrete overrides can reuse a directly implemented trait's body with
`Trait.function(self, arguments...)`; ordinary calls remain dynamically dispatched.

The compiler is itself a Java 25 application, built with the Maven wrapper in this repository.

## Status

The implementation matches [LANGUAGE.md](LANGUAGE.md), which is the authoritative language
guide; where documentation and code disagree, the code wins. The code-generation contract is
described in [JAVA_BACKEND.md](JAVA_BACKEND.md). `./mvnw verify` is the definition of a
healthy build.

## Requirements

| Requirement | Version | Notes |
| --- | --- | --- |
| JDK | 25 | The launcher, the compiler, and every generated program target Java 25. |
| Maven | none | `./mvnw` downloads Maven and the build dependencies on first use. |
| Network | first build only | Only needed to fetch the Maven distribution and dependencies. |

Run `java -version` before building: the build compiles with `--release 25` and refuses to
produce working classes on an older JDK.

## Quick start

```bash
# Compile the compiler, run every test, check coverage thresholds, then package the uber jar.
./mvnw verify

# Transpile, compile with javac, and run a program; the result is cached.
./solvik run example.sol

# Transpile and compile without running; exit status 0 means the file compiles.
./solvik check example.sol

# Keep the generated Java sources next to the cached classes for inspection.
./solvik run --keep-sources example.sol

# Resolve bindings, compile, and run against explicit dependency JARs.
./solvik run --cp "lib/a.jar:lib/b.jar" app.sol
```

The launcher builds `target/solvik.jar` when it is missing or older than the compiler sources,
so a fresh checkout can run `./solvik run app.sol` directly. The packaged jar is also runnable on
its own: `java -jar target/solvik.jar run app.sol`.

## How it works

1. `ProgramLoader` loads the packaged standard library — every `.sol` resource under the
   compiler's own resources — and then the entry file and the files it imports. An import names
   a file; only a packaged resource may declare the reserved `solvik` root namespace.
2. `Lexer` and `Parser` produce the AST in `Ast`.
3. `TypeChecker` resolves names, validates modules and signatures, resolves bundled `native`
   declarations against the compiler's binding registry, and records a type for every expression
   and binding.
4. `JavaLowerer` turns the checked AST into a typed Java IR (`dev.solvik.compiler.jir`), which
   `JavaSourceRenderer` renders as deterministic Java 25 source. `Transpiler` emits one Java
   file per reached class; `GeneratedIntrinsics` declares the reachable JDK-only support members
   as IR, and `IoSupport` supplies the generated binding adapter when a native binding is used.
5. The CLI compiles the generated sources with the JDK compiler and publishes them atomically in a
   content-addressed cache. `run` then executes the result in an isolated class loader over the
   cached classes and the supplied dependency JARs; `check` stops after the compile.

Diagnostics carry stable codes (`SOL-L###`, `SOL-P###`, `SOL-T###`, `SOL-I###`, `SOL-R###`) and
are rendered by `DiagnosticReporter` with a source line and a caret.

## Command line

```text
usage: solvik [--keep-sources] [--cp <jar-list>] run|check <source file>

run               transpile, compile with javac, and run the source file
check             transpile and compile with javac, without running the file
--keep-sources    retain the generated .java files in the cache entry (run only)
--cp <jar-list>   ordered, explicit dependency JARs (';' on Windows)
```

The command word is required and must be the first positional argument: `./solvik example.sol` is a
usage error. Global options may appear on either side of it. `run` and `check` are the command
words; further ones are added in `Main.Command`, where the recognized words and their dispatch live
together. A source file whose path is exactly a command word must be spelled as a path (`./run`,
`./check`).

`check` stops after `javac`. It checks the whole program — the entry file, every file it imports
transitively, and the packaged standard library — so a diagnostic in an imported file fails the
check and is reported against that file. Nothing is executed: a program that writes output, calls
`exit`, or would abort at run time still checks successfully. It is a one-off compile as well: the
generated Java and classes go into a temporary, randomly keyed entry under the same cache root, that
entry is deleted when the check ends, and no published entry is read or written. A check therefore
leaves `~/.cache/solvik/` as it found it, a later `run` compiles for itself, and repeated checks
recompile; `--keep-sources` applies to `run` only.

Exit codes: `0` success, `1` compile error or runtime abort, `2` bad usage (unknown option, unknown
command, missing command, or the wrong number of operands).

The cache root is resolved from `-Dsolvik.cache.dir`, then `$SOLVIK_CACHE_DIR`, then
`$XDG_CACHE_HOME/solvik`, then `~/.cache/solvik`. Published entries belong to `run`: they are keyed
by the loaded source text of the whole program, the supplied classpath, the `--keep-sources` mode,
and the compiler build, so a rebuild invalidates them automatically. `check` writes only a scratch
entry, which it deletes.

## Repository layout

```text
src/main/java/dev/solvik/compiler/   compiler: CLI, lexer, parser, checker, bindings, backend
src/main/java/dev/solvik/compiler/jir/ typed Java IR, its validator, and the source renderer
src/main/resources/solvik/        packaged standard library: every .sol here belongs to every
                                  program and needs no import; solvik/io declares the bundled
                                  Channel, Stdio, TextReader, TextWriter, ByteReader, ByteWriter,
                                  File, and IoError types
src/test/java/                        unit, language-consistency, and execution tests
example.sol                           complete language corpus: exercises every implemented
                                      construct; include.sol and include-extra.sol provide
                                      imported declarations
include.sol                           imported declarations used by example.sol
include-extra.sol                     further imported declarations; imports include.sol
plans/                                design and implementation records for past features
LANGUAGE.md                           language guide (authoritative)
JAVA_BACKEND.md                       Java representation and lowering contract
AGENTS.md, DESIGN.md, IMPLEMENT.md    engineering workflow for changes in this repository
```

## Verification

`./mvnw verify` runs every gate that a change must satisfy:

- `javac` with `-Xlint:all -Werror` for main and test sources, including every generated adapter;
  a warning fails the build.
- 900+ unit tests (`maven-surefire-plugin`) and packaged-JAR integration tests
  (`maven-failsafe-plugin`), including running programs from `target/solvik.jar` with an empty
  dependency classpath.
- JaCoCo coverage thresholds of 85% instruction and 80% branch coverage.

`LanguageConsistencyTest`, `BundledIoExecutionTest`, `BundledFileExecutionTest`,
`StaticInitializerExecutionTest`, and the other `*ExecutionTest` suites compile and run the generated
Java they produce. `JavaIrTest`, `JavaSourceRendererTest`, and the renderer's javac fixture test cover
the Java IR, precedence, imports, and formatting independently of Solvik; `JavaLoweringTest`
covers lowering edge cases.

## Conventions

- Java sources use four-space indentation, LF line endings, and a single ASCII-sorted
  import block after any `import static` lines.
- Public compiler types carry Javadoc that names the rule being implemented and the section of
  `LANGUAGE.md` it comes from.
- Behavior changes update the authoritative documentation and add the focused regression test
  that proves them.

## License

No license has been declared for this repository yet. Add a `LICENSE` file before distributing
the compiler or generated programs.
