<p align="center">
  <img src="https://img.shields.io/badge/status-experimental-orange.svg" alt="Status: Experimental"/>
  <img src="https://img.shields.io/badge/runtime-GraalVM%20%2F%20Truffle-000000.svg" alt="GraalVM / Truffle"/>
  <img src="https://img.shields.io/badge/Java-25-blue.svg" alt="Java 25"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0%20%2F%20UPL%201.0-blue.svg" alt="License: Apache 2.0 / UPL 1.0"/>
</p>

<h1 align="center">Solvik</h1>

<p align="center">
  <strong>A small, strongly typed, expression-oriented language for GraalVM.</strong>
</p>

<p align="center">
  Familiar syntax. Predictable semantics. Compile-time safety without an elaborate type system.
</p>

---

> **Project status**
>
> Solvik is an experimental language under active development. The language specification is being
> stabilized and the implementation is evolving with it. It is suitable for language experimentation,
> compiler work, and evaluation, but should not yet be treated as a production-stable platform.

## Overview

Solvik is a strongly and statically typed, nominally typed, object-oriented language implemented on
[GraalVM Truffle](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/).

Its design combines a familiar Java/TypeScript/Kotlin-style surface with stricter and more explicit
semantics:

- immutable-by-default bindings with `val` and explicit mutation with `var`;
- non-null types by default, nullable `T?`, safe access, coalescing, and flow narrowing;
- classes that are final by default, controlled single inheritance, and explicit `override`;
- multiple interfaces with default methods;
- composition through statically resolved delegation;
- nominal generics with compile-time checking;
- value-carrying enums and sealed types with exhaustive `match`;
- non-fallthrough `switch`;
- first-class `Regex`;
- Rust-style raw strings;
- `..` string concatenation;
- range-based `for` loops;
- Go-style lexical semicolon insertion;
- compile-time `include` and lightweight module namespaces;
- a statically validated compiler pipeline that lowers to an optimized Truffle AST.

Solvik aims to be **strict without being complicated**. It borrows useful ideas from modern languages
without adopting their entire programming model: there is no borrow checker, no gradual `any`,
no structural type system, no multiple class inheritance, and no implicit numeric widening.

## What Solvik looks like

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

Top-level executable statements form one implicit `main`, so small programs do not require an
entry-point class or function.

## Design philosophy

Solvik is built around a few deliberate principles.

### Familiar syntax, stronger guarantees

The language should be immediately readable to developers coming from Java, Kotlin, TypeScript,
C#, or Dart, while moving errors into compile time whenever practical.

Examples include:

- exact function arity;
- static name and member resolution;
- strict assignment compatibility;
- immutable bindings;
- nullability checking;
- inheritance validation;
- interface conformance;
- exhaustive matching;
- source-located diagnostics.

A program with compile-time errors never produces an executable Truffle call target.

### Explicit over implicit

Solvik avoids behavior that is convenient locally but difficult to reason about globally.

For example:

```solvik
val x: Int = 1
val y: Long = Long(x)
```

Numeric conversion is explicit. There is no implicit widening or narrowing.

Likewise, `Any` is a real top type, not an escape hatch from static checking:

```solvik
val value: Any = "hello"
val count: Int = value // compile error
```

### Composition first, inheritance controlled

Classes are final by default. Class inheritance is single and opt-in, while interfaces and
delegation provide reusable behavior without encouraging deep inheritance hierarchies.

```solvik
class Service implements Logger {
    delegate val logger: Logger
}
```

Delegation is resolved statically and ambiguities are compile-time errors.

### Small semantic surface

Solvik intentionally avoids accumulating features simply because another language has them.

The goal is not maximum expressiveness. The goal is **high clarity per feature**.

---

## Where Solvik sits

Solvik is not intended to be a clone of any existing language. Its design sits between several
mainstream languages while making a more conservative trade-off around semantic complexity.

