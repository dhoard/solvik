
<p align="center">
  <img src="https://img.shields.io/github/actions/workflow/status/dhoard/solvik/build.yaml?branch=main" alt="Build Status"/>
  <img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License Apache 2.0"/>
  <img src="https://img.shields.io/badge/version-0.1.0-blue.svg" alt="Version 0.1.0"/>
  <img src="https://img.shields.io/badge/go-%3E%3D1.25-00ADD8.svg" alt="Go >=1.25"/>
</p>

<h1 align="center">solvik</h1>

<p align="center">
  <em>A bytecode-compiled, statically-typed programming language with a custom bytecode VM, written in Go.</em>
</p>

---

## Overview

Solvik is a programming language that compiles to bytecode and executes on a custom stack-based virtual machine. It combines the safety of static typing with the expressiveness of modern language features including switch/regex matching, first-class collections, and a comprehensive standard library.

The entire toolchain — lexer, parser, type checker, compiler, bytecode verifier, and VM — is implemented in a single Go module with no external runtime dependencies.

### What Solvik Looks Like

```
package example

func greet(name: string) -> string {
    return "Hello, " .. name .. "!"
}

func main() -> int {
    println(greet("Solvik"))
    return 0
}
```

## Language Influences

Solvik draws syntax and semantic inspiration from established languages while forging its own identity. The table below summarizes the primary influences found in the language design.

| Rank | Language | Influence Areas |
|------|----------|----------------|
| 1st | **Go** | Package system, `func` keyword, top-level functions, semicolon-optional syntax, `...T` variadic parameters, multiple return values, `print`/`println` built-ins, structural typing (traits), overall architecture, module-based standard library |
| 2nd | **Swift** | `name: Type` parameter syntax, `-> ReturnType` arrow, `for-in` loops, switch no-fallthrough semantics |
| 3rd | **Rust** | `mut` keyword, immutable-by-default binding, raw strings (`r"..."` / `r#"..."#`), enum declarations, trailing commas |
| 4th | **C#** | Nullable types (`Type?` suffix), null-coalescing (`??`) operator |
| 5th | **Java** | Exception handling (`try`/`catch`/`finally`/`throw`), `exception` type, underscore numeric separators (`1_000_000`), typed collection generics (`List<T>`, `Map<K,V>`) |

## Features

### Language

| Feature | Description |
|---------|-------------|
| **Static typing** | Type-checked at compile time with `byte`, `int`, `float`, `bool`, `char`, `string`, `List<T>`, `Map<K,V>`, enum types |
| **Nullable types** | `string?` — nullable variant with `??` null-coalescing operator |
| **Type inference** | Return type inference and expression type propagation |
| **Control flow** | `if`/`else if`/`else`, `while`, `for-in` loops, `break`, `continue` |
| **Exception handling** | `try`/`catch`/`finally`/`throw` with first-class `exception` type (`.message`, `.trace` fields), string auto-conversion, deterministic unwinding, finally-block guarantees |
| **Switch statements** | First-match semantics, no implicit fallthrough, optional `default` |
| **Regex matching** | `regex()` built-in produces first-class regex values for switch case matching |
| **Functions** | Zero or more parameters, return types, early returns, recursion |
| **Enumerations** | `enum Color { Red, Green, Blue }` — user-defined enum types with named integer constants, optional explicit values, trailing comma support, and full type safety |
| **Structs** | User-defined data types with named fields and methods, `pub` visibility, `mut` per-field mutability, value semantics, structural equality |
| **Traits** | Go-style structural typing — abstract behavioral contracts, implicit satisfaction, trait as parameter/variable/return type, dynamic dispatch via fat pointers |
| **Variadic functions** | `func sum(values: ...int)` — Go-style variadic parameters with `...T`, auto-packing into `List<T>`, spread `list...` support |
| **Collections** | List literals `[1, 2, 3]`, Map literals `{"key": "value"}` |
| **Raw strings** | Rust-style `r"..."`, `r#"..."#`, `r##"..."##` — preserve literal backslashes |
| **Underscores in numeric literals** | Java-style `1_000_000`, `3.14_15`, `0xFF_FF` — improves readability of large numbers |
| **Trailing commas** | Optional comma after final call argument — improves multiline diffs |
| **Operators** | Arithmetic (`+`, `-`, `*`, `/`, `%`), comparison, logical (`&&`, `||`, `!`), bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`), string concat (`..`), null coalescing (`??`) |
| **Immutable-by-default** | Variables are immutable by default. `mut x: int = 5` for mutable bindings. Reassignment of immutable variables is a compile error (Rust-style). |
| **Block scope** | Variables can be scoped within `{ }` blocks with shadowing |
| **Semicolons** | Optional — newlines terminate statements; semicolons allow compact forms |

### Standard Library

| Module | Functions |
|--------|-----------|
| **Core** | `print`, `println`, `string`, `int`, `float`, `bool`, `typeOf`, `len`, `regex` |
| **String** | `length`, `byteLength`, `charAt`, `substring`, `contains`, `startsWith`, `endsWith`, `indexOf`, `toUpper`, `toLower`, `trim`, `split`, `join` |
| **Math** | `abs`, `min`, `max`, `floor`, `ceil`, `round`, `sqrt`, `pow`, `sin`, `cos`, `tan`, `PI`, `E` |
| **Environment** | `get`, `set`, `keys` |
| **File** | `read`, `write`, `append`, `delete`, `exists` |
| **Map** | `contains` — check if a key exists in a map |
| **Process** | `run` — execute external commands |
| **Time** | `now`, `sleep` |
| **Random** | `float`, `int`, `range`, `uniform`, `choice`, `shuffle`, `sample`, `seed` |

### Toolchain

| Command | Description |
|---------|-------------|
| `solvik <file>` | Compile and execute a source file |
| `solvik --check <file>` | Type-check a source file without executing |
| `solvik --verbose <file>` | Compile and execute with verbose output |
| `solvik --version` | Print version information |

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
./dist/solvik example.sol
```

