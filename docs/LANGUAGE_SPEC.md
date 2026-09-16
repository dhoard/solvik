# Solvik Language Specification

Status: normative implementation baseline.

`must` and `must not` define required behavior. Features explicitly marked `deferred` are not part of the language until this document defines them. An implementation must not invent semantics for a deferred or unspecified feature.

Solvik is a strongly and statically typed general-purpose language with familiar TypeScript/Kotlin-like syntax, explicit mutability, safe object-oriented defaults, composition/delegation, controlled inheritance, null safety, exhaustive pattern matching, first-class regular expressions, Rust-style raw strings, and Go-style semicolon insertion.

Solvik source files use the `.sol` extension. The language id is `solvik` and the MIME type is `application/x-solvik`.

Solvik does not support SimpleLanguage source syntax or semantics. There is no compatibility mode. Inherited SimpleLanguage implementation code is migration scaffolding, not part of this specification.

## 1. Design Goals

Solvik should be:

- familiar to TypeScript, Java, Kotlin, C#, and Dart developers;
- strongly and statically typed;
- nominally typed;
- concise without relying on ambiguous syntax;
- safe by default;
- final by default for classes and overridable members;
- composition-first while still supporting controlled single inheritance;
- predictable: syntax should not depend on parser error recovery;
- suitable for efficient GraalVM/Truffle execution.

### Lexical basics

Identifiers use `[A-Za-z_][A-Za-z0-9_]*`; keywords are reserved and `$` is not an identifier character. `//` starts a line comment. `/* ... */` is a non-nesting block comment. Comments are otherwise whitespace, but their physical newlines remain visible to semicolon insertion.

Decimal integer literals contain ASCII digits and have type `Int` in the initial typed core. A literal outside the signed 32-bit range is a compile-time error until additional literal forms are specified.

Phase 7 adds `L`-suffixed `Long` literals and decimal floating-point literals with an optional exponent. Floating-point literals have type `Double`; an `F` suffix selects `Float`. `Byte` and `Short` values use explicit conversion.

A character literal uses single quotes and contains exactly one Unicode scalar value or one of the escapes supported by normal strings, for example `'A'` or `'\n'`.

## 2. Variables and Mutability

`val` declares an immutable binding/property.

```solvik
val name: String = "Doug"
val count = 1
```

Reassignment is illegal:

```solvik
val count = 1
count = 2 // compile error
```

`var` declares a mutable binding/property.

```solvik
var count: Int = 0
count = count + 1
```

`val` freezes the binding, not the complete reachable object graph.

```solvik
class User {
    var name: String

    init(name: String) {
        this.name = name
    }
}

val user = User("Doug")
user.name = "Douglas" // valid
user = User("Other")  // compile error
```

## 3. Static and Strong Typing

Solvik uses nominal static typing.

Two unrelated classes with identical members are not assignment-compatible.

```solvik
class A {
    val value: String
}

class B {
    val value: String
}

val a: A = B("x") // compile error
```

`Any` must never behave like TypeScript's `any`. Assigning a value to `Any` does not disable type checking.

```solvik
val x: Any = "hello"
val n: Int = x // compile error
```

A checked cast or type refinement is required.

Assignments are statements, not value-producing expressions. The target must be a mutable local or `var` property. Calls require exact arity, and each argument must be assignable to its declared parameter type.

Operator precedence, from lowest to highest, is:

1. `??`;
2. `||`;
3. `&&`;
4. `==`, `!=`;
5. `<`, `<=`, `>`, `>=`, `is`, `as`;
6. `+`, `-`;
7. `*`, `/`;
8. unary `!` and unary `-`;
9. calls and member access.

`&&` and `||` short-circuit and require `Boolean` operands. Unary `!` requires `Boolean`. The initial arithmetic and ordering operators require `Int` operands and produce `Int` or `Boolean` as appropriate. Integer division truncates toward zero and division by zero raises a Solvik runtime arithmetic error. `String + String` concatenates; Solvik does not implicitly convert other values to `String`.

`==` and `!=` require operands whose types are assignment-compatible in at least one direction. They compare built-in scalar and enum values by value and ordinary class instances by identity. User-defined operator overloading is deferred.

## 4. Root Type Hierarchy

All non-null value types ultimately derive from the built-in root hierarchy.

Initial conceptual hierarchy:

```text
Any
└── Object
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
    ├── List<T>
    └── user-defined classes
```

Built-in types may use compiler/runtime-defined inheritance regardless of user-visible restrictions.