| Language | What Solvik shares | Where Solvik differs |
|---|---|---|
| **Java** | nominal typing, classes, interfaces, managed runtime, explicit structure | less ceremony, immutable-by-default bindings, null safety, exhaustive matching, no fallthrough-oriented design |
| **Kotlin** | `val` / `var`, null safety, concise syntax, modern OO ergonomics | intentionally smaller semantic surface; fewer implicit or advanced language mechanisms |
| **Swift** | strong static typing, safe defaults, exhaustiveness, value-oriented control-flow ideas | GraalVM-based, more conventional Java-family OO model, smaller language surface |
| **TypeScript** | approachable syntax and low visual ceremony | nominal rather than structural typing; no gradual `any`; semantics do not depend on JavaScript |
| **Rust** | raw strings, exhaustiveness, explicit semantics, preference for compile-time validation | garbage-collected managed runtime; no borrow checker, lifetimes, or ownership model |
| **Dart** | approachable modern syntax, object orientation, application-language focus | stronger emphasis on strict compile-time semantics and fewer dynamic escape hatches |
| **Scala** | expression-oriented ideas and JVM/GraalVM ecosystem proximity | deliberately avoids Scala's advanced type-system and metaprogramming complexity |
| **Go** | lexical semicolon insertion, simplicity as a design constraint | richer static type system, classes/interfaces, null safety, pattern-oriented features |
| **C#** | modern nominal OO and strong static typing | substantially smaller feature surface and fewer historical compatibility constraints |
| **Gleam / Roc** | emphasis on predictable semantics and compile-time correctness | Solvik retains a familiar nominal OO programming model rather than a primarily functional one |
| **Zig / Mojo** | explicitness and modern language-design goals | Solvik targets managed application programming rather than systems programming or heterogeneous compute |

A useful shorthand is:

> **TypeScript-like readability, Kotlin/Swift-style safety, Rust-inspired explicitness, and Go-like restraint—running on GraalVM.**

The comparison is about design direction, not source compatibility or feature equivalence.

---

## Language highlights

### Explicit mutability

```solvik
val name = "Solvik"
var count = 0

count = count + 1
name = "Other" // compile error
```

`val` freezes the binding, not the entire reachable object graph.

### Null safety

Types are non-null by default.

```solvik
val name: String = "Solvik"
val optional: String? = null
```

Flow analysis can narrow nullable values:

```solvik
func length(value: String?): Int {
    if (value != null) {
        return value.length
    }

    return 0
}
```

### Controlled inheritance

Classes and overridable members are final unless explicitly opened.

```solvik
open class Animal {
    open func speak(): String {
        return "..."
    }
}

class Dog extends Animal {
    override func speak(): String {
        return "woof"
    }
}
```

Solvik supports single class inheritance and multiple interfaces.

### Delegation

Behavior reuse does not require inheritance.

```solvik
interface Logger {
    func log(message: String)
}

class Service implements Logger {
    delegate val logger: Logger

    Service(logger: Logger) {
        this.logger = logger
    }
}
```

Delegated implementations are resolved statically.

### Sealed types and exhaustive `match`

Solvik supports value-carrying enums, sealed type hierarchies, and exhaustive pattern matching.

The compiler verifies that all known variants are covered before lowering the program.

### Non-fallthrough `switch`

`switch` never implicitly falls through from one case to another.

Cases may use compile-time constants and regular-expression patterns, with an optional `default`.

### First-class regular expressions

`Regex` is a built-in type rather than merely a convention around strings.

Constant regex patterns can be compiled once and reused by the runtime.

### Raw strings

Rust-style raw-string delimiters avoid escaping-heavy source:

```solvik
val text = r#"He said "hello"."#
val more = r##"A value containing "# inside"##
```

The opening delimiter determines the exact closing delimiter.

### String concatenation

Solvik uses `..` rather than overloading `+` for both arithmetic and strings:

```solvik
val total = 10
println("total = " .. total)
```

Arithmetic binds more tightly than concatenation:

```solvik
"a = " .. x + y
```

is parsed as:

```text
"a = " .. (x + y)
```

### Ranges

Range syntax distinguishes inclusive and exclusive traversal:

```solvik
for (i in 0...10) {
    println(i)
}

for (i in 0..<10) {
    println(i)
}
```

The language also defines descending-exclusive range syntax with `..>`.

### Semicolon insertion

Solvik accepts explicit semicolons but does not require them at ordinary line endings.

The lexer performs deterministic, Go-style semicolon insertion before parsing. This is a lexical
rule, not JavaScript-style parser recovery.

---

## Static compiler architecture

Solvik deliberately separates syntax, semantic analysis, and execution.