Or create a new file:

```
// hello.sol
package example

func main() -> int {
    println("Hello, Solvik!")
    return 0
}
```

```bash
./dist/solvik hello.sol
```

### Type-Check Without Running

```bash
./dist/solvik --check example.sol
```

## Language Guide

### Package Declaration

Every Solvik source file must start with a `package` declaration:

```
package example
```

The package name is used for function name mangling across modules during multi-file compilation. The `package` keyword must appear as the first token-producing line in the file.

### File Dependencies: `use`

A source file can declare dependencies on other `.sol` files using the `use` keyword. The source type must be explicitly specified with `file:` (local file) or `url:` (remote URL):

```
package example

use file:utils.string    // resolves to <file-dir>/utils/string.sol
use file:"db/pool"       // resolves to <file-dir>/db/pool.sol (quoted)
```

Absolute and home-relative paths are also supported:

```
use file:"/usr/lib/common.sol"    // absolute path
use file:"~/modules/http.server"  // resolves to $HOME/modules/http/server.sol
```

Values may be quoted or unquoted. Unquoted dotted names (`file:utils.string`) are automatically converted to paths with directory separators.

Remote URLs require `url:`:

```
use url:"https://example.com/lib.sol" sha-256:abc123def456...
```

The `sha-256` flag provides content integrity verification checksum (required for HTTPS by default). The `insecure:true` flag allows HTTP URLs and skips TLS certificate verification:

```
use url:"http://example.com/lib.sol" sha-256:abc123... insecure:true
```

Flags are optional, order-independent, and can be combined.

The `use` keyword is a compile-time directive only. No code executes at load time — execution starts at `main()`.

#### Scoped access

Functions from a `use`d file are accessed through their package name. The package name is declared by the `package` statement in the target file, not derived from the `use` path:

```
// ---- main.sol ----
package myapp

use file:lib.format     // discovers lib/format.sol

func main() -> int {
    format.greet("world")   // qualified access via package name
    return 0
}
```

```
// ---- lib/format.sol ----
package format               // <-- this defines the access prefix

func greet(name: string) -> string {
    return "Hello, " .. name
}
```

Rules:
- Unqualified calls (`greet()`) only resolve to functions in the **same package**
- Cross-package calls must use qualified syntax: `package.function()`
- Functions in the same file can always call each other unqualified
- Two files sharing the same `package` name can call each other unqualified

> **Note**: On Windows, `~` expands to `%USERPROFILE%`.

### Variables

Variables are declared with a name, type annotation, and optional initializer:

```
count: int = 42
name: string = "Solvik"
pi: float = 3.14159
active: bool = true
initial: char = 'A'
small: byte = 255
big: int = 1000000
nullable: string? = null
```

