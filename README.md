<p align="center">
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License Apache 2.0"/>
  <img src="https://img.shields.io/badge/version-0.1.0-blue.svg" alt="Version 0.1.0"/>
  <img src="https://img.shields.io/badge/rust-stable-000000.svg" alt="Rust stable"/>
</p>

<h1 align="center">solvik</h1>

<p align="center">
  <em>A statically typed, class-based language compiled to bytecode for a Rust virtual machine.</em>
</p>

---

## Overview

Solvik is a statically typed programming language. All behavior lives in
class and interface methods — there are no free functions and no closures.
Programs are compiled by a single Rust toolchain through an explicit IR
stage, verified, encoded to a binary bytecode format, and executed on a
stack-based VM with a managed (mark-and-sweep) heap and shared-heap threads.

The normative language description is in [LANGUAGE.md](LANGUAGE.md); the
type and operational semantics are in [SEMANTICS.md](SEMANTICS.md).
Performance results and the optimization decision log are in
[PERFORMANCE.md](docs/PERFORMANCE.md).

### What Solvik Looks Like

```solvik
module demo

interface Greeter {

    greet(name: String): String
}

class Bot implements Greeter {

    public static new(): Self {
        return Self {}
    }

    override public greet(name: String): String {
        return "hello " .. name
    }
}

class Main {

    public static run(args: String...): Long {
        g: Greeter = Bot.new()
        stdout.println(g.greet("world"))
        return 0
    }
}
```

Run it:

```sh
solvik example.sol
```

Formatting and validation are available without executing the program:

```sh
solvik --format example.sol   # writes formatted source to stdout
solvik --check example.sol    # parses, resolves, and type-checks only
```

The canonical style uses four-space indentation, lowercase dotted module names,
uppercase class/interface/enum names, lowercase methods and members, explicit
`self.field` access, and named fields in `Self` initializers. Local variables,
parameters, loop/catch variables, and pattern bindings are lowercase as well.

## Building

Requirements: a stable Rust toolchain (`cargo`). Dependencies are vendored
under `vendor/`.

```sh
./build.sh            # fmt check, unit tests, clippy, release build, conformance
./build.sh clean      # remove build artifacts
```

The release binary lands in `dist/solvik`.

## Testing

```sh
./test/run.sh                 # conformance suite (test/cases/)
```

See [CONFORMANCE.md](CONFORMANCE.md) for the suite layout and how to add
cases.

## Repository layout

```
src/                Rust compiler + VM (single crate, binary `solvik`)
  lexer.rs          tokenization
  parser.rs         recursive-descent parser (AST)
  ast.rs            abstract syntax tree
  resolve.rs        name resolution, class hierarchy, vtables
  types.rs          type representation and subtyping
  check.rs          type checker + IR emission
  ir.rs             intermediate representation (mandatory stage)
  optimize.rs       peephole IR optimizations (constant folding)
  compiler.rs       IR -> bytecode
  verifier.rs       fixed-point dataflow validation of bytecode
  disasm.rs         bytecode disassembler (SOLVIK_DUMP_BC=1)
  bytecode/         binary encoding/decoding of code modules
  vm/               stack machine, heap/GC, frames, natives
  stdlib/           built-in type signatures and native ids
example.sol         full-language tour (deterministic)
test/cases/         conformance suite
sublime/            Sublime Text syntax module
```

## Language highlights

- Classes with single inheritance, interfaces with default methods, enums
  with payload variants and `match`.
- Generics with type erasure: `Box<T>`, `Pair<A, B>`, constrained
  parameters.
- Explicit nullability (`T?`) with coalesce (`??`) and narrowing.
- No free functions, no closures: threads take `Runnable` objects.
- Conversions via `<Type>.from(...)`; introspection via `Type.of` /
  `Type.isType`.
- Shared-heap threads with `Mutex` and `Semaphore`; processes; regex; JSON;
  Base64; MD5/SHA-1/SHA-256; file I/O.

## Diagnostics and exit codes

Diagnostics carry codes by family: `L###` lexer, `P###` parser, `C###`
semantic/compiler, `V###` verifier, `E###` runtime.

| Exit | Meaning |
| ---- | ------- |
| 0    | success |
| 1    | compilation error |
| 2    | runtime error / uncaught exception |
| 3    | internal error |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
