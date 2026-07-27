
<p align="center">
  <img src="https://img.shields.io/github/actions/workflow/status/dhoard/solvik/build.yaml?branch=main" alt="Build Status"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License Apache 2.0"/>
  <img src="https://img.shields.io/badge/version-0.1.0-blue.svg" alt="Version 0.1.0"/>
  <img src="https://img.shields.io/badge/go-%3E%3D1.25-00ADD8.svg" alt="Go >=1.25"/>
</p>

<h1 align="center">solvik</h1>

<p align="center">
  <em>A compiled, statically-typed programming language with a custom bytecode VM, written in Go.</em>
</p>

---

## Overview

Solvik is a programming language that compiles to bytecode and executes on a custom stack-based virtual machine. It combines the safety of static typing with the expressiveness of modern language features including switch/regex matching, first-class collections, and a comprehensive standard library.

The entire toolchain — lexer, parser, type checker, compiler, bytecode verifier, and VM — is implemented in a single Go module with no external runtime dependencies.

### What Solvik Looks Like

```
package example

def greet(name: string) -> string {
    return "Hello, " + name + "!"
}

def main() -> int {
    println(greet("Solvik"))
    return 0
}
```

## Features

### Language

| Feature | Description |
|---------|-------------|
| **Static typing** | Type-checked at compile time with `int`, `long`, `float`, `double`, `bool`, `char`, `byte`, `string`, `List<T>`, `Map<K,V>` |
| **Nullable types** | `string?` — nullable variant with `??` null-coalescing operator |
| **Type inference** | Return type inference and expression type propagation |
| **Control flow** | `if`/`else if`/`else`, `while`, `for-in` loops, `break`, `continue` |
| **Switch statements** | First-match semantics, no implicit fallthrough, optional `default` |
| **Regex matching** | `regex()` built-in produces first-class regex values for switch case matching |
| **Functions** | Zero or more parameters, return types, early returns, recursion |
| **Collections** | List literals `[1, 2, 3]`, Map literals `{"key": "value"}` |
| **Raw strings** | Rust-style `r"..."`, `r#"..."#`, `r##"..."##` — preserve literal backslashes |
| **Trailing commas** | Optional comma after final call argument — improves multiline diffs |
| **Operators** | Arithmetic (`+`, `-`, `*`, `/`, `%`), comparison, logical (`&&`, `||`, `!`), bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`), string concat (`+`), null coalescing (`??`) |
| **Block scope** | Variables can be scoped within `{ }` blocks with shadowing |
| **Semicolons** | Optional — newlines terminate statements; semicolons allow compact forms |

### Standard Library

| Module | Functions |
|--------|-----------|
| **Core** | `print`, `println`, `string`, `int`, `long`, `double`, `bool`, `typeOf`, `len`, `regex` |
| **String** | `length`, `byteLength`, `charAt`, `substring`, `contains`, `startsWith`, `endsWith`, `indexOf`, `toUpper`, `toLower`, `trim`, `split`, `join` |
| **Math** | `abs`, `min`, `max`, `floor`, `ceil`, `round`, `sqrt`, `pow`, `sin`, `cos`, `tan` |
| **Environment** | `get`, `set`, `keys` |
| **File** | `read`, `write`, `append`, `delete`, `exists` |
| **Process** | `run` — execute external commands |
| **Time** | `now`, `sleep` |

### Toolchain

| Command | Description |
|---------|-------------|
| `solvik run <file>` | Compile and execute a source file |
| `solvik check <file>` | Type-check a source file without executing |
| `solvik compile <file>` | Compile source to portable bytecode (`.lbc`) |
| `solvik exec <file>` | Execute a pre-compiled bytecode file |
| `solvik disassemble <file>` | Disassemble bytecode for debugging |
| `solvik version` | Print version information |

## Quick Start

### Prerequisites

- Go 1.25 or later

### Build

```bash
git clone https://github.com/dhoard/solvik
cd solvik
./build.sh
```

Or manually:

```bash
go build -o dist/solvik ./cmd/solvik
```

### Run Your First Program

```bash
./dist/solvik run example.sol
```

Or create a new file:

```
// hello.sol
package example

def main() -> int {
    println("Hello, Solvik!")
    return 0
}
```

```bash
./dist/solvik run hello.sol
```

### Type-Check Without Running

```bash
./dist/solvik check example.sol
```

### Compile to Bytecode

```bash
./dist/solvik compile example.sol
./dist/solvik exec output.lbc
```

## Language Guide

### Package Declaration

Every Solvik source file must start with a `package` declaration:

```
package example
```

The package name is used for function name mangling across modules during multi-file compilation. The `package` keyword must appear as the first token-producing line in the file.

### Variables

Variables are declared with a name, type annotation, and optional initializer:

```
count: int = 42
name: string = "Solvik"
pi: double = 3.14159
active: bool = true
initial: char = 'A'
small: byte = 255
big: long = 1000000
nullable: string? = null
```

Reassignment uses `=`:

```
count = count + 1
```

### Functions

Functions are declared with `def`, parameters with types, and a return type:

```
def add(a: int, b: int) -> int {
    return a + b
}

