<p align="center">
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License Apache 2.0"/>
  <img src="https://img.shields.io/badge/version-0.1.0-blue.svg" alt="Version 0.1.0"/>
  <img src="https://img.shields.io/badge/java-17-007396.svg" alt="Java 17"/>
  <img src="https://img.shields.io/badge/maven-3.9+-C71A36.svg" alt="Maven 3.9+"/>
</p>

<h1 align="center">solvik</h1>

<p align="center">
  <em>A statically typed, struct-and-trait language transpiled to Java 17.</em>
</p>

---

## Overview

Solvik is a statically typed programming language. Concrete state lives in
nominal managed-reference structs; behavior is defined by struct and
trait methods. Instance methods declare an explicit `self` receiver.
Solvik has no struct inheritance, free functions, closures, or function
values.

This repository is a single-module Maven project containing an independent
Solvik-to-Java transpiler written in Java 17. The transpiler has a handwritten
lexer and recursive-descent parser, an immutable AST, a symbol/type checker, a
dedicated lowering phase into a backend-neutral typed IR, a small
constant-folding optimizer, a Java backend lowering phase, and a deterministic
Java source emitter. It does not use a bytecode VM or a native runtime: the
generated Java embeds everything it needs and is compiled by `javac`.

The normative language description is in [LANGUAGE.md](LANGUAGE.md); the type
and operational semantics are in [SEMANTICS.md](SEMANTICS.md). The compiler
architecture and optimization boundaries are in
[TRANSPILER_JAVA.md](TRANSPILER_JAVA.md).

### What Solvik Looks Like

```solvik
package demo

trait Greeter {

    func greet(self, name: String): String
}

struct Bot implements Greeter {

    prefix: String

    pub func new(prefix: String): Self {
        return Self {
            prefix: prefix,
        }
    }

    pub func greet(self, name: String): String {
        return self.prefix .. name
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let g: Greeter = Bot.new("hello ")
        System.getOut().println(g.greet("world"))
        return 0
    }
}
```

Transpile it and run the generated Java:

```sh
./build.sh
./transpile.sh example.sol ExampleProgram
javac --release 17 -Xlint:all ExampleProgram.java
java ExampleProgram
```

`./transpile.sh input.sol OutputClassName` writes exactly
`OutputClassName.java` in the caller's current working directory. The file is
package-free and self-contained: it can be copied to a clean directory and
compiled with a Java 17 JDK and no Solvik runtime JAR.

To transpile and run a program in one step (into a temporary directory),
forwarding any extra arguments to the Solvik program:

```sh
./solvik.sh example.sol
./solvik.sh test/cases/39-cli-arguments/main.sol one two
```

## Building

Requirements: a JDK 17 or newer and the Maven Wrapper (committed). No global
Maven installation is required.

```sh
./build.sh          # ./mvnw -B clean verify: compile, test, and package
./mvnw test         # compile and run unit + conformance tests
./mvnw package      # build target/solvik.jar
./mvnw clean verify # full quality gate
```

`build.sh` produces the executable transpiler JAR at `target/solvik.jar`.
`transpile.sh` runs it and forwards all arguments unchanged. If the JAR is
missing, `transpile.sh` prints a `run ./build.sh first` message and exits with
code 3.

The low-level equivalent of the launcher is:

```sh
java -jar target/solvik.jar input.sol OutputClassName
```

## Testing

Tests run under Maven Surefire as part of the normal lifecycle:

- `FrontendTests` exercises the lexer, parser, semantic analyzer, IR, and
  emitter.
- `CompilerPhaseTests` covers individual phases and the boundaries between
  them: AST-to-Solvik-IR lowering, constant folding, Java expression lowering,
  emitter precedence/associativity, structural runtime-feature reachability,
  determinism, diagnostics, and phase timing.
- `MonitorTest` covers the automatic struct monitor and `atomic(...)`: the
  generated fair lock/lock-order fields, instance-method and trait-default
  locking, static-method non-serialization, deterministic multi-object
  ordering, identity deduplication, `finally` release, and a real multithreaded
  increment/opposite-order run.
- `ConformanceTest` transpiles every fixture under `test/cases/`, compiles
  the generated Java with `javac --release 17 -Xlint:all -Werror`, runs it,
  and checks exit codes and golden output. It also covers `example.sol`, the
  wrapper-name collision case, single-file independence, and the CLI exit
  codes.

