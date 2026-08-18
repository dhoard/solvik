# Solvik Language Specification

This document is the normative description of the stable, user-visible Solvik
language. `README.md` is an introduction; when the two differ, this document
and the executable conformance tests are authoritative.

## Source files and termination

A source file consists of declarations and `use` directives. An optional
package declaration may appear first:

```solvik
package example
```

Newline terminates a statement unless the grammar requires continuation, such
as after a binary operator, comma, or unmatched opening delimiter. A semicolon
always terminates a statement and allows multiple statements on one line.
Adjacent complete statements without a newline or semicolon are invalid.

## Lexical rules

Identifiers start with a letter or `_` and may contain letters, digits, and `_`.
Keywords include `package`, `use`, `struct`, `trait`, `enum`, `func`, `mut`,
`pub`, `if`, `else`, `while`, `for`, `in`, `switch`, `case`, `default`, `try`,
`catch`, `finally`, `throw`, `return`, `break`, `continue`, `true`, `false`,
and `null`. `import` is not a keyword. Comments are `//` line comments and
nested `/* ... */` block comments.

Strings may be ordinary escaped strings (`"..."`) or raw strings (`r"..."`
and raw-delimited variants). Numeric underscores are valid only between actual
digits: `1_000`, `0xFF_FF`, and `1.5e1_0` are valid. Leading, trailing,
consecutive, and delimiter-adjacent underscores are invalid.

## Types

Primitive types are `bool`, `byte`, `int`, `float`, `char`, and `string`.
`void` is an internal return type; source functions use no arrow for void
results. User-defined types include structs, traits, and enums. Collection
types are recursive:

```text
type := primary [ "?" ]
primary := bool | byte | int | float | char | string
         | identifier
         | list "<" type ">"
         | stack "<" type ">"
         | map "<" type "," type ">"
```

Nullable suffixes compose at every level, for example
`list<map<string, int?>?>`. Nested generic closers do not require whitespace:
`list<list<int>>`.

Assignments require compatible types. Numeric widening is `byte` to `int` to
`float`. Enums are opaque: enum values are not implicitly convertible to or
from integers, and `int(enumValue)` is the explicit conversion to an integer.

## Declarations and functions

Variables use `name: type`, and are immutable unless declared with `mut`:

```solvik
value: int = 10
mut total: int = 0
```

Functions omit the arrow when they return no value. A value-returning function
has exactly one return type:

```solvik
func log(message: string) {
    println(message)
}

func add(a: int, b: int) -> int {
    return a + b
}
```

Multiple return types, comma-separated return expressions, and multi-target
assignment are invalid. Use a named struct for a multi-value result.

## Structs and methods

Struct construction uses named fields:

```solvik
struct Point {
    pub mut x: int
    pub mut y: int
}

p: Point = Point { x: 3, y: 4 }
```

Fields and methods are private unless marked `pub`. Fields are implicitly in
scope in methods, and `self` is an explicit receiver alias:

```solvik
struct Counter {
    pub mut value: int

    pub func get() -> int {
        return self.value
    }

    pub mut func increment() {
        self.value = self.value + 1
    }
}
```

Methods are non-mutating by default. A normal `func` cannot assign receiver
fields and may be called on immutable or mutable receivers. A `mut func` may
assign fields declared `mut` and requires a mutable receiver. Traits preserve
this receiver-mutability contract.

## Traits and enums

Traits use structural typing. A public struct method satisfies a trait method
only when its parameters, result, and receiver mutability match.

Enum values compare naturally with values from the same enum and may be used
in switches and as map keys. Cross-enum comparisons and enum/integer
comparisons are errors unless both sides are explicitly converted to `int`.

## Collections and iteration

The canonical count operation is `.len()` for lists, maps, stacks, and strings:

```solvik
items: list<int> = [1, 2, 3]
names: map<string, int> = { "one": 1 }
s: stack<int> = stack()

a: int = items.len()
b: int = names.len()
c: int = s.len()
d: int = "hello".len()
```

A one-variable map loop iterates over keys. The canonical entry form has two
unparenthesized bindings:

```solvik
for key, value in names {
    println(key .. "=" .. value)
}
```

Parenthesized map bindings are not part of the language. Lists, strings, and
stacks use one binding and iterate over their values.

## Switch

Cases use a brace directly, without a colon. Cases are first-match and never
fall through:

```solvik
switch code {
    case 200 {
        return "ok"
    }
    default {
        return "unknown"
    }
}
```

## Dependencies

`use` is the only source dependency directive. File dependencies use
`use file:path`; URL dependencies use `use url:value`, with optional
`checksum:sha256:<64-hex-digits>` and `insecure:true|false` flags. URL and
checksum values may be unquoted, quoted, or raw strings as required by their
contents. `import` is an ordinary identifier and is not dependency syntax.

## Expressions and precedence

From tightest to loosest: calls/indexing/member access; unary `!`, `-`, `~`;
`* / %`; `+ -`; `..`; `<< >>`; `&`; `^`; `|`; comparisons; equality;
`&&`; `||`; `??`; assignment `=`.

`..` is the only string-concatenation operator. Arithmetic binds before
concatenation: `"total=" .. a + b` means `"total=" .. (a + b)`.

## Conformance tests

Executable syntax fixtures live under `test/conformance/`. Valid fixtures must
compile without diagnostics. Invalid fixtures declare an expected diagnostic
code in their first comment. `internal/conformance` walks both directories
during `go test ./...`, which is also run by `./build.sh`.