def greet() -> string {
    return "Hello!"
}

def logMessage(level: string, message: string) -> void {
    println("[" + level + "] " + message)
}
```

Functions support recursion:

```
def factorial(n: int) -> long {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}
```

### Control Flow

**Conditionals:**

```
if value > 0 {
    return "positive"
} else if value < 0 {
    return "negative"
} else {
    return "zero"
}
```

**While loops:**

```
total: int = 0
i: int = 1
while i <= 10 {
    total = total + i
    i = i + 1
}
```

**For-in loops:**

```
for v in values {
    total = total + v
}
```

`break` and `continue` are supported:

```
for v in values {
    if v < 0 {
        continue
    }
    if v > 100 {
        break
    }
    total = total + v
}
```

### Switch Statements

Switch uses first-match semantics with no implicit fallthrough:

```
switch code {
    case 200:
        return "OK"

    case 404:
        return "Not Found"

    case 500:
        return "Internal Server Error"

    default:
        return "Unknown"
}
```

### Regex Matching

The `regex()` built-in compiles a regular expression and returns a first-class regex value. When used in a switch case expression, the switch value is matched against the pattern:

```
switch entry {
    case regex(r"^ERROR\s+\[\d+\]:"):
        return "structured-error"

    case regex(r"^WARN\s+"):
        return "warning"

    default:
        return "unmatched"
}
```

Raw strings (`r"..."`) are the natural choice for regex patterns — backslashes are preserved literally, so `r"\d"` matches digits without needing to escape the backslash.

Regex values can also be used inline in expressions:

```
// Equivalent to the escaped string form:
regex("^ERROR\\s+\\[\\d+\\]:")
```

### Collections

**Lists:**

```
numbers: List<int> = [10, 20, 30, 40, 50]
first: int = numbers[0]
count: int = len(numbers)
```

**Maps:**

```
config: Map<string, string> = {
    "host":   "localhost",
    "port":   "8080",
    "scheme": "http",
}
host: string = config["host"]
```

### Strings

Ordinary strings support escape sequences:

```
escaped: string = "line1\nline2\ttabbed"
quote: string = "She said \"hello\""
backslash: string = "C:\\Users\\name"
```

Raw strings preserve all characters literally — ideal for regex patterns and file paths:

```
path: string = r"C:\Users\name\file.txt"
quoted: string = r#"The value is "quoted"."#
pattern: string = r"^\d+\.\d+$"
```

### Trailing Commas

A comma after the final function call argument is optional and does not create an extra argument. This is particularly useful for multiline calls:

```
configure(
    "localhost",
    8080,
    true,
)
```

These two calls are equivalent:

```
send("user", "message")
send("user", "message",)
```

### Null Coalescing

The `??` operator provides a default value when a nullable expression is `null`:

```
name: string? = null
display: string = name ?? "Guest"
```

### String Built-ins

```
text: string = "Hello, World!"
length: int = string.length(text)
sub: string = string.substring(text, 0, 5)
hasWorld: bool = string.contains(text, "World")
upper: string = string.toUpper(text)
parts: List<string> = string.split("a,b,c", ",")
joined: string = string.join(parts, "-")
```

### Comments

```
// Single-line comments only (block comments are not supported)
```

## Architecture

Solvik's toolchain follows a traditional multi-phase compiler architecture:

```
Source Code
    │
    ▼
┌─────────────┐
│   Lexer     │  Tokenization (lexical analysis)
└──────┬──────┘
       │ tokens
       ▼
┌─────────────┐
│   Parser    │  Recursive-descent parsing → AST
└──────┬──────┘
       │ AST
       ▼
┌─────────────┐
│  Resolver   │  Name resolution and scope analysis
└──────┬──────┘
       │ resolved AST
       ▼
┌─────────────┐
│  Checker    │  Type checking and type inference
└──────┬──────┘
       │ typed AST
       ▼
┌─────────────┐
│  Compiler   │  AST → Bytecode
└──────┬──────┘
       │ bytecode
       ▼
┌─────────────┐
│  Verifier   │  Bytecode verification
└──────┬──────┘
       │ verified bytecode
       ▼
┌─────────────┐
│  VM         │  Stack-based virtual machine
└─────────────┘
       │
       ▼
   Result
