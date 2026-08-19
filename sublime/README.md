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
| Keywords | `package`, `use`, `func`, `if`, `else`, `while`, `for`, `in`, `return`, `break`, `continue`, `switch`, `case`, `default`, `try`, `catch`, `finally`, `throw`, `enum`, `struct`, `trait`, `pub`, `mut` |
| Types | `bool`, `byte`, `int`, `float`, `char`, `string`, `void`, `any`, `exception`, `list`, `map`, `stack` |
| Constants | `true`, `false`, `null` |
| Strings | Double-quoted with escape sequences, raw strings (`r"..."` through `r######"..."######`) |
| Characters | Single-quoted char literals with escape support |
| Numbers | Decimal, hex (`0x`), binary (`0b`), octal (`0o`), underscores, scientific notation, `f` float suffix |
| Comments | Line (`//`), nested block (`/* ... /* nested */ ... */`) |
| Operators | Arithmetic, comparison, logical, bitwise, string concatenation (`..`), null coalescing (`??`), return arrow (`->`), variadic (`...`), nullable (`?`) |
| Declarations | Package namespaces, `use` directives, enum/struct/trait names, and function/method names are highlighted at the declaration site |
| Modifiers | `pub` and `mut` visibility/mutability modifiers |
