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
| Keywords | `func`, `if`, `else`, `while`, `for`, `in`, `return`, `break`, `continue`, `switch`, `case`, `default`, `try`, `catch`, `finally`, `throw`, `mut`, `enum`, `package`, `use` |
| Types | `bool`, `byte`, `int`, `long`, `float`, `double`, `char`, `string`, `void`, `List`, `Map`, `exception` |
| Constants | `true`, `false`, `null` |
| Strings | Double-quoted with escape sequences, raw strings (`r"..."`, `r#"..."#`, `r##"..."##`) |
| Characters | Single-quoted char literals with escape support |
| Numbers | Decimal, hex (`0x`), scientific notation, type suffixes (`L`, `f`, `d`) |
| Comments | Line (`//`), nested block (`/* ... /* nested */ ... */`) |
| Operators | All arithmetic, comparison, logical, bitwise, null coalescing (`??`), arrow (`->`) |
| Declarations | Function and enum names are highlighted at the declaration site |
| Namespaces | Package declarations are highlighted |
