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
Adjacent complete statements without a newline or semicolon are invalid
(P078); the same rule applies to top-level declarations and struct members.
A statement continues across newlines while the next token continues the
expression (binary operators, postfix operators, commas, or opening
delimiters); operators at the start of the next line also continue the
expression.

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
         | func "<" type ("," type)* ">"
```

Function types (`func<P1, ..., Pn, R>`) are value types for callable values;
see "Functions and closures" below. `void` may appear only as the final
(function return) element of a function type.

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

### Runtime type introspection

`typeOf(value)` and `isType(value, "type")` are top-level core built-ins, not
methods on values. `typeOf` returns the canonical runtime type tag:
`null`, `bool`, `byte`, `int`, `float`, `char`, `string`, `list`, `map`,
`stack`, `regex`, `exception`, or `function`; user-defined struct and enum
values return their local type name in lowercase. `isType` compares the value's
runtime tag with the supplied type-name string. Programs should use the
lowercase canonical names, for example:

```solvik
value: any = 42
name: string = typeOf(value)       // "int"
if isType(value, "int") {
    n: int = value
}
```

Runtime introspection does not replace static typing, method resolution, or
structural trait satisfaction. A value's declared trait type determines trait
dispatch; `typeOf` reports the concrete runtime value type. There is no
`kindOf` built-in.

Nullable suffixes compose at every level, for example
`list<map<string, int?>?>`. Nested generic closers do not require whitespace:
`list<list<int>>`.

Assignments require compatible types. Numeric widening is `byte` to `int` to
`float`. Enums are opaque: enum values are not implicitly convertible to or
from integers, and `int(enumValue)` is the explicit conversion to an integer.
Integer-backed enum values are 64-bit integer constants; payload (algebraic)
values carry data instead (see "Algebraic enums and pattern matching"). The
conversion functions `int`,
`float`, and `byte` accept a numeric value (converting numerically, with `int`
truncating floats) or a parseable string; failed conversions raise a
catchable `conversion failed` exception (E073). `bool` accepts booleans and
the strings `"true"`/`"false"` (case-insensitive).

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
`implements` declarations. Type parameter names must be unique per declaration,
and a method type parameter may not shadow a struct type parameter (C099).
Constraint references must name traits with matching generic arity (C096).

### Explicit type arguments

Generic function calls, generic method calls, and struct literals may state
their type arguments explicitly, all or none of them at once:

```solvik
a: int = identity<int>(42)
b: Box<int> = Box<int> { value: 9 }
```

Explicit arguments pin parameters that values cannot infer and are checked:
the argument count must match the declaration (C096 static, E067 runtime) and
values must still be assignable to the instantiated parameter types (E066).
Inside a generic declaration, explicit arguments may reference an enclosing
type parameter (`Box<T> { ... }` inside `func wrap<T>(...)`); they resolve to
the caller's instantiation.

Because `<` is also the comparison operator, a `<...>` list in expression
position is treated as type arguments only when it parses as one and is
immediately followed by a call `(` or, for a simple type name in a place where
struct literals are allowed, a literal `{`. Otherwise it is a comparison.

### Inference and null values

Type arguments are inferred from non-null argument values, struct literal
field values, and generic collection elements. A `null` value carries no type
evidence and never determines a type parameter: `identity(null)` is rejected
with E067 unless the type argument is supplied or inferable another way.
When a null argument or field expression is a simple name with a declared
nullable type (`n: int? = null; identity(n)`), that declared type participates
in inference.

Struct literals are also seeded by an expected instantiation: a declaration,
assignment to a declared target, or `return` that names a concrete
instantiation supplies the type arguments, so nullable fields can be `null`
without restating the type:

```solvik
b: Box<int?> = Box { value: null }
```

If a type parameter cannot be inferred (and is not fixed by explicit
arguments, expected types, or declared-type evidence), the program fails with
E067 before the value is used.

Generic instantiations are invariant: `Box<int>` is not assignable to
`Box<int?>`. Construct the desired instantiation explicitly, for example
`Box<int?> { value: b.value }`.

### Generic arity and recursive types

Annotations must instantiate generic types exactly: `list<T>`, `stack<T>`,
and `map<K,V>` require their full argument lists, and a generic struct must
be fully instantiated while a non-generic struct may not take arguments
(C096 static; E067 at runtime as defense).

Struct fields may recurse only through a nullable type or a collection
(`list`, `map`, `stack`), which are indirect. A direct non-nullable cycle such
as `struct Node { next: Node }` is rejected (C097); `Node<T>?` chains are the
idiomatic linked shape:

```solvik
struct Node<T> {
    pub value: T
    pub next: Node<T>?
}
```

Struct literal fields are checked against the instantiated field types when
the instantiation is known (C098).

### Constraint checking and trait-argument inference

The core traits below are predefined and may be used as constraints without a
source declaration:

- `Stringable`: `string() -> string`
- `Equatable`: `equals(other: any) -> bool`
- `Comparable`: `compare(other: any) -> int`
- `Hashable`: `hash() -> int`
- `Countable`: `len() -> int`
- `Iterable<T>`: `iterator() -> list<T>`
- `Collection<T>`: `len()`, `isEmpty()`, `contains(T)`, and `iterator()`

A declared constraint is validated when the instantiation is known: failures
are C095 at validation time and E067 at runtime. A function type parameter
that appears only as a trait argument is inferred from the actual type's
structural method signatures, so both forms below work with `Pair<int>` and
`list<int>` arguments:

```solvik
func total<T, C: Iterable<T>>(items: C) -> T
func count<C: Iterable<int>>(items: C) -> int
```

Built-in values expose these capabilities structurally. For example, numeric,
boolean, char, string, and collection values are `Stringable`; strings, lists,
maps, and stacks are `Countable`; lists and stacks satisfy `Collection<T>`;
and strings, lists, maps (over keys), and stacks are iterable.

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

## Algebraic enums and pattern matching

Enums are sum types: either opaque integer-backed constants (the original
form) or algebraic cases carrying positional payloads. An enum with any
payload case is algebraic — every case is identified by name and no case may
declare an explicit integer value (P077 for a payload case with a value;
C107 for an explicit value elsewhere in an algebraic enum). Integer-backed
enums keep their existing semantics, including explicit values and `int()`
conversion.

```solvik
enum Color {
    Red
    Green
    Blue
}

enum Result<T, E> {
    Ok(T)
    Error(E)
}

enum Shape {
    Rect(int, int)
    Circle(int)
    Group(Shape)
}
```

Enums may declare type parameters, with `&` constraints like structs. Payload
cases take positional types; `int(enumValue)` is rejected for payload cases
(E066). `typeOf` reports the lowercased enum name deterministically for every
case. Structural equality compares the case name and payloads element-wise.
`string()` renders payload cases as `CaseName(payload, ...)` and integer-backed
cases as their integer value.

### Construction

No-payload cases are constants: `Color.Red`. Payload cases are constructed by
calling the qualified case: `Result.Ok(5)`. Type arguments are inferred from
payload values, supplied explicitly as `Result<int, string>.Ok(5)`, or seeded
by an expected instantiation in a declaration, assignment, or return:

```solvik
r: Result<int, string> = Result.Ok(5)   // T and E from the annotation
e: Result<int, string> = Result.Error("boom")
ex: Result<string, bool> = Result<string, bool>.Ok("hi")
```

A type parameter that cannot be determined (it appears in no payload, has no
explicit argument, and no expected instantiation is available) is rejected
with E067. Payload count and types are checked statically (C101).

### Pattern matching

A switch case whose callee resolves to an enum case member is a pattern case:

```solvik
switch result {
    case Result.Ok(value) {
        println(value)
    }
    case Result.Error(error) {
        println(error)
    }
}
```

Pattern positions accept:

- binding names, declared in the case body scope (`Result.Ok(value)`);
- `_` wildcards, matching any payload without binding;
- literals, matching the payload value (`Shape.Circle(2)`);
- nested enum-case patterns, qualified (`Shape.Group(Shape.Circle(_))`) or
  bare same-enum (`case Ok(v)` at any level).

Same-enum qualification may be omitted at any pattern level: a bare case name
resolves as a member of the enum being matched (the switch value's enum at
the top level). If it is not a member, the case is an ordinary equality case.
Resolution is deterministic: enum-case membership wins over other
interpretations. A payload case used without pattern arguments in a switch is
an error (C107); pattern counts must match (C107); payload types must match
(C108); patterns of a different enum or instantiation are impossible (C094).
`default` behaves as before; `case null` matches nullable values.

### Exhaustiveness

A switch over a closed enum must cover every case or provide `default`;
missing cases are C105. A nullable switch additionally requires `case null`
or `default`. Duplicate coverage of the same case is C106. Switches over
`any` or non-enum values have no exhaustiveness requirement.
## Functions and closures

Functions are ordinary typed values. A function type lists parameter types and
ends with the return type:

```solvik
func<int, int>      // (int) -> int
func<int, string>   // (int) -> string
func<int>           // () -> int
func<int, void>     // (int) -> void
func<int, int>?     // nullable function value
```

`void` is allowed only as the return element (C104); a function type always
lists at least the return type (P076 if written bare). Function types are
invariant: an assignment matches only when every parameter type and the return
type are exactly equal. Generic and variadic functions have no assignable
function type (their signatures are not fixed), and built-in functions are not
typed values; use them by direct call instead.

A top-level function name, a bound method (`obj.method`), or an anonymous
function can be stored, passed, and returned:

```solvik
func double(x: int) -> int {
    return x * 2
}

f: func<int, int> = double          // top-level function value
h: func<string, string> = g.greet   // bound method value

func apply(value: int, f: func<int, int>) -> int {
    return f(value)
}

func makeAdder(amount: int) -> func<int, int> {
    return func(x: int) -> int {
        return x + amount
    }
}
```

Anonymous functions use exactly one syntax — `func(params) -> ret { body }`,
with no arrow when they return nothing — and may not declare type parameters
(their parameter and return types may still reference an enclosing
declaration's type parameters):

```solvik
multiply: func<int, int> = func(x: int) -> int {
    return x * factor
}
```

### Capture semantics

A closure captures the lexical environment that was current at its point of
definition, by reference to that storage:

- An immutable binding never changes after initialization, so capturing it
  behaves like a value copy.
- A `mut` binding is shared: assignments through the closure are visible to the
  enclosing scope, and later assignments in the enclosing scope are visible to
  the closure.
- Nested closures share the same storage.
- Each call of the enclosing function creates fresh storage, so two closures
  returned by two calls observe independent variables.
- A closure created inside a method captures the receiver; a closure in a
  `mut func` may mutate receiver fields, one in a plain `func` may not.
- Parameters shadow captured names; inner declarations shadow captured names.
- A captured environment lives as long as any closure referencing it.

Closure equality is reference identity: two references to the same named
function are equal; distinct closures are never equal, even with identical
behavior. `typeOf` reports `"function"` for every callable value, and
`string()` renders `<function name>` for named functions and `<closure>` for
anonymous ones. Callable values may be used as map keys and inside structs.

### Higher-order generics

Function types compose with generics; type parameters in a function type are
inferred from the argument value:

```solvik
func mapOne<T, R>(value: T, transform: func<T, R>) -> R {
    return transform(value)
}

s: string = mapOne(42, func(n: int) -> string { return "v=" .. n })
```

### Function-value checking

- Assigning an incompatible function signature to a variable, field, or
  parameter is a compile error (C100; runtime E066 as defense).
- Calls through function values check arity and argument types (C101 static;
  E068 runtime as defense, including calls through `any`).
- Calls to statically known non-callable values are rejected (C102).
- Calling a `null` function value raises a catchable `null reference` (E031).
- `break`/`continue` inside a closure body cannot escape the closure (E068).

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
Enum-case patterns (see "Algebraic enums and pattern matching") match by
case, bind payload variables, and are subject to exhaustiveness checking.

## Dependencies

`use` is the only source dependency directive. File dependencies use
`use file:path`; URL dependencies use `use url:value`, with optional
`checksum:sha256:<64-hex-digits>` and `insecure:true|false` flags. URL and
checksum values may be unquoted, quoted, or raw strings as required by their
contents. `import` is an ordinary identifier and is not dependency syntax.

## Packages and type identity

Every file begins with a `package` declaration naming its module. Files
sharing a package name merge into one package and call each other
unqualified; a library file (loaded via `use`) may not declare `main`.
Functions are accessed across packages with qualified syntax
(`package.function()`); unqualified calls resolve within the same package.

Type identity is canonical: `package + type name + generic arguments`
(`foo.User`, `collections.Box<int>`). A dependency package may not reuse a
built-in namespace name (`string`, `math`, `env`, `file`, `process`, `time`,
`random`, `path`, `hash`, `secrets`, `base64`): C121.

Cross-package types are usable in annotations, literals, enums, traits, and
patterns:

```solvik
client: http.Client = http.Client { name: "api" }
box: collections.Box<int> = collections.Box<int> { value: 1 }
status: http.Status = http.Status.OK
result: http.Outcome<int, string> = http.Outcome.Good(5)

func measure<T: http.Measurer>(x: T) -> int

switch result {
    case http.Outcome.Good(v) { ... }
    case http.Outcome.Bad(e) { ... }
}
```

Type parameters may be inferred from values across packages; expected
instantiations in declarations, assignments, and returns seed qualified
construction as well (`r: http.Outcome<int, string> = http.Outcome.Good(5)`).
The same local type name in different packages is two distinct types.
`typeOf`, `string()`, and `isType` display the local name only.

### Visibility

- Fields and methods are private unless marked `pub`; same-package access is
  unrestricted, cross-package access requires `pub` (C120 static; E070
  runtime defense, including access through `any`).
- Structs, enums, and traits are private unless declared `pub struct` /
  `pub enum` / `pub trait`; cross-package use of a private type is C120.
- Traits are satisfied only by public methods (unchanged).
- Functions are accessible cross-package without a visibility marker; this is
  the existing behavior and is documented rather than changed.

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

## Static validation

Before execution, the reference validator rejects programs that are
statically knowable to be invalid. Runtime checks remain as defensive
enforcement (catchable where documented, e.g. `null reference` E031). The
checks:

- Declarations: duplicate functions (C090), fields (C091), parameters (C092),
  enum cases (C091), and top-level type names (C109); unknown types in
  annotations (C110); generic arity (C096); non-nullable recursive struct
  fields (C097); method type-parameter shadowing (C099).
- Calls: argument counts and types for named functions, methods, built-ins,
  function values, enum construction, and variadic tails (C101); generic
  calls with explicit type arguments check against substituted parameters
  (C101); calls to non-callable values (C102); function-signature assignment
  (C100); generic constraint failures (C095).
- Control flow: every path of a non-void function or closure must return
  (C111); statements after `return`/`throw`/`break`/`continue` in the same
  block are unreachable (C112); `break`/`continue` outside a loop are errors
  (C113); `return` values must match the declared type (C114) and `return`
  shape must match void-ness (C115); switch cases are type-checked (C094)
  and exhaustive over closed enums (C105), with duplicates rejected (C106)
  and pattern shapes/types validated (C107/C108).
- Nullability: after `if x != null` (or `if x == null ... else`), `x` has its
  non-nullable type in the narrowed branch; a nullable value cannot flow into
  a non-nullable target without narrowing or `??` (rejected via C118/C119).
  Null dereference itself remains a catchable runtime semantic (E031).
- Mutability: assignment to an immutable binding (C116) or an immutable
  struct field (C117) is rejected; mutating receivers follow C068.
- Assignability: one centralized relation (`assignable`) drives declaration
  initializers (C118) and assignments (C119). Generic struct, enum, and
  function instantiations are invariant; numeric widening is `byte` to `int`
  to `float`; `any` targets accept anything; downcasting from `any` to a
  concrete type is a runtime-checked operation (E066); `string` to
  `exception` coercion is implicit.

## Standard library behavior

String and collection functions follow Go standard-library semantics:
`charAt` raises a catchable runtime error on an out-of-range index while
`substring` clamps its bounds to the string length; `indexOf` returns `-1`
when a substring is not found; `typeOf` reports struct type names lowercased.

### Collections with closures

`list<T>` gains higher-order methods driven by closures:

```solvik
xs.map(func(x: int) -> string { return "n" .. x })   // -> list<string>
xs.filter(func(x: int) -> bool { return x % 2 == 0 })
xs.fold(0, func(acc: int, x: int) -> int { return acc + x })
xs.reduce(func(a: int, b: int) -> int { return a + b })  // errors on empty (E072)
xs.find(func(x: int) -> bool { return x > 100 })          // -> T?
xs.any(func(x: int) -> bool { ... })
xs.all(func(x: int) -> bool { ... })
xs.contains(value)                                        // existing
xs.first() / xs.last()                                    // -> T?
xs.reverse()
xs.sort(func(a: int, b: int) -> int { return a - b })
```

These are methods on the list value itself, not global helpers. Their result
types follow the transform closure (`map` produces `list<R>`, `fold` produces
`U`).

### Standard namespaces

- `string`: `join`, `convert`, `repeat`, `padStart`, `padEnd`; string methods
  `len`, `charAt` (Unicode-safe runes), `substring`, `split`, `indexOf`,
  `toUpper`, `toLower`, `trim`, `byteLength`, `contains`, `startsWith`,
  `endsWith`. Formatting strategy: `string()` + `..`.
- `math`: `abs`, `min`, `max`, `floor`, `ceil`, `round`, `sqrt`, `pow`,
  `sin`, `cos`, `tan`, `PI`, `E`.
- `file`: `read`, `write`, `append`, `delete`, `remove`, `exists`, `temp`,
  `tempDir`, `list` (directory entries), `mkdir`, `isFile`, `isDir`, `size`,
  `rename`. File errors are catchable (E072).
- `path`: `join`, `basename`, `dirname`, `ext`, `abs`, `exists`.
- `process`: `run`, `capture(command, ...) -> map<string, any>` with
  `status`/`stdout`/`stderr`, `args() -> list<string>` (CLI arguments after
  the source file). `env`: `get`, `set`, `keys`.
- `json`: `parse(string) -> any` (a typed tree of `map<string, any>`,
  `list<any>`, scalars, and `null`; downcast with `isType`) and
  `stringify(value) -> string`. Parse/serialize errors are E072.
- `http`: `get(url)`, `post(url, body)`, `request(method, url, body, headers)`
  each return `map<string, any>` with `status`/`body`/`headers`; network
  failures are catchable (E072).
- `time`: `now()` (ms since epoch), `sleep(ms)`, `iso(ms)` (UTC ISO-8601),
  `parse(iso)` (ms). Durations are expressed in milliseconds.
- `random`: `float`, `int`, `range`, `uniform`, `choice`, `shuffle`,
  `sample`, `seed`. `base64`: `encode`/`decode`. `hash`: `md5`, `sha1`,
  `sha256`, `sha512`. `secrets`: `token`, `hex`.
- `test`: `assert(cond, msg?)`, `assertTrue`, `assertFalse`,
  `assertEq(a, b, msg?)`, `assertNe`, `assertNull`. Failures raise a
  catchable exception (E071). Test files call assertions from functions and
  return 0 on success.

## Conformance tests

Executable syntax fixtures live under `test/conformance/`. Valid fixtures must
compile without diagnostics. Invalid fixtures declare an expected diagnostic
code in their first comment. Python reference fixtures live under
`test/reference/`. `tools/parity.py` uses Python as the semantic oracle and can
compare Go and Rust observable output, exit status, and conformance diagnostics
as those implementations are brought to parity. `./build.sh` runs the Python
reference checks before the optimized toolchains.