Phase 4 implements `Int` as the initial numeric type. `Byte`, `Short`, `Long`, `Float`, and `Double` are reserved built-in names and become usable when the complete root hierarchy is implemented in Phase 7. No implicit numeric widening or narrowing is permitted. Numeric conversion uses an explicit built-in type call such as `Long(value)`; an out-of-range integral conversion raises a Solvik runtime arithmetic error and an out-of-range constant conversion is a compile-time error.

Integral arithmetic is checked and raises a Solvik runtime arithmetic error on overflow. `Float` and `Double` follow IEEE 754 arithmetic. Arithmetic operands must have the same numeric type and produce that type.

`Any` is the top type for every non-null Solvik value. `Object` is the root of class, interface, and enum values. `Nothing` is a subtype of every type.

`Unit` has one value and is the result of a function that returns normally without a value. `Nothing` is the bottom type and has no values. Exception declaration and `throw` syntax are deferred.

## 5. Nullability

Types are non-null by default.

```solvik
val name: String = "Doug"
val bad: String = null // compile error
```

`T?` denotes a nullable type.

```solvik
val name: String? = null
```

`null` is assignable only to nullable types. If `S` is a subtype of `T`, then `S` is assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null `T`.

Safe member access:

```solvik
val length = name?.length
```

Null coalescing:

```solvik
val display = name ?? "Unknown"
```

For `receiver?.member`, the member is evaluated only when the receiver is non-null and the result type is the member type made nullable. For `left ?? right`, `left` must be nullable; the result is the common type of non-null `left` and `right`.

Flow-sensitive narrowing is required:

```solvik
if (name != null) {
    print(name.length) // name is String here
}
```

Narrowing is permitted only when the analyzed value cannot be written or invalidated along that control-flow path. A write to a `var` invalidates its prior narrowing.

## 6. Functions

Preferred syntax:

```solvik
fun add(a: Int, b: Int): Int {
    return a + b
}
```

Parameter types and function return types must be explicit in the initial implementation. Local variables may infer their type from an initializer.

A function that returns normally without a value has return type `Unit`. `Nothing` remains the bottom type for computations that never complete normally.

Source-file scope contains declarations only; executable top-level statements and global variables are not supported. A call may be used as a statement. Other value-producing expressions cannot stand alone as statements. `return;` is valid only in a `Unit` function; `return value` requires the value to be assignable to the declared return type.

Functions are not overloaded in the initial language: two functions with the same name in one scope are a compile-time error. The executable entry point is exactly `fun main(): Unit`. Command-line argument binding is deferred.

Names use lexical scope. Redeclaration in the same scope is an error. A nested block may shadow an outer declaration. A local variable must be definitely initialized before it is read.

The initial predeclared I/O functions are `print(value: Any): Unit` and `println(value: Any): Unit`. Strings and characters display as their contents, numbers in decimal, Boolean values as `true` or `false`, `Unit` as `Unit`, and an ordinary object as its class name. `println` appends the platform line separator. Input APIs and user-defined display customization are deferred.

## 7. Classes

Classes are final by default.

```solvik
class User {
    val id: Long
    var name: String

    init(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

A class must explicitly opt into inheritance:

```solvik
open class Animal {
    open fun speak(): String {
        return "..."
    }
}
```

Single inheritance only:

```solvik
class Dog extends Animal {
    override fun speak(): String {
        return "woof"
    }
}
```

Multiple class inheritance is forbidden.

Overrides must always use `override`.

Members are not overridable unless the declaration permits it.

The initial language has no visibility modifiers; declared members are externally accessible. Object storage remains encapsulated behind declared properties, and undeclared member access is illegal.

A class has at most one `init` declaration. Calling the class name invokes it. Every property without a declaration initializer must be assigned exactly once on every successful constructor path before it is read; a `val` property cannot be assigned afterward.

A class with no explicit `init` has an implicit zero-argument initializer only when all properties have declaration initializers. A subclass initializer must invoke `super(arguments)` as its first statement when the superclass has no zero-argument initializer; otherwise `super()` is implicit. `super.member` accesses the immediate superclass implementation.

An overriding method must have exactly the inherited parameter types and may return a subtype of the inherited return type. An `open` member may be overridden; all other members are final.

## 8. Interfaces

Interfaces define nominal contracts and may have default method implementations.

```solvik
interface Named {
    fun name(): String

    fun greeting(): String {
        return "Hello " + name()
    }
}
```

Classes may implement multiple interfaces:

```solvik
class User implements Named, Printable {
    ...
}
```

Interfaces contain methods, not stored properties. An implementing method must use the same parameter types and a covariant return type. If multiple interfaces provide an otherwise unresolved default for the same method, the class must explicitly override it.

## 9. Composition and Delegation

Composition is a primary language design mechanism.

Delegation removes forwarding boilerplate.

```solvik
interface Repository<T> {
    fun find(id: Long): T?
    fun save(value: T): Unit
}