Reassignment uses `=`:

```
count = count + 1
```

### Functions

Functions are declared with `func`, parameters with types, and a return type:

```
func add(a: int, b: int) -> int {
    return a + b
}

func greet() -> string {
    return "Hello!"
}

func logMessage(level: string, message: string) -> void {
    println("[" .. level .. "] " .. message)
}
```

Functions support recursion:

```
func factorial(n: int) -> int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}
```

### Blocks

All body-bearing constructs — functions, `if`, `else`, `while`, `for`, `try`, `catch`, `finally`, `switch` cases, and standalone scope blocks — require explicit brace-delimited blocks `{ ... }`. Single-statement bodies without braces are not valid syntax.

```
// Valid — braces are required everywhere
if ready {
    start()
}

switch status {
    case "ok": {
        proceed()
    }
    default: {
        abort()
    }
}

// Invalid — single-statement body without braces
if ready start()
```

This rule applies consistently across all constructs to make block structure explicit and unambiguous.

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
    case 200: {
        return "OK"
    }

    case 404: {
        return "Not Found"
    }

    case 500: {
        return "Internal Server Error"
    }

    default: {
        return "Unknown"
    }
}
```

Case bodies must be wrapped in `{ }` — consistent with all other body-bearing constructs in the language.

### Regex Matching

The `regex()` built-in compiles a regular expression and returns a first-class regex value. When used in a switch case expression, the switch value is matched against the pattern:

```
switch entry {
    case regex(r"^ERROR\s+\[\d+\]:"): {
        return "structured-error"
    }

    case regex(r"^WARN\s+"): {
        return "warning"
    }

    default: {
        return "unmatched"
    }
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

Map literals use curly braces `{ }` with key:value entries:

```
config: Map<string, string> = {
    "host":   "localhost",
    "port":   "8080",
    "scheme": "http",
}
host: string = config["host"]
```

Like all brace-delimited constructs, map literals require the enclosing `{ }`.
In expression context (e.g., assignments, return values, function arguments)
they are unambiguous — the parser distinguishes map literals from blocks
by position.

### Numeric Literals with Underscores

Numeric literals support underscores as digit separators (Java-style). Underscores can appear between digits in integer literals, floating-point literals, and hexadecimal literals. They are ignored at parse time and do not affect the value.

```
// Integer literals
million: int = 1_000_000
creditCard: int = 1234_5678_9012_3456

// Floating-point literals
pi: float = 3.14_15_92

// Hexadecimal literals
mask: int = 0xFF_FF_FF_00
rgb: int = 0x00_FF_00
```

Underscores are not allowed:
- At the start of a number (`_100` is an identifier)
- At the end of a number (`100_` is a parse error)
- Adjacent to a decimal point (`1_.0`, `1._0`)
- In exponent parts (`1e1_0`)

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

### Variadic Functions

A variadic parameter accepts zero or more arguments of the same type, packed into a `List<T>`:

```solvik
func sum(values: ...int) -> int {
    mut total: int = 0
    for v in values {
        total = total + v
    }
    return total
}
```

**Call with inline arguments:**

```solvik
sum()              // values = []
sum(5)             // values = [5]
sum(1, 2, 3)       // values = [1, 2, 3]
```

**Mixed fixed and variadic:**

```solvik
func greet(greeting: string, names: ...string) -> void {
    for name in names {
        println(greeting .. ", " .. name)
    }
}

greet("Hello", "Alice", "Bob")
```

**Spread an existing list:**

```solvik
names: List<string> = ["Alice", "Bob", "Charlie"]
greet("Hi", names...)
```

**Constraints:**
- Only one variadic parameter per function
- It must be the last parameter
- Nullable variadic parameters are not allowed
- The variadic parameter has type `List<T>` inside the function body

### Null Coalescing

The `??` operator provides a default value when a nullable expression is `null`:

```
name: string? = null
display: string = name ?? "Guest"
```

### Enumerations

Enum types let you define a set of named integer constants as a distinct type:

```
package example

enum Color {
    Red,
    Green,
    Blue,
}

enum HttpStatus {
    OK = 200,
    NotFound = 404,
    InternalError = 500,
}
```

**Auto-assigned values:** Variants without explicit values start at 0 and increment:

```
enum Day { Mon, Tue, Wed, Thu, Fri, Sat, Sun }
// Mon=0, Tue=1, ..., Sun=6
```

**Explicit values:** You can assign specific integer values:

```
enum Permissions { Read = 4, Write = 2, Execute = 1 }
```

**Mixed values:** Explicit and auto-assigned values can be mixed. Auto-assignment continues from the last explicit value:

```
enum ErrorCode {
    Unknown = 0,
    NotFound,      // = 1
    Timeout,       // = 2
    AuthFailure = 100,
    RateLimited,   // = 101
}
```

**Trailing commas:** A comma after the final variant is optional:

```
enum Size {
    Small,
    Medium,
    Large,        // trailing comma — ok
}
```

**Type safety:** Enums are distinct types. Assigning one enum type to another is a compile error:

```
status: HttpStatus = HttpStatus.OK      // ok
color: Color = HttpStatus.OK            // compile error
```

**Enum values in switch:** Enums work naturally with switch:

```
switch color {
    case Color.Red: {
        println("red")
    }
    case Color.Green: {
        println("green")
    }
    case Color.Blue: {
        println("blue")
    }
    default: {
        println("unknown")
    }
}
```

**Integer interop:** Enum values can be compared to integers and used where integers are expected:

```
count: int = Color.Red    // ok, implicit conversion
if Color.Green == 1 { }   // ok
```

### Structs

Structs are user-defined data aggregates with named fields and associated methods. No inheritance, no subtyping, no dynamic dispatch.

**Declaration:**

```solvik
struct Point {
    pub mut x: int,
    pub mut y: int,
}
```

Fields are declared `name: Type`. Trailing commas are optional. Fields are **immutable by default** — use `mut` to allow reassignment after construction.

**Visibility:** Fields and methods are **private by default**. Use `pub` to make them accessible outside the struct:

```solvik
struct Account {
    pub name: string,          // readable from outside
    pub mut balance: int,      // readable and writable from outside
    secret: string,            // private — only accessible inside Account methods

    pub func deposit(amount: int) -> void {
        balance = balance + amount
    }

    func validate() -> bool {  // private method
        return balance >= 0
    }
}
```

Inside a struct's own methods, all fields and methods (public and private) are accessible.

**Methods:** Methods are defined inside the struct block. Fields are implicitly in scope — no `self` or `this` keyword:

```solvik
struct Point {
    pub mut x: int,
    pub mut y: int,

    pub func distance() -> float {
        sqSum: float = x * x + y * y
        return math.sqrt(sqSum)
    }

    pub func move(dx: int, dy: int) -> void {
        x = x + dx
        y = y + dy
    }

    pub func describe() -> string {
        return "Point(" .. x .. ", " .. y .. ")"
    }
}
```

Methods can call other methods of the same struct unqualified:

```solvik
struct Point {
    pub mut x: int,
    pub mut y: int,

    pub func distance() -> float { ... }

    pub func describe() -> string {
        return "Point(distance=" .. distance() .. ")"
    }
}
```

**Mutability at call sites:** Calling a method that mutates fields requires the receiver to be `mut`:

```solvik
mut p: Point = Point(3, 4)
p.move(10, 20)        // ok — p is mutable

q: Point = Point(1, 2)
q.move(10, 20)        // compile error — q is immutable
```

**Construction:** Positional syntax, field order matches declaration:

```solvik
p: Point = Point(3, 4)
```

**Field access:** Uses dot notation:

```solvik
x: int = p.x
cfg.port = 9090       // requires cfg to be mut if port is pub mut
```

**Empty structs:** Valid with no fields or methods:

```solvik
struct Empty {}
e: Empty = Empty()
```

**Struct equality:** Two structs are equal if all fields are equal (recursively). Structs can be compared with `==` and `!=`.

### Traits

Traits are abstract behavioral contracts using Go-style structural typing. A struct satisfies a trait implicitly if it has all required methods with matching signatures. No `implements` keyword is needed.

```sol
trait Drawable {
    func draw() -> string
    func area() -> float
}

struct Circle {
    pub mut radius: float,

    pub func draw() -> string {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub func area() -> float {
        return 3.14159 * radius * radius
    }
}
```

`Circle` satisfies `Drawable` because it has `draw() -> string` and `area() -> float`.

**Trait as parameter type:**

```sol
func printShape(shape: Drawable) -> void {
    println(shape.draw() .. " area=" .. string(shape.area()))
}
```

**Trait as variable type and multiple traits:**

```sol
mut current: Drawable = Circle(5.0)
current = Rectangle(3.0, 4.0)  // both satisfy Drawable
```

A struct can satisfy multiple traits simultaneously. If two traits have a method with the same name and signature, one method serves both contracts.

### Exception Handling: try / catch / finally / throw

Solvik supports deterministic exception handling with `try`, `catch`, `finally`, and `throw`.

**Exception Model:** Solvik has a first-class `exception` type with two read-only fields:
- `.message` — the exception message string
- `.trace` — a formatted `.sol` stack trace captured at the point the exception was created

String values auto-convert to `exception` when used with `throw`, assigned to `exception` variables, or returned from functions returning `exception`. The `.trace` is captured at the conversion point.

**Syntax:**

```solvik
try {
    riskyOperation()
} catch (e: exception) {
    println("failed: " .. e.message)
    println(e.trace)
} finally {
    cleanup()
}
```

Valid forms:

```solvik
// try + catch (no finally)
try {
    operation()
} catch (e: exception) {
    println(e.message)
}

// try + finally (no catch)
try {
    operation()
} finally {
    cleanup()
}

// try + catch + finally
try {
    operation()
} catch (e: exception) {
    println(e.message)
} finally {
    cleanup()
}
```

A `try` statement must contain at least one of `catch` or `finally`. The `catch` clause (if present) must appear before `finally`.

**Throwing:**

```solvik
throw "operation failed"           // string auto-converts to exception
throw exceptionVariable            // throw an existing exception value
```

The thrown expression must have non-nullable `string` or `exception` type.

**Exception Variables:**

```solvik
failure: exception = "something failed"   // string auto-converts, trace captured here
println(failure.message)                    // "something failed"
println(failure.trace)                      // formatted stack trace
```

**Catchable Runtime Faults:**

- Division or modulo by zero
- Invalid list or string index
- Invalid map access
- Explicit `throw`
- Failures raised by native functions (file, process, etc.)

**Propagation Rules:**

| Scenario | Behavior |
|----------|----------|
| `try` completes normally | Skip `catch` (if present), execute `finally`, continue after statement |
| Exception from `try` with `catch` | Transfer to `catch`, bind exception value, execute `catch`, execute `finally`, continue normally |
| Exception from `try` without `catch` | Execute `finally`, rethrow the original exception |
| Exception from `catch` | Execute `finally`, propagate the catch exception |
| Exception from `finally` | Supersedes any pending exception or control-flow transfer |

**finally Guarantees:**

A `finally` block executes before control leaves the protected region due to:
- `return`
- `break`
- `continue`
- an exception

After `finally` completes normally, the original control transfer resumes.

```solvik
func example() -> int {
    try {
        return 10        // ← prints "cleanup" first, then returns 10
    } finally {
        println("cleanup")
    }
}
```

A control transfer initiated by `finally` (return, break, continue, throw) supersedes a pending transfer.

**Uncaught Exceptions:**

An uncaught exception terminates execution with a runtime error showing:
- The internal error code
- The exception message
- The source position of the original throw or runtime fault
- The Solvik call stack

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
// Single-line comments

/* Block comments */

/* Nested /* block */ comments are supported */
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
| `cmd/solvik` | 208 | CLI tool with flags (`--check`, `--version`, `--verbose`) |
| `internal/lexer` | 1,274 | Tokenization — keywords, literals, operators, raw strings, comments |
| `internal/parser` | 2,355 | Recursive-descent parser with error recovery |
| `internal/ast` | 613 | AST node definitions for all program constructs |
| `internal/resolver` | 858 | Scope resolution, variable declaration validation |
| `internal/checker` | 2,113 | Type checking, type inference, arity validation |
| `internal/compiler` | 2,254 | Bytecode generation from typed AST |
| `internal/bytecode` | 897 | Bytecode instruction set, serialization, disassembly |
| `internal/vm` | 1,916 | Stack-based virtual machine — execution engine |
| `internal/native` | 854 | Built-in function implementations (core, string, math, env, file, process, time) |
| `internal/runtime` | 430 | Compilation pipeline orchestration, multi-file support |
| `internal/types` | 651 | Type system — type representations, compatibility |
| `internal/symbol` | 110 | Symbol table for scope management |
| `internal/source` | 180 | Source position tracking, span management |
| `internal/diagnostic` | 391 | Error and diagnostic reporting |
| `internal/verifier` | 392 | Bytecode verification — stack balance, operand validation |

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
./dist/solvik test/<name>.sol
```

Integration test scripts are located in `test/`:

| Script | Description |
|--------|-------------|
| `hello.sol` | Basic variable assignment and printing |
| `for_test.sol` | For-in loop iteration |
| `full_test.sol` | Combined features — functions, lists, while, if, structs, traits |
| `list_test.sol` | List literal construction |
| `semicolon.sol` | Semicolon statement termination |
| `simple_sum.sol` | List iteration and accumulation |
| `string_test.sol` | String operations |
| `switch_test.sol` | Switch/case with exact matching |
| `trailing_comma.sol` | Trailing comma in call arguments |
| `byte_test.sol` | Byte type operations |
| `enum_test.sol` | Enum declarations and usage |
| `list_iteration.sol` | List iteration patterns |
| `map_test.sol` | Map operations |
| `multi_return_test.sol` | Multiple return values |
| `struct_test.sol` | Struct construction and field access |
| `struct_method.sol` | Struct methods and mutability |
| `struct_map_key.sol` | Structs as map keys |
| `struct_nullable.sol` | Nullable struct types |
| `struct_visibility.sol` | Public/private field and method visibility |
| `trait_test.sol` | Trait declaration, satisfaction, and dispatch |
| `trait_multi.sol` | Struct satisfying multiple traits |
| `trait_collection.sol` | Trait-typed collections with heterogeneous structs |
| `trait_nullable.sol` | Nullable trait types |
| `try_catch_finally.sol` | Exception handling |
| `underscore_test.sol` | Underscores in numeric literals |
| `use_test.sol` | Multi-file `use` dependencies |
| `variadic_test.sol` | Variadic function parameters |

### Complete Language Example

```bash
./dist/solvik example.sol
```

`example.sol` is a 1,200+ line comprehensive demonstration covering every supported language construct and built-in function.

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

## Releases

### Prerequisites

- [GoReleaser](https://goreleaser.com/install/) v2 or later

### Local Snapshot Build

Build all supported platform binaries locally without publishing:

```bash
goreleaser release --snapshot --clean
```

Output appears under `./dist/`:

```
dist/
├── solvik_<version>_linux_x86_64.tar.gz
├── solvik_<version>_linux_arm64.tar.gz
├── solvik_<version>_darwin_x86_64.tar.gz
├── solvik_<version>_darwin_arm64.tar.gz
├── solvik_<version>_windows_x86_64.zip
├── solvik_<version>_windows_arm64.zip
├── checksums.txt
├── solvik_linux_amd64_v1/
│   └── solvik
├── solvik_darwin_amd64_v1/
│   └── solvik
...
```

Snapshots use a version string like `0.0.0-SNAPSHOT-<commit>`.

### Tagged Release

1. Tag the release:

```bash
git tag v1.2.3
git push origin v1.2.3
```

2. Build and publish to GitHub Releases:

```bash
GITHUB_TOKEN=<your-token> goreleaser release --clean
```

GoReleaser reads `GITHUB_TOKEN` from the environment. The token needs `repo` scope.

The binary version is injected at build time via `ldflags -X main.Version=<tag>`.

### Supported Build Targets

| OS | Architecture | Format |
|----|-------------|--------|
| Linux | AMD64 | `.tar.gz` |
| Linux | ARM64 | `.tar.gz` |
| macOS | AMD64 | `.tar.gz` |
| macOS | ARM64 | `.tar.gz` |
| Windows | AMD64 | `.zip` |
| Windows | ARM64 | `.zip` |

All builds use `CGO_ENABLED=0` for static cross-compilation.

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
- [x] Exception handling (`try`/`catch`/`finally`/`throw`)
- [x] Nullable types and null-coalescing (`T?`, `??`)
- [x] Multiple return values
- [x] Enum types
- [x] Structs (fields, methods, `pub` visibility)
- [x] Traits (Go-style structural typing, dynamic dispatch via fat pointers)
- [x] Variadic functions (`...T`)
- [ ] Generics / parametric polymorphism
- [ ] Default trait method implementations
- [ ] Generic traits (`trait Sortable<T>`)
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
