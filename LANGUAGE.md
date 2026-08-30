# Solvik Language Specification

This document is the normative description of the stable, user-visible Solvik
language. `README.md` is an introduction. `solvik.py` is the executable semantic
reference. When optimized implementations disagree with the reference, the Python
behavior is authoritative unless this specification and its conformance tests are
deliberately changed together.

## Source files and termination

Every source file must begin with a `package` declaration naming the module:

```solvik
package example
```

A file then consists of `use` directives and top-level declarations
(structs, traits, enums, and functions).

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
and raw-delimited variants). Escaped strings support `\n`, `\t`, `\r`, `\\`,
`\"`, `\'`, `\0`, `\xHH`, `\uHHHH`, and `\UHHHHHHHH`; unknown escapes and
non-hexadecimal `\x`/`\u`/`\U` digits are errors. Char literals are UTF-8
runes and support the same escape set. Numeric underscores are valid only between actual
digits: `1_000`, `0xFF_FF`, and `1.5e1_0` are valid. Leading, trailing,
consecutive, and delimiter-adjacent underscores are invalid. Integer literals
support `0x` (hexadecimal), `0b` (binary), and `0o` (octal) prefixes.

## Types

Solvik has a uniform value-type model. The intrinsic value types are `bool`,
`byte`, `int`, `float`, `char`, and `string`; the collection value types are
`list<T>`, `map<K,V>`, and `stack<T>`. Intrinsic representation is an
implementation detail: built-in values participate in methods, structural
traits, and generic constraints under the same rules as user-defined structs.
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

A nullable value may not be used as a value directly: method calls, indexing,
member access, arithmetic, and `..` concatenation on a statically nullable
operand raise a catchable `null reference` exception when the value is null
at runtime (code E031, in the same family as division by zero and index
out of range). The unwrap mechanisms are the null-coalescing operator (`A ??
B`) and null-comparison narrowing (`if x != null { ... }`), inside which the
variable has its non-nullable type. Equality with `null` never raises.

The `any` type accepts any value. Downcasting an `any` value to a concrete
type (declaration initializer, assignment, function argument, return value, or
collection element) is checked at runtime: a mismatch raises a catchable
`type mismatch` exception (code E066). `isType(value, "type")` is the guard
idiom. Nullable targets accept `null`; collection element types and trait
targets are not checked.

Nullable suffixes compose at every level, for example
`list<map<string, int?>?>`. Nested generic closers do not require whitespace:
`list<list<int>>`.

Assignments require compatible types. Numeric widening is `byte` to `int` to
`float`. Enums are opaque: enum values are not implicitly convertible to or
from integers, and `int(enumValue)` is the explicit conversion to an integer.
Enum values are 64-bit integer constants. The conversion functions `int`,
`float`, and `byte` accept a numeric value (converting numerically, with `int`
truncating floats) or a parseable string.

## Generic declarations and constraints

Structs, traits, and functions may declare type parameters after their name.
Type arguments on function calls and struct literals are inferred from values;
type annotations carry concrete generic arguments. A type parameter may have
one or more structural trait constraints separated by `&`:

```solvik
func identity<T>(value: T) -> T {
    return value
}

func render<T: Stringable>(value: T) -> string {
    return value.string()
}

struct Box<T> {
    pub value: T
}
```

Generic structs have value semantics like all other structs. A concrete type
must satisfy every declared constraint. Solvik does not use nominal
`implements` declarations.

The core traits below are predefined and may be used as constraints without a
source declaration:

- `Stringable`: `string() -> string`
- `Equatable`: `equals(other: any) -> bool`
- `Comparable`: `compare(other: any) -> int`
- `Hashable`: `hash() -> int`
- `Countable`: `len() -> int`
- `Iterable<T>`: `iterator() -> list<T>`
- `Collection<T>`: `len()`, `isEmpty()`, `contains(T)`, and `iterator()`

Built-in values expose these capabilities structurally. For example, numeric
and string values are `Stringable`; strings, lists, maps, and stacks are
`Countable`; lists and stacks satisfy `Collection<T>`; and strings,
lists, maps (over keys), and stacks are iterable.

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

The entry function `main` takes no parameters and returns `int` or nothing.
When run through the CLI, its returned integer is the process exit code
(`0` for success).

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

Traits use structural typing. A public user-defined method or intrinsic
built-in method satisfies a trait method only when its parameters, result, and
receiver mutability match. No explicit `implements` declaration exists.
Built-in and user-defined values therefore participate in the same behavioral
abstractions.

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

Parenthesized map bindings are not part of the language. Lists and stacks use
one binding and iterate over their values. Strings iterate over characters
and support index access returning a `char`. One-binding iteration is defined
structurally by an `iterator() -> list<T>` capability; user-defined structs can
participate by providing a public method with that signature. Maps preserve
their special two-binding key/value form and expose keys through one-binding
iteration:

```solvik
first: char = "hello"[0]
for c in "hello" {
    // c is a char
}
```

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

Case expressions are type-checked against the switch expression: a case whose
type is not assignable to the switch type is a compile error (it could never
match). Two exceptions are exempt: `regex(...)` cases (pattern matching) and
`case null` on a nullable switch type. Cases of a wider numeric type match a
narrower switch value (`case 1` matches a `float` switch value `1.0`).

## Dependencies

`use` is the only source dependency directive. File dependencies use
`use file:path`; URL dependencies use `use url:value`, with optional
`checksum:sha256:<64-hex-digits>` and `insecure:true|false` flags. URL and
checksum values may be unquoted, quoted, or raw strings as required by their
contents. `import` is an ordinary identifier and is not dependency syntax.

Functions are accessed across packages with qualified syntax
(`package.function()`); unqualified calls resolve within the same package, and
files sharing a package name call each other unqualified. Enums, structs, and
traits declared in any file of a package are usable in every file of that
package, unqualified. Cross-package type usage is not supported; types live in
the package that declares them.

Only the entry file may define `main`. A library file loaded via `use` that
declares `main` is a compile error, so the program entry point never depends
on file order.

## Expressions and precedence

From tightest to loosest: calls/indexing/member access; unary `!`, `-`, `~`;
`* / %`; `+ -`; `..`; `<< >>`; `&`; `^`; `|`; comparisons; equality;
`&&`; `||`; `??`; assignment `=`.

`..` is the only string-concatenation operator. Arithmetic binds before
concatenation: `"total=" .. a + b` means `"total=" .. (a + b)`.

`%` applies to floats as well as integers: `5.5 % 2.0` is `1.5`.

Characters order by Unicode code point (`'a' < 'b'`, `'z' > 'é'`). Mixed
char/numeric comparisons are rejected; use the explicit `int(c)` conversion to
compare a character with a number.

## Standard library behavior

String and collection functions follow Go standard-library semantics:
`charAt` raises a catchable runtime error on an out-of-range index while
`substring` clamps its bounds to the string length; `indexOf` returns `-1`
when a substring is not found; `typeOf` reports struct type names lowercased.

## Conformance tests

Executable syntax fixtures live under `test/conformance/`. Valid fixtures must
compile without diagnostics. Invalid fixtures declare an expected diagnostic
code in their first comment. Python reference fixtures live under
`test/reference/`. `tools/parity.py` uses Python as the semantic oracle and can
compare Go and Rust observable output, exit status, and conformance diagnostics
as those implementations are brought to parity. `./build.sh` runs the Python
reference checks before the optimized toolchains.