class UserService implements Repository<User> {
    delegate val repository: Repository<User>

    init(repository: Repository<User>) {
        this.repository = repository
    }
}
```

The compiler synthesizes forwarding behavior for interface members supplied by a delegate. A delegate is an immutable, explicitly typed property that must be initialized under the normal constructor rules.

Explicit methods declared on the class take precedence over delegated members.

Ambiguous delegation must be a compile-time error.

```solvik
class X implements Printable {
    delegate val a: PrinterA
    delegate val b: PrinterB

    // compile error if both supply print() and X does not explicitly resolve it
}
```

## 10. Built-in Types and Runtime Representation

Language-level primitive types are class types conceptually, but the runtime may specialize them to efficient JVM/Truffle primitive representations.

For example:

```text
Int / Long -> primitive integral representations where profitable
Boolean    -> boolean
Double     -> double
```

The language object model must not force unnecessary boxing.

## 11. Generics

Solvik supports nominal generics.

```solvik
class Box<T> {
    var value: T
}

val names: List<String>
```

Generic type arguments are invariant. The initial runtime uses erasure while preserving complete compile-time checking. A runtime type test against a non-reified type argument is a compile-time error.

`List<T>` is the initial immutable collection type. It exposes `val size: Int` and `fun get(index: Int): T`; an invalid index raises a Solvik runtime bounds error. Collection literals, mutable collections, maps, iteration protocols, and collection variance are deferred.

## 12. Enums, Sealed Types, and Exhaustive Match

Enums may carry values.

```solvik
enum Result<T, E> {
    Ok(T)
    Error(E)
}
```

Enum variants are nested nominal constructors. Outside a context that already establishes the enum type, qualify them as `Result.Ok(value)`. Inside a `match` over a known enum, `Ok(value)` is permitted.

`match` is expression-oriented and exhaustive where the compiler knows a closed variant set.

```solvik
val message = match result {
    Ok(value) => "value=" + value
    Error(error) => "error=" + error
}
```

Missing a known enum/sealed variant is a compile-time error unless a wildcard pattern handles it.

`sealed` types define a closed hierarchy usable for exhaustiveness analysis.

A `sealed class` is abstract and may be extended only by declarations in the same source file. Its complete transitive subtype set is closed when that file is compiled.

Initial `match` patterns are enum variant patterns, sealed-subtype binding patterns of the form `name: Type`, and wildcard `_`. Branches are checked in source order, duplicate or unreachable branches are errors, and every known variant must be covered unless `_` is present. The result type is the nearest common declared supertype to which every branch result is assignable; if none exists, the match is ill-typed.

## 13. switch

`switch` is a statement for straightforward value dispatch and Regex matching.

Cases never implicitly fall through.

```solvik
switch (value) {
    case 1:
        print("one")

    case 2:
        print("two")

    default:
        print("other")
}
```

No `break` is required to terminate a case.

Cases are tested in source order and exactly the first matching case executes. Each case body is an implicit block. A `break` inside a case is illegal unless it exits a loop nested inside that case.

Constant case expressions must be compile-time constants assignable to the switched value's type. Regex cases require a `String` switch value. A switch contains at most one `default`, and it must be last.

Initial Solvik does not provide a `fallthrough` keyword. Shared cases are expressed directly, for example:

```solvik
case 1, 2:
    print("one or two")