```text
Source
  |
  v
Lexer
  |
  v
Semicolon-Inserting Token Stream
  |
  v
ANTLR Parser
  |
  v
Solvik Syntax AST
  |
  +--> include / module resolution
  +--> symbol collection
  +--> name resolution
  +--> type resolution
  +--> static type checking
  +--> nullability / flow analysis
  +--> inheritance validation
  +--> interface conformance
  +--> delegation resolution
  +--> exhaustiveness analysis
  |
  v
Typed / Lowered Solvik Representation
  |
  v
Truffle AST
  |
  v
GraalVM execution / JIT
```

The syntax AST models source semantics and retains source spans for diagnostics. Executable Truffle
nodes are a separate representation and are created only after static analysis succeeds.

This keeps the compiler front end independent from the execution backend and leaves open the
possibility of targeting another Truffle representation later without redesigning the type checker.

### Why Truffle?

GraalVM Truffle provides mature infrastructure for implementing languages:

- specialization and partial evaluation;
- optimized call targets;
- inline caching;
- tooling and instrumentation;
- Polyglot interoperability;
- JVM execution;
- native-image integration.

Solvik reuses that execution infrastructure while replacing the source language and dynamic
semantics inherited from GraalVM SimpleLanguage.

---

## Type system

Solvik uses an explicit compiler type model rather than treating Java classes as the language's type
system.

Conceptually:

```text
Any
├── Number
│   ├── Byte
│   ├── Short
│   ├── Int
│   ├── Long
│   ├── Float
│   └── Double
├── Boolean
├── Char
├── String
├── Unit
├── Regex
├── RegexMatch
├── List<T>
├── Set<T>
├── Map<K, V>
├── Stack<T>
└── user-defined classes / interfaces / enums
```

`Nothing` is the bottom type.

Important properties include:

- nominal static typing;
- no implicit `any`;
- no implicit numeric widening or narrowing;
- explicit nullable types;
- nominal generics;
- checked integral arithmetic;
- compile-time type checking before lowering;
- user-defined operator overloading is not part of the current language.

---

## Multi-file programs

A top-level `include` directive combines another `.sol` file into the same statically checked
program.

```solvik
// Main.sol
include "Greeting.sol"

println(greet("Solvik"))
```

```solvik
// Greeting.sol
func greet(name: String): String {
    return "Hello, " .. name .. "!"
}
```

Includes are resolved at compile time. Source files are parsed independently and their syntax trees
are combined; source text is not concatenated and reparsed.

This preserves:

- source identity;
- diagnostics;
- semicolon boundaries;
- instrumentation locations;
- deterministic include behavior.

Each canonical physical file is included at most once per compilation, and include cycles are
compile-time errors.

### Modules

A source file may declare a module namespace:

```solvik
module math_utils

func double(value: Int): Int {
    return value * 2
}
```

It can be included under a file-local alias:

```solvik
module app_main

include "Math.sol" alias math

println(math::double(21))
```

`::` is namespace qualification; `.` remains ordinary member access.

---

## Diagnostics

Compile-time diagnostics are source-located and carry stable machine-readable codes:

| Family | Compiler stage |
|---|---|
| `SOLV-LEX-nnn` | lexer and raw-string scanning |
| `SOLV-PARS-nnn` | parser |
| `SOLV-RESOL-nnn` | name, member, include, and module resolution |
| `SOLV-TYPE-nnn` | static type checking and flow analysis |
| `SOLV-SEM-nnn` | semantic validation |

The diagnostic architecture is intended to make compiler errors useful both to humans and to future
tooling.

---

## Building

### Requirements

- GraalVM for JDK 25
- `native-image` for native builds
- a POSIX-compatible shell
- network access the first time Maven resolves dependencies

The repository build wrappers locate GraalVM through:

1. `GRAALVM_HOME`;
2. a GraalVM `JAVA_HOME`;
3. `/opt/graalvm`.

A non-GraalVM JDK is rejected to avoid accidental builds against the wrong runtime.

### JVM build

```sh
./build.sh
```

This performs a clean Maven build and runs the test suite.

The JVM launcher is produced at:

```text
standalone/target/solvik
```

Run a source file with:

```sh
./standalone/target/solvik language/tests/Hello.sol
```

Source may also be supplied on standard input by omitting the file argument.

### Native build

```sh
./build-native.sh
```

The native launcher is produced at:

```text
standalone/target/solvik-native
```

Run it with:

```sh
./standalone/target/solvik-native language/tests/Hello.sol
```

The native distribution embeds the language runtime and does not require a JVM at runtime.

---

## Testing

Solvik uses:

- **JUnit 6**
- **AssertJ**
- **Maven Surefire**