```

### Packages

| Package | Lines | Responsibility |
|---------|-------|----------------|
| `cmd/solvik` | 246 | CLI tool with subcommands (`run`, `check`, `compile`, `exec`, `disassemble`, `version`) |
| `internal/lexer` | 1,065 | Tokenization — keywords, literals, operators, raw strings, comments |
| `internal/parser` | 1,421 | Recursive-descent parser with error recovery |
| `internal/ast` | 473 | AST node definitions for all program constructs |
| `internal/resolver` | 555 | Scope resolution, variable declaration validation |
| `internal/checker` | 1,109 | Type checking, type inference, arity validation |
| `internal/compiler` | 1,336 | Bytecode generation from typed AST |
| `internal/bytecode` | 947 | Bytecode instruction set, serialization, disassembly |
| `internal/vm` | 1,375 | Stack-based virtual machine — execution engine |
| `internal/native` | 837 | Built-in function implementations (core, string, math, env, file, process, time) |
| `internal/runtime` | 276 | Compilation pipeline orchestration, multi-file support |
| `internal/types` | 457 | Type system — type representations, compatibility |
| `internal/symbol` | 555 | Symbol table for scope management |
| `internal/source` | 202 | Source position tracking, span management |
| `internal/diagnostic` | 202 | Error and diagnostic reporting |
| `internal/verifier` | 322 | Bytecode verification — stack balance, operand validation |

## Build System

The `build.sh` script orchestrates the full build pipeline:

```bash
./build.sh               # Full build: clean → build → test → package → script tests
./build.sh quick         # Clean build without tests
./build.sh test          # Run Go tests only
./build.sh scripts       # Run integration test scripts only
./build.sh clean         # Remove dist directory
```

The build process:
1. Cleans the `dist/` directory
2. Builds the `solvik` binary with stripped symbols
3. Runs `go test ./...` (with race detector when CGO is enabled)
4. Runs `go vet ./...`
5. Verifies formatting with `gofmt`
6. Packages the distribution
7. Runs all `.sol` integration test scripts in `test/`

## Testing

### Go Tests

```bash
go test ./...
```

The test suite includes:
- Lexer tests (tokenization, raw strings, edge cases)
- Parser tests (syntax, error recovery)
- Compiler tests (bytecode generation)
- VM runtime tests (execution, phase tests)
- Raw string runtime tests

### Integration Tests

```bash
./dist/solvik run test/<name>.sol
```

Integration test scripts are located in `test/`:

| Script | Description |
|--------|-------------|
| `hello.sol` | Basic variable assignment and printing |
| `for_test.sol` | For-in loop iteration |
| `full_test.sol` | Combined features — functions, lists, while, if |
| `list_test.sol` | List literal construction |
| `semicolon.sol` | Semicolon statement termination |
| `simple_sum.sol` | List iteration and accumulation |
| `string_test.sol` | String operations |
| `switch_test.sol` | Switch/case with exact matching |
| `trailing_comma.sol` | Trailing comma in call arguments |

### Complete Language Example

```bash
./dist/solvik run example.sol
```

`example.sol` is a 750+ line comprehensive demonstration covering every supported language construct and built-in function.

## Project Structure

```
language/
├── cmd/
│   └── solvik/
│       └── main.go          # CLI entry point
├── internal/
│   ├── ast/                 # Abstract syntax tree
│   ├── bytecode/            # Bytecode instruction set
│   ├── checker/             # Type checker
│   ├── compiler/            # AST → bytecode compiler
│   ├── diagnostic/          # Error reporting
│   ├── lexer/               # Lexical analyzer
│   ├── native/              # Built-in functions
│   ├── parser/              # Recursive-descent parser
│   ├── resolver/            # Name resolver
│   ├── runtime/             # Compilation pipeline
│   ├── source/              # Source position tracking
│   ├── symbol/              # Symbol table
│   ├── types/               # Type system
│   ├── verifier/            # Bytecode verifier
│   └── vm/                  # Virtual machine
├── dist/                    # Build output
├── test/                    # Integration test scripts
├── build.sh                 # Build automation
├── example.sol              # Complete language example
├── go.mod                   # Go module definition
└── README.md                # This file
```

## Development

### Prerequisites

- Go 1.25 or later
- Make (optional, for build script)

### Running Tests

```bash
# All tests
go test ./...

# With race detection
CGO_ENABLED=1 go test -race ./...

# Specific package
go test ./internal/parser/...

# Specific test
go test ./internal/runtime -run TestFullRun -v
```

### Code Quality

```bash
# Formatting
gofmt -w .

# Static analysis
go vet ./...
```

## Roadmap

- [x] Lexer with full token set and raw string support
- [x] Recursive-descent parser with error recovery
- [x] Type checker with type inference
- [x] Bytecode compiler and verifier
- [x] Stack-based virtual machine
- [x] Switch/case with regex matching
- [x] Standard library (string, math, env, file, process, time)
- [x] Raw strings (Rust-style)
- [x] Trailing comma support
- [x] Multi-file compilation
- [ ] Generics / parametric polymorphism
- [ ] Standard package manager
- [ ] Language server protocol (LSP) support
- [ ] WASM backend
- [ ] FFI / C interop

## License

Solvik is released under the Apache License, Version 2.0.

```
Copyright (c) 2026-present Douglas Hoard

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