```

## 14. Regex

`Regex` is a first-class built-in type.

Minimum conceptual API:

```solvik
class Regex extends Object {
    fun matches(value: String): Boolean
    fun find(value: String): RegexMatch?
    fun findAll(value: String): List<RegexMatch>
    fun replace(value: String, replacement: String): String
}
```

The initial portable pattern syntax supports literals, `.`, `^`, `$`, character classes, capturing groups, alternation, `*`, `+`, `?`, `{m}`, `{m,}`, `{m,n}`, and the ASCII classes `\d`, `\s`, and `\w` with their uppercase negations. Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.

`matches` requires the complete input to match. `find` returns the first non-overlapping match and `findAll` returns all non-overlapping matches from left to right. `replace` replaces all non-overlapping matches and treats the replacement as literal text; capture substitution is deferred.

`RegexMatch` exposes immutable `value: String`, `start: Int`, `end: Int`, `groupCount: Int`, and `fun group(index: Int): String?`. Offsets are zero-based character offsets and `end` is exclusive. Group zero is the complete match.

Regex construction accepts raw strings:

```solvik
val number = Regex(r#"^\d+$"#)
```

Regex patterns may be used in `switch` cases:

```solvik
switch (input) {
    case regex r#"^\d+$"#:
        print("number")

    case regex r#"^[A-Za-z]+$"#:
        print("word")

    default:
        print("other")
}
```

Regex cases do not establish exhaustiveness. A wildcard/default is required where exhaustiveness is required.

Regex match/capture binding in `switch` is deferred.

## 15. Strings

### Normal strings

Normal strings cannot contain an unescaped physical newline. They support exactly `\\`, `\"`, `\n`, `\r`, `\t`, and `\0`. Any other escape is a lexical error.

```solvik
val message = "hello\nworld"
```

String interpolation is deferred. A `$` has no interpolation meaning in the initial implementation.

### Rust-style raw strings

Solvik supports Rust-style raw string delimiters.

```solvik
r"simple"
r#"Test '"#
r##"contains "# text"##
r###"arbitrary content"###
```

General rule:

```text
r + N '#' characters + '"' + content + '"' + exactly N '#' characters
```

The `r`, hashes, and opening quote must be contiguous. If `r` is not immediately followed by zero or more `#` characters and a quote, it is lexed as an identifier. The opening delimiter fixes `N`; the first quote followed by exactly `N` hashes closes the token. The token's semantic value is the content between the delimiters.

Raw strings:

- do not process backslash escapes;
- allow quotes except when the exact closing delimiter is encountered;
- preserve embedded newlines;
- initially do not perform string interpolation.

An unterminated raw string is a lexical error at its opening delimiter. The diagnostic must show the exact closing delimiter that was expected.

Examples:

```solvik
val regex = r#"\d+\s+"#
val json = r#"{"name":"Doug","path":"C:\temp"}"#
val sql = r#"
SELECT *
FROM users
WHERE name = 'Doug'
"#
```

## 16. Statement Termination

Solvik uses Go-style lexical semicolon insertion.

Programmers may explicitly write `;`, but normal style uses newlines.

```solvik
val x = 1
val y = 2
```

is tokenized equivalently to:

```solvik
val x = 1;
val y = 2;
```

Semicolon insertion must occur in a token-stream stage after lexing and before parsing. It must not depend on parser errors.

The lexer must preserve physical newline information. The token-stream stage ignores spaces and comments but treats a newline contained in a line comment or block comment as a physical newline.

At a physical newline, emit one synthetic `SEMI` token when all of these conditions hold:

1. the unmatched `(` and `[` nesting depths are both zero;
2. the preceding significant token is an identifier, a literal, `break`, `continue`, `return`, `)`, `]`, or `}`;
3. the next significant token is not `.`, `?.`, or `else`.

At end of file, apply the same rule without a next-token exception. Consecutive blank lines must not emit duplicate semicolons. Explicit `;` and synthesized semicolons must both become the parser's `SEMI` token.

Expressions continue naturally after operators and commas:

```solvik
val total = price +
    tax +
    shipping
```

`return` followed by a newline terminates the return statement:

```solvik
return
value
```

is equivalent to:

```solvik
return;
value;
```

### Member chaining

Solvik supports TypeScript/Kotlin-style leading-dot chains:

```solvik
val result = service
    .load()
    .transform()
```

The semicolon-inserting token stream must suppress insertion when the next significant token is `.` or `?.`. This is the only member-chain lookahead exception; do not use general JavaScript-style heuristics.

## 17. Control Flow

Standard forms:

```solvik
if (condition) {
    ...
} else {
    ...
}

while (condition) {
    ...
}

for (var i: Int = 0; i < limit; i = i + 1) {
    ...
}
```

Parentheses around conditions are retained for TypeScript/Java familiarity.

`if` and loop conditions must have type `Boolean`. `while` is a pre-test loop. `for` uses exactly three clauses: an optional local declaration or assignment, an optional Boolean condition, and an optional assignment. The two separators inside `for (...)` are explicit semicolons. An omitted condition is `true`. `break` and `continue` are valid only inside a loop.

## 18. Type Tests and Casts

Support type tests:

```solvik
if (value is String) {
    print(value.length)
}
```

The compiler must narrow the type where the checked value is stable and no intervening write can invalidate the refinement.

Checked cast syntax:

```solvik
val user = value as User
```

An unsuccessful `as` cast raises a Solvik runtime type error. Safe-cast syntax is deferred.

## 19. Semantic Priorities

When language features conflict, prefer:

1. compile-time correctness;
2. deterministic syntax;
3. explicit semantics;
4. safe defaults;
5. understandable diagnostics;
6. runtime performance;
7. syntactic convenience.

Do not copy TypeScript's unsound `any` behavior or JavaScript's automatic semicolon insertion behavior.