The complete JVM test suite runs as part of:

```sh
./build.sh
```

Checked-in `.sol` examples under `language/tests/` serve as executable examples of the published
syntax and behavior.

Every language change should include positive and negative tests, particularly for:

- parsing;
- name resolution;
- type checking;
- semantic validation;
- diagnostics;
- lowering;
- runtime execution;
- GraalVM integration.

The final repository quality gates are:

```sh
./build.sh
./build-native.sh
```

Both must pass after compiler/runtime changes.

---

## Repository layout

```text
pom.xml
├── language/
│   ├── src/main/java/org/solvik/
│   │   ├── parser/          lexer support, semicolon insertion, ANTLR parser
│   │   ├── ast/             source-level syntax AST
│   │   ├── type/            compiler type model
│   │   ├── semantic/        resolution, typing, flow, validation
│   │   ├── lowering/        typed representation -> Truffle AST
│   │   ├── diagnostic/      source-located SOLV-* diagnostics
│   │   ├── regex/           regex abstraction and portable dialect
│   │   ├── source/          source files, spans, catalog, string handling
│   │   └── truffle/         language integration and runtime
│   ├── src/test/            JUnit / AssertJ tests
│   └── tests/               executable .sol examples and golden outputs
├── launcher/                command-line launcher
├── standalone/              JVM distribution and native-image packaging
├── docs/
│   ├── LANGUAGE_SPEC.md     normative language definition
│   └── ARCHITECTURE.md      compiler/runtime architecture
├── AGENTS.md                repository implementation constraints
├── CONTRIBUTING.md
├── LICENSE.md
└── NOTICE.md
```

The ANTLR grammar is:

```text
language/src/main/java/org/solvik/parser/grammar/Solvik.g4
```

Generated parser sources are build artifacts and must not be edited directly.

---

## Implementation heritage

Solvik is an in-place conversion of
[GraalVM SimpleLanguage](https://github.com/graalvm/simplelanguage).

The project retains useful infrastructure such as Truffle integration, execution specialization,
interop patterns, tooling hooks, and build structure while replacing:

- the guest-language grammar;
- dynamic typing assumptions;
- the object/type model;
- parser-directly-to-runtime-node architecture;
- SimpleLanguage naming and public identity;
- SimpleLanguage source compatibility.

There is no SimpleLanguage compatibility mode.

Solvik source files use `.sol`, the GraalVM language id is `solvik`, and the MIME type is
`application/x-solvik`.

---

## Language specification

The authoritative language definition is:

[`docs/LANGUAGE_SPEC.md`](docs/LANGUAGE_SPEC.md)

Compiler/runtime boundaries are documented in:

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)

Repository-wide implementation rules are documented in:

[`AGENTS.md`](AGENTS.md)

When implementation behavior and the language specification disagree, the specification is
authoritative unless it is explicitly updated as part of the language design.

---

## Project direction

Solvik's intended niche is a managed application language that is:

- easier to reason about than a highly dynamic language;
- smaller conceptually than languages with advanced ownership or type-system machinery;
- more modern and strict than traditional Java-style language design;
- familiar enough that Java, Kotlin, TypeScript, C#, and Dart developers can read it immediately;
- structured so semantic errors are detected before runtime;
- able to benefit from GraalVM's optimizing runtime and interoperability infrastructure.

A concise description is:

> **Solvik is a strongly typed, expression-oriented programming language for GraalVM, designed
> around familiar syntax, predictable semantics, immutable-by-default programming, and compile-time
> validation without an elaborate type system.**

---

## Contributing

Read [`AGENTS.md`](AGENTS.md), [`docs/LANGUAGE_SPEC.md`](docs/LANGUAGE_SPEC.md), and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) before changing language behavior.

Every semantic change should:

1. preserve the compiler architecture;
2. include focused positive and negative tests;
3. update the language specification where semantics change;
4. keep diagnostics precise and source-located;
5. pass both final quality gates:

```sh
./build.sh
./build-native.sh
```

Contributions require sign-off under the [Developer Certificate of Origin](DCO.md).

---

## License

New Solvik source is licensed under the **Apache License, Version 2.0**; see
[`LICENSE.md`](LICENSE.md).

Files adapted from GraalVM SimpleLanguage remain under the upstream **Universal Permissive License
(UPL), Version 1.0** and retain the required Oracle attribution.

See [`NOTICE.md`](NOTICE.md) for attribution and license details.
