# Solvik Sublime Text Syntax

Syntax highlighting for the [Solvik](https://github.com/dhoard/solvik) programming language (`.sol` files).

## Installation

### Manual

Copy `Solvik.sublime-syntax` into your Sublime Text packages directory:

- **Linux / macOS**: `~/.config/sublime-text/Packages/User/`
- **Windows**: `%APPDATA%\Sublime Text\Packages\User\`

### Via Package Control (if published)

Install the `Solvik` package from Package Control.

## Features

| Category | Highlights |
|----------|-----------|
| Keywords | `package`, `use`, `class`, `interface`, `enum`, `extends`, `implements`, `delegate`, `to`, `if`, `else`, `while`, `for`, `in`, `let`, `return`, `break`, `continue`, `match`, `try`, `catch`, `finally`, `throw`, `static`, `mutable`, `public`, `self` |
| Types | `Boolean`, `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`, `BigInteger`, `BigDecimal`, `Char`, `String`, `Void`, `Object`, `Self`, `Exception`, `Regex`, `List`, `Map`, `Stack`, `Set`, `Thread`, `Mutex`, `Semaphore`, `Process` |
| Namespaces | `Math`, `Type`, `Base64`, `Hash`, `Json`, `Time`, `Random`, `File`, `Test` |
| Constants | `true`, `false`, `null` |
| Strings | Double-quoted with escape sequences, raw strings (`r"..."` through `r######"..."######`) |
| Characters | Single-quoted char literals with escape support |
| Numbers | Decimal, hex (`0x`), binary (`0b`), octal (`0o`), underscores, scientific notation, `f` float suffix |
| Comments | Line (`//`), nested block (`/* ... /* nested */ ... */`) |
| Operators | Arithmetic, comparison, logical, string concatenation (`..`), null coalescing (`??`), variadic (`...`), nullable (`?`) |
| Declarations | Module namespaces, `use` directives, class/interface/enum names are highlighted at the declaration site |
| Modifiers | `public` visibility, `mutable` fields, `let` locals, `static` |