See [CONFORMANCE.md](CONFORMANCE.md) for the fixture layout and how to add
cases. Manual performance measurements live in `benchmarks/` and are described
in [benchmarks/README.md](benchmarks/README.md).

## Repository layout

```
pom.xml                     the only Maven project descriptor (single module)
build.sh                    Maven-backed build entry point
transpile.sh                transpiler CLI entry point
solvik.sh                   transpile-and-run convenience entry point
src/main/java/org/solvik/transpiler/
                            lexer, parser, analyzer, lowering, optimizer, CLI
src/main/java/org/solvik/transpiler/language/
                            backend-neutral operators and literal kinds
src/main/java/org/solvik/transpiler/backend/
                            JavaLowerer, JavaIr, JavaEmitter, runtime modules
src/test/java/org/solvik/transpiler/
                            JUnit 5 unit, phase, and conformance tests
test/cases/                 language conformance fixtures
benchmarks/                 optional manual Java benchmarks
docs/                       performance notes
example.sol                 full-language tour (deterministic)
sublime/                    Sublime Text syntax module
```

The compiler pipeline is documented in [TRANSPILER_JAVA.md](TRANSPILER_JAVA.md):

```
.sol  ->  Lexer  ->  Parser  ->  AST  ->  SemanticAnalyzer
      ->  SolvikLowerer  ->  SolvikProgram / SolvikStmt / SolvikIr
      ->  IrOptimizer  ->  JavaLowerer / JavaProgram
      ->  JavaEmitter  ->  .java
```

Each phase has one responsibility, and the dependencies run one way:
`frontend -> semantic -> Solvik IR -> Java backend`. The backend never walks
the parser AST and never re-derives semantic decisions that the frontend
already made.

## Language highlights

- Structs with private state and methods, explicit traits with default
  methods and delegation, enums with payload variants and `match`. Instance
  methods declare an explicit `self` receiver, which is inferred from the
  first parameter (its absence makes a static method).
- Method return types are optional: omitting `: Type` declares a `Void`
  method that returns no value; an explicit `: Void` is rejected.
- Composition instead of struct inheritance: a struct may delegate an
  trait to a private composed field; composition never creates a
  subtype relationship.
- Generics with type erasure: `Box<T>`, `Pair<A, B>`, constrained
  parameters.
- Java-shaped collections: `List<T>`, `Map<K, V>`, `Stack<T>`, `Set<T>`
  with previous-value/nullable return conventions, `Integer` sizes and
  indices, hash-indexed O(1) map/set operations, and per-collection
  thread-safety (unrelated collections progress concurrently). Built-in
  collections are not `atomic(...)` operands.
- Explicit nullability (`T?`) with coalesce (`??`) and narrowing.
- Automatic per-struct monitors: every struct instance method holds the
  receiver's exclusive, reentrant monitor, and `atomic(a, b) { ... }` holds
  several instances' monitors for a block. Static methods are not serialized.
- No free functions, no closures: threads take `Runnable` objects.
- Conversions via `<Type>.from(...)`; introspection via `Type.of` /
  `Type.isType`.
- Shared-heap threads with `Mutex` and `Semaphore`; processes; regex; JSON;
  Base64; MD5/SHA-1/SHA-256; file I/O.
- `System` process/runtime services: standard-stream accessors
  (`getIn`/`getOut`/`getErr`), the LF line separator, host-environment lookup
  (`getEnv(name)` nullable, `getEnv()` mutable snapshot), monotonic and
  wall-clock time (`getNanoTime`, `getCurrentTimeMillis`), and a
  program-local property store (`getProperty`/`setProperty`/
  `clearProperty`).
- Scope blocks: `{ ... }` as a statement for explicit variable lifetime
  management.

## Diagnostics and exit codes

Diagnostics carry codes by family: `L###` lexer, `P###` parser, `C###`
semantic/compiler, `E###` runtime, `W###` warnings. Warnings do not affect
the exit code.

| Exit | Meaning |
| ---- | ------- |
| 0    | success |
| 1    | compilation error (source diagnostics) |
| 2    | generated-program runtime error / uncaught exception |
| 3    | CLI / internal error |

On success the process status is the `Integer` returned by the program's
`Main.run` entry point (Java's `System.exit(int)` status), so returning a
non-zero value from `Main.run` produces that exit code.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
