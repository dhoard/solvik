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

    User(name: String) {
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
4. `==`, `!=`, `===`, `!==`;
5. `<`, `<=`, `>`, `>=`, `is`, `as`;
6. `..`;
7. `+`, `-`;
8. `*`, `/`;
9. unary `!` and unary `-`;
10. calls and member access.

`&&` and `||` short-circuit and require `Boolean` operands. Unary `!` requires `Boolean`. The initial arithmetic and ordering operators require `Int` operands and produce `Int` or `Boolean` as appropriate. Integer division truncates toward zero and division by zero raises a Solvik runtime arithmetic error. `..` concatenates: both operands are rendered through `toString` and the result is always `String`, so `1 .. "x"` is `"1x"` and `"x" .. null` is `"xnull"`. Concatenation binds looser than arithmetic, so `a + b .. c` is `(a + b) .. c`, and it is left-associative. Solvik performs no other implicit conversion to `String`.

### Equality and reference identity

Solvik distinguishes semantic equality from reference identity:

```text
==    semantic equality
!=    semantic inequality
===   reference identity
!==   reference non-identity
```

`===` and `!==` occupy the same precedence tier as `==` and `!=`. All four are left-associative.
The lexer uses longest-match, so `===` and `!==` are single tokens rather than a shorter operator
followed by `=`. Every equality or identity expression evaluates its left operand first and its
right operand second, exactly once each. `!=` and `!==` negate the corresponding positive operation
without evaluating either operand again.

#### Semantic comparability for `==` and `!=`

`left == right` and `left != right` are well typed only when one operand type is assignable to the
other; the result type is `Boolean`. Two `Int` values are comparable, a `Point` and `Any` are
comparable, and unrelated nominal classes are not directly comparable even though `equals` accepts
`Any?`. A caller that intentionally wants an arbitrary comparison may use an `Any`-typed value or
call `equals` explicitly.

#### The universal equality member

Every non-null value has the built-in member:

```solvik
open func equals(other: Any?): Boolean
```

It is a language-defined universal member, not operator overloading, and its explicit call and `==`
use the same semantic equality definition:

```solvik
value.equals(other)
value == other
```

A user class may declare exactly:

```solvik
override func equals(other: Any?): Boolean
```

The compiler requires `override`, exactly one explicit parameter typed exactly `Any?`, and return
type exactly `Boolean`. The inherited root member is open, and an override follows the ordinary
`open`/`final` rules for further subclasses. An interface cannot redeclare `equals`, and a property
or delegate cannot use the reserved name `equals`. A direct call on a nullable receiver follows
ordinary nullable-member rules: `value?.equals(other)` is safe and has result `Boolean?`, while
`value.equals(other)` is an error when `value` may be null. Built-in scalar, `Unit`, enum, and
reference-backed built-in implementations are fixed and cannot be overridden. A bare `value.equals`
member read is invalid, exactly like a bare `value.toString` read.

#### Semantic equality algorithm

After both operands have been evaluated, `left == right` performs these steps in order:

1. if both values are `null`, the result is `true`;
2. if exactly one value is `null`, the result is `false` and no user code runs;
3. else, if the left value is a built-in scalar or `Unit`, its fixed rule below applies;
4. else, if the left value is an enum value, recursive enum equality applies;
5. else, if the left value is a user-defined class instance, its effective `equals(other)` override
dispatches dynamically; when no class in its hierarchy overrides `equals`, the root default is
reference identity;
6. else the fixed rule for the remaining built-in value below applies.

The left operand is the dynamic receiver. The right operand never receives a fallback equality
call. An override is invoked even when both operands are the same reference; there is no general
identity shortcut before user dispatch, so `==` and an explicit `equals` call stay behaviorally
aligned even for an override with side effects. Guest exceptions from `equals` propagate normally. A
comparison never delegates to arbitrary Java `equals`.

The fixed built-in rules are:

| Type | Semantic equality |
|---|---|
| `Byte`, `Short`, `Int`, `Long` | same-type integral value |
| `Float`, `Double` | same-type IEEE 754 `==` |
| `Boolean` | Boolean value |
| `Char` | character value |
| `String` | character-sequence content |
| `Unit` | always equal to `Unit` |
| `List`, `Set`, `Map`, `Stack` | reference identity |
| `Regex` | exact pattern source text |
| `RegexMatch` | immutable snapshot: `value`, `start`, `end`, `groupCount`, and every captured group |

Floating equality preserves IEEE behavior: NaN is unequal to every value including itself, positive
and negative zero are equal, and infinities compare by their values. Two enum values are equal
exactly when they belong to the same enum type, have the same variant, and their corresponding
payloads are semantically equal, left to right. Enum equality never delegates to Java array or
object equality.

`Set` construction, `add`, `contains`, and `remove`, `Map` construction, `put`, `get`,
`containsKey`, and `remove`, and constant `switch` matching all use this one definition, comparing
the resident element or key as the left receiver. `Map.put` preserves the resident key and its
position when a semantically equal key is supplied.

A user `equals` implementation must be reflexive, symmetric, transitive, consistent while
equality-relevant state is unchanged, and false for `null`. The compiler and runtime do not prove or
repair these properties.

#### Reference identity

`===` answers whether two values are the same Solvik allocation. It never invokes `equals`, another
guest method, or Java `Object.equals`. `!==` is its exact logical negation.

The identity-bearing static types are exactly:

- user-defined class types, including sealed classes and parameterized class applications;
- interface types, including parameterized interface applications;
- `List<T>`, `Set<T>`, `Map<K, V>`, and `Stack<T>`;
- nullable forms of the preceding types.

The following types are not identity-bearing: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`,
`Boolean`, `Char`, `String`, and `Unit`; enum types; `Regex` and `RegexMatch`; `Any` and `Object`;
unbounded type parameters; `Nothing` and a bare null literal. A value held in `Any` or `Object` must
first be narrowed or checked-cast to an identity-bearing type, which prevents a JVM representation
choice from becoming observable when the runtime value is a scalar, string, enum, regex, or `Unit`.

Identity operands must also satisfy the ordinary equality comparability rule: one operand type must
be assignable to the other. After removing nullability, at least one operand must establish an
identity-bearing type and the other must be compatible with it. A null literal is permitted only
against a nullable identity-bearing operand, so `null === null` is a compile error. A failure of
assignability uses the ordinary invalid-operand diagnostic; a compatible pair with no identity-bearing
operand uses `SOLV-TYPE-039`.

```solvik
val a = Point(1, 2)
val b = Point(1, 2)
val c = a

a == b   // false without an override; true when Point.equals compares fields
a === b  // false
a === c  // true
a !== c  // false
```

Stable nullable identity tests participate in flow analysis: `x !== null` narrows `x` to its
non-null reference type on the true path, and `x === null` narrows it on the false path, under the
same write-invalidation rules as `== null` and `!= null`.

User-defined operator overloading beyond the universal `equals` member is deferred.

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

`Any` declares the universal members `func toString(): String` and `open func equals(other: Any?): Boolean` (section 3). They are available on every non-null value. Built-in scalars provide fixed, non-overridable implementations: `Int`, `Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style floating-point text, `Boolean` renders `true` or `false`, `Char` renders its character, `String` renders its contents, and `Unit` renders `Unit`. A built-in scalar cannot be extended and its `toString` cannot be overridden. A user-defined class inherits the default representation (its class name) and may declare `override func toString(): String` for a class-specific representation (section 7).

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

The reference-identity null tests `name === null` and `name !== null` narrow the same way.

Narrowing is permitted only when the analyzed value cannot be written or invalidated along that control-flow path. A write to a `var` invalidates its prior narrowing.

## 6. Functions

Preferred syntax:

```solvik
func add(a: Int, b: Int): Int {
    return a + b
}
```

Parameter types must be explicit in the initial implementation. A function's return type is written only when the function returns a value; a declaration that omits the return type returns no value and has type `Unit`. Writing `: Unit` explicitly is permitted but redundant. Local variables may infer their type from an initializer.

A function that returns normally without a value has return type `Unit`, whether that type is omitted or written explicitly. `Nothing` remains the bottom type for computations that never complete normally.

The program scope contains declarations and executable statements, which may be interleaved freely. When the root source uses compile-time inclusion (section 20), the declarations and statements of every expanded file participate in this one program; a file that declares a `module` places its top-level declarations in that module's namespace, and an included module may be referenced through a namespace prefix (section 20). The top-level statements, in include-expansion order, form the body of an implicit `func main()`; a top-level `val`/`var` is therefore a local of the implicit main, not a global. Declaration lookup remains order-independent within a module, so a declaration may be referenced from a physically earlier file or statement. The entry point is always implicit: declaring a function named `main` explicitly, in the root or in any included file, is a compile-time error. A program with no executable top-level statements has no entry point and does nothing. A call may be used as a statement. Other value-producing expressions cannot stand alone as statements. `return;` is valid only in a function declared without a return type; `return value` requires the value to be assignable to the declared return type.

Functions are not overloaded in the initial language: two functions with the same name in one scope are a compile-time error. The executable entry point is the implicit `main` formed by the program's executable top-level statements. Command-line argument binding is deferred. A program that reaches the end of its entry point exits with status `0`; the predeclared `exit(code: Int)` function terminates the program immediately with the given status.

Names use lexical scope. Redeclaration in the same scope is an error. A nested block may shadow an outer declaration. A local variable must be definitely initialized before it is read.

### Scope blocks

A brace-delimited block may stand alone as a statement. A scope block introduces a new lexical scope for the statements it contains; sibling blocks are independent scopes, so the same local name may be declared in each without any shadowing between them.

```solvik
{
    val result: String = parseHeader()
    handleHeader(result)
}

{
    val result: String = parseBody()
    handleBody(result)
}
```

A scope block is neither a loop nor a function boundary: `break`, `continue`, and `return` inside it apply to the enclosing loop or function. A block nested inside another block may still shadow an outer declaration, exactly like the body of an `if`, `while`, or `for`.

The initial predeclared I/O functions are `print(value: Any?)` and `println(value: Any?)`. Both accept every value including `null`; `null` displays as `null`. A value displays as its `toString()` representation (section 4): strings and characters as their contents, numbers in decimal or Java-style floating-point text, Boolean values as `true` or `false`, `Unit` as `Unit`, and an ordinary object as its class name unless the class overrides `toString`. Because display is defined by `toString`, a class override is honored by `print`, `println`, and `..`. `println` appends the platform line separator. The predeclared `exit(code: Int)` function runs no further Solvik code: it terminates the program with `code` as the process exit status and returns no value. Input APIs are deferred.

### Callable arity

The number of explicit arguments supplied to a statically resolved callable must satisfy the callable's parameter requirements. Argument-count validation occurs during semantic analysis: the parser only recognizes an argument list, and an arity mismatch is a source-located compile-time error that prevents the program from being lowered or executed.

For a callable declared with only required parameters, the required count is the number of declared parameters:

```solvik
func add(a: Int, b: Int): Int {
    return a + b
}

add(1, 2)      // valid
add(1)         // compile error: too few arguments
add(1, 2, 3)   // compile error: too many arguments
```

A call's argument list may end with a trailing comma (`add(1, 2,)`). The trailing comma contributes
no argument, so it never affects arity. The list still requires at least one argument, so `add(,)` is
a parse error while `add()` is the ordinary empty argument list. Only call argument lists accept a
trailing comma; parameter lists, type-argument lists, enum variant value lists, match pattern lists,
and switch case labels do not.

The receiver of an instance method is not an explicit argument and does not contribute to source-level arity. In `user.setName("Doug")`, a method declared as `func setName(name: String)` has source-level arity `1`. Constructors, interface methods, and built-in functions follow the same rule. The predeclared `print`, `println`, and `exit` functions each declare exactly one parameter, so a call that supplies a different number of arguments is a compile-time error; built-ins participate in the ordinary resolved-callable model rather than receiving separate arity rules.

A statically resolved call's arity is verified before its argument types and before generic type-argument inference. A call with the wrong number of arguments therefore reports an arity error rather than a misleading argument type error, and the incorrect count suppresses the argument type checks and inference for that call.

The initial language has no default parameters, no variadic parameters, no overloading, and no first-class function values, so every statically resolved callable has exactly one permitted argument count. A runtime arity check remains only as an internal invariant: source programs cannot reach it because an invalid count is rejected during semantic analysis.

## 7. Classes

Classes are final by default.

```solvik
class User {
    val id: Long
    var name: String

    User(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

A class must explicitly opt into inheritance:

```solvik
open class Animal {
    open func speak(): String {
        return "..."
    }
}
```

Single inheritance only:

```solvik
class Dog extends Animal {
    override func speak(): String {
        return "woof"
    }
}
```

Multiple class inheritance is forbidden.

Overrides must always use `override`.

Members are not overridable unless the declaration permits it.

The initial language has no visibility modifiers; declared members are externally accessible. Object storage remains encapsulated behind declared properties, and undeclared member access is illegal.

A class declares its constructor as a class member whose name is the class name, with a parameter list and a body, and without the `func` keyword or a return type:

```solvik
class User {
    val id: Long
    var name: String

    User(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

Calling the class name invokes its constructor. A class has at most one constructor declaration. Every property without a declaration initializer must be assigned exactly once on every successful constructor path before it is read; a `val` property cannot be assigned afterward.

A constructor is not a method. It is not inherited, cannot carry `open` or `override`, is not declared by an interface, is not forwarded by a `delegate`, and cannot be invoked as `this.User(...)`. For a generic class `Box<T>`, the constructor is named `Box`, not `Box<T>`. A class member declaration other than the constructor cannot have the same name as its class.

A class with no explicit constructor has an implicit zero-argument initializer only when all properties have declaration initializers. A subclass constructor must invoke `super(arguments)` as its first statement when the superclass has no zero-argument initializer; otherwise `super()` is implicit. `super.member` accesses the immediate superclass implementation.

An overriding method must have exactly the inherited parameter types and may return a subtype of the inherited return type. An `open` member may be overridden; all other members are final.

The inherited `Any.toString()` is an open member, so a class may declare `override func toString(): String` for a class-specific string representation. Because the built-in member is always inherited, declaring `toString` without `override`, changing its parameter list, or returning a type other than `String` is a compile-time error, and a stored member may not reuse the reserved name `toString`.

## 8. Interfaces

Interfaces define nominal contracts and may have default method implementations.

```solvik
interface Named {
    func name(): String

    func greeting(): String {
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
    func find(id: Long): T?
    func save(value: T)
}

class UserService implements Repository<User> {
    delegate val repository: Repository<User>

    UserService(repository: Repository<User>) {
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

`List<T>`, `Set<T>`, `Stack<T>`, and `Map<K, V>` are the initial built-in mutable collection types. They are nominal generic types deriving from `Object`; their type arguments are invariant and erased at runtime.

A collection is constructed with a class-style call. The type arguments may be written explicitly
(`List<Int>(1, 2, 3)`) or omitted to infer them from the declared type of the left-hand side
(`val names: List<String> = List("a", "b")`); a construction that writes neither is a compile-time
error. A call with no value arguments constructs an empty collection (`List<Int>()`).

For `List`, `Set`, and `Stack`, the value arguments are the initial elements and each must be
assignable to the element type; `Set` keeps only the first of equal elements. `Map` takes
`key: value` entries, each key assignable to `K` and each value assignable to `V`; a repeated key
keeps its position and takes the latest value. A `key: value` entry is meaningful only in a `Map`
construction, and a positional value is not valid in a `Map` construction.

* `List<T>`: `val isEmpty: Boolean`, `val size: Int`, `func add(element: T)`, `func get(index: Int): T`,
  `func removeAt(index: Int): T`, `func set(index: Int, element: T)`, `func clear()`. An invalid index
  raises a Solvik runtime bounds error.
* `Set<T>`: `val isEmpty: Boolean`, `val size: Int`, `func add(element: T): Boolean`,
  `func contains(element: T): Boolean`, `func remove(element: T): Boolean`, `func clear()`.
* `Map<K, V>`: `val isEmpty: Boolean`, `val size: Int`, `func put(key: K, value: V)`,
  `func get(key: K): V`, `func containsKey(key: K): Boolean`, `func remove(key: K): Boolean`,
  `func clear()`. `get` for a missing key raises a Solvik collection error.
* `Stack<T>`: `val isEmpty: Boolean`, `val size: Int`, `func push(element: T)`, `func peek(): T`,
  `func pop(): T`, `func clear()`. `peek` and `pop` on an empty stack raise a Solvik collection error.

Collection literals beyond a constructor call, iteration protocols, and collection variance remain
deferred.

## 12. Enums, Sealed Types, and Exhaustive Match

Enums may carry values.

```solvik
enum Result<T, E> {
    Ok(T)
    Error(E)
}
```

Enum variants are nested nominal constructors. Outside a context that already establishes the enum type, qualify them as `Result.Ok(value)`. Inside a `match` over a known enum, `Ok(value)` is permitted.

`match` is expression-oriented and exhaustive where the compiler knows a closed variant set. Each
branch result is an expression; because a block is an expression (section 21), a branch may use a
brace-delimited block for multiple statements followed by a tail result.

```solvik
val message = match result {
    Ok(value) => "value=" + value
    Error(error) => "error=" + error
}
```

Missing a known enum/sealed variant is a compile-time error unless a wildcard pattern handles it.

`sealed` types define a closed hierarchy usable for exhaustiveness analysis.

A `sealed class` is abstract and may be extended only by declarations in the same physical source file. Its complete transitive subtype set is closed when the program is compiled. An `include` splices declarations into one program but does not erase the physical file boundary, so a subclass written in a different included file is a compile-time error.

Initial `match` patterns are enum variant patterns, sealed-subtype binding patterns of the form `name: Type`, and wildcard `_`. Branches are checked in source order, duplicate or unreachable branches are errors, and every known variant must be covered unless `_` is present. The result type is the nearest common declared supertype to which every branch result is assignable; if none exists, the match is ill-typed.

## 13. switch

`switch` is a statement for straightforward value dispatch and Regex matching. It is also available
as an **expression** that produces a value; the statement and expression forms share their surface
syntax and are distinguished by syntactic context (section 21).

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
    func matches(value: String): Boolean
    func find(value: String): RegexMatch?
    func findAll(value: String): List<RegexMatch>
    func replace(value: String, replacement: String): String
}
```

The initial portable pattern syntax supports literals, `.`, `^`, `$`, character classes, capturing groups, alternation, `*`, `+`, `?`, `{m}`, `{m,}`, `{m,n}`, and the ASCII classes `\d`, `\s`, and `\w` with their uppercase negations. Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.

`matches` requires the complete input to match. `find` returns the first non-overlapping match and `findAll` returns all non-overlapping matches from left to right. `replace` replaces all non-overlapping matches and treats the replacement as literal text; capture substitution is deferred.

`RegexMatch` exposes immutable `value: String`, `start: Int`, `end: Int`, `groupCount: Int`, and `func group(index: Int): String?`. Offsets are zero-based character offsets and `end` is exclusive. Group zero is the complete match.

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

A top-level `include` directive (section 20) ends with an explicit or inserted `SEMI` exactly like a statement. Its path is a string or raw-string literal, so a following physical newline terminates the directive under this section's ordinary rule; no include-specific termination rule exists.

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

Solvik also supports range `for`-in loops:

```solvik
for (i in 1...5) {
    ...
}

for (i in 0..<5) {
    ...
}

for (i in 5..>0) {
    ...
}
```

The loop variable is an implicitly declared immutable `Int` binding scoped to the loop body. Both bounds are `Int` expressions evaluated once before the first iteration. `...` ascends from the start and includes the end, `..<` ascends from the start and excludes the end, and `..>` descends from the start and excludes the end. A reversed or empty range performs zero iterations rather than raising an error. `break` and `continue` behave exactly as in the three-clause `for`. Range `for`-in is a distinct loop construct; it does not introduce the general iteration protocol, which remains deferred (section 11). `in` is a reserved keyword.

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

## 20. File Inclusion

Solvik supports a top-level, compile-time `include` directive that expands other `.sol` files into
one statically checked program.

```solvik
include "lib/math.sol"
include r#"lib/generated.sol"#;
```

`include` is a reserved keyword. An include may appear only as an item of a compilation unit: it is
not a statement and cannot appear in a function, method, constructor, block, loop, `switch`, or
`match` branch. The path is a normal or raw string literal and the directive ends with an explicit
or lexically inserted `SEMI` (section 16).

An include may bind a file-local namespace prefix with an optional `alias` suffix:

```solvik
include "lib/math.sol"
include "lib/math.sol" alias math
```

`alias` is a reserved keyword. The alias name is written after the path and is followed by the same
explicit or inserted `SEMI`. There is no export or selective-import form. The included declarations
are reached through the prefix with the `::` namespace separator:

```solvik
math::add(1, 2)
val point: math::Point = math::Point(1)
val result: math::Result = math::Result.Ok(1)
```

### Modules and namespaces

A physical file may name its namespace with an optional `module` declaration, which must be the first
item in the file:

```solvik
module com_example_util

func add(a: Int, b: Int): Int {
    return a + b
}
```

`module` is a reserved keyword. The written name is a single identifier: lowercase letters and
digits with parts joined by exactly one underscore, each part starting with a letter
(`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word. Underscores replace Java package
dots, so `com.example.util` is written `com_example_util`. Module names never contain dots. A file
without a `module` declaration belongs to the implicit default module.

- A file's top-level `func`, `class`, `interface`, and `enum` declarations belong to its module.
- Two files that declare the same module name are one module and their declarations merge; a
  duplicate declaration within the merged module is `SOLV-RESOL-002`.
- Including a file that declares a module makes that module's name a visible prefix in the including
  file. `include P alias p` binds the prefix `p` to the included file's module instead. An alias
  name follows the same naming rule as a module name.
- Prefixes are file-local and non-transitive: a file does not inherit the prefixes or aliases of the
  files it includes; it must include a file itself to reference it.
- `alias` naming a file in the default module is `SOLV-RESOL-014`, because the default module has no
  name to bind.
- Binding one prefix twice in a file, including a collision with a prefix an unaliased include
  already made visible, is `SOLV-RESOL-013`.
- `::` is the namespace separator. A qualified declaration reference is written `p::Name`, where the
  prefix `p` must be a visible module or alias; a qualified type is `p::Type`, a qualified call is
  `p::function(...)`, and a qualified enum variant is `p::Enum.Variant`. The `.` operator remains
  ordinary member access, so a qualified reference is never confused with member access and there is
  no name-collision rule between declarations and prefixes.

Unqualified name resolution within a file is, innermost first: lexical locals and parameters, the
file's own module, the implicit default module, and the built-in prelude. Built-in types and
functions are always visible unqualified and cannot be shadowed by a module or alias name.

Top-level `val`/`var` declarations and executable statements are not part of any module namespace:
they remain locals and statements of the single implicit `main` (section 6) and are not reachable as
`p::name`. A qualified reference does not change the single-`main` execution model.

### Path resolution

For `include P` written in physical file `F`:

1. Decode `P` with the normal or raw string rules of section 15. An invalid normal-string escape is
   a lexical error at the path literal and no lookup occurs.
2. Require a non-empty path whose final file name ends in `.sol`; otherwise report
   `SOLV-RESOL-007` at the path literal.
3. If `P` begins with the exact prefix `~/`, replace that prefix with the host user's home directory
   and treat the result as absolute. A later `~` and a leading `~name` are ordinary path text. If no
   home directory is available, report `SOLV-RESOL-007`.
4. An absolute expanded path is used directly. Otherwise it is resolved against the directory
   containing `F`.
5. A root that is not file-backed (for example `<stdin>` or an in-memory polyglot `Source`) resolves
   its relative includes against the environment's current working directory. Every nested relative
   include is resolved against its including file, never the root directory.
6. The file is normalized and canonicalized before it is used as an identity.

Resolution uses the Truffle environment's public file access, so polyglot I/O permissions remain
authoritative. There are no include search paths and no fallback search order. Shell interpolation,
environment-variable expansion, URLs, classpath resources, package lookup, and non-file URI schemes
are not part of the language.

### Expansion, duplicates, and cycles

Expansion is depth-first and left-to-right. Each physical file is parsed with the ordinary lexer,
semicolon-inserting token stream, and parser. At an `include`, the target is recursively expanded and
its resolved top-level items are spliced at the include position; the directive itself is absent from
the resolved program.

- A canonical physical file is expanded at most once per evaluated root. A later include of the same
  canonical file is a no-op, so a diamond is deterministic and an included top-level statement never
  runs twice.
- Two paths or symlinks that resolve to the same file are the same include. Two different files with
  identical contents remain different includes.
- If a canonical file is encountered while it is still being expanded, report `SOLV-RESOL-011` at the
  include that closes the cycle. The message lists the canonical cycle chain in encounter order.

For example, when `root` includes `a` then `b`, and both `a` and `b` include `common`, the expanded
item order is the items of `common`, then the remaining items of `a`, then the remaining items of `b`,
then the remaining items of `root`.

### Program scope and entry point

The expanded items form one program. Declaration lookup is order-independent within a module. Within
the implicit default module all top-level functions, classes, interfaces, and enums share one
declaration scope, and a duplicate name is `SOLV-RESOL-002`; a named module merges the declarations
of every file that declares that module and rejects a duplicate within it. Redeclaring a built-in
function or type is rejected by the existing declaration checks.

The expanded executable top-level statements, in expansion order, form the one implicit `main`. A
top-level `val`/`var` remains a local of that implicit main, so its visibility and definite
initialization follow statement order across file boundaries. An explicit `func main` in any
participating file remains `SOLV-SEM-001`. A fully expanded program with no executable top-level
statements has no entry point and does nothing.

Module resolution, include resolution, and file reads finish before semantic analysis and lowering.
There is no runtime module or include node and no runtime file I/O. Physical file identity is
preserved: a sealed class may still be extended only in its own physical source file (section 12),
and parser, semantic, instrumentation, and runtime locations identify the file that supplied the
code.

### Required diagnostics

| Code name | Stable code | Primary span |
|---|---|---|
| `RESOL_INCLUDE_INVALID_PATH` | `SOLV-RESOL-007` | path literal |
| `RESOL_INCLUDE_NOT_FOUND` | `SOLV-RESOL-008` | include directive |
| `RESOL_INCLUDE_NOT_FILE` | `SOLV-RESOL-009` | include directive |
| `RESOL_INCLUDE_IO` | `SOLV-RESOL-010` | include directive |
| `RESOL_INCLUDE_CYCLE` | `SOLV-RESOL-011` | include directive that closes the cycle |
| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration or include directive |
| `RESOL_ALIAS_DUPLICATE` | `SOLV-RESOL-013` | include directive |
| `RESOL_ALIAS_DEFAULT_MODULE` | `SOLV-RESOL-014` | include directive |
| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |
| `SEM_SEALED_SUBTYPE_OUTSIDE_FILE` | `SOLV-SEM-039` | illegal subclass declaration |

Messages for path failures include the written path and, when one exists, the resolved candidate.
Denied access and other I/O failures become `SOLV-RESOL-010` and never escape as host errors.

## 21. Expression-Oriented Constructs

A block, an `if`, and a `switch` may be used as values. The feature is additive: assignments remain
statements, a function still requires an explicit `return` for a value, and there is no implicit
function result. `throw` remains deferred. These rules are compiled before lowering: a construct
with an error never produces an executable call target, and no runtime node repairs an invalid
construct with `null`, `Unit`, zero, `false`, an empty string, or a host sentinel.

### 21.1 Terms

A **statement block** is a brace-delimited block in statement position; its behavior is unchanged.
A **block expression** is a brace-delimited block in expression position. It has its own lexical
scope and may contain zero or more statements followed by an optional **tail expression**.

A path **completes abruptly** when it executes `return`, or a valid enclosing-loop `break` or
`continue`, before reaching the construct's result. Abrupt completion carries no value and does not
participate in result joining. A path **completes normally without a result** when it reaches the end
of a value-required body without evaluating a tail expression; that is a compile-time error. It is
distinct from evaluating a tail expression whose type is `Unit`, because `Unit` is a real type with
one value.

### 21.2 Block expressions

```solvik
val answer = {
    val base = 20
    base + 22
}

val logged: Unit = {
    println("done")
}
```

The first block has type `Int` and value `42`; the second has type `Unit`. A block expression
introduces one lexical scope. Earlier statements execute in source order, and a local declared
inside the block is visible to later items in that block and nowhere outside it.

Every normally completing path through a value-required block must reach its tail expression. An
empty block, a block ending in a local declaration, and a block ending in an assignment are invalid
in expression position and do not acquire an implicit `Unit` result:

```solvik
val invalid = {
    val local = 1
}
```

To produce `Unit`, use a tail expression whose type is `Unit`. A standalone scope block remains a
statement block, and the existing rule that an unused value-producing non-call expression cannot
stand alone still applies.

A value-required block whose every path completes abruptly has type `Nothing` and never evaluates a
tail expression.

### 21.3 Semicolons and tail expressions

Explicit and synthesized semicolons are the same parser token and have the same language meaning,
so token origin is never inspected to decide whether a value exists. All three forms below have the
same value and type:

```solvik
val a = { 42 }

val b = {
    42
}

val c = {
    42;
}
```

Each is an `Int` block expression with value `42`. Comments and blank lines before `}` do not
affect tail selection, and a terminal assignment is a statement and never a tail expression.

### 21.4 `if` expressions

An `if` may be used in expression position:

```solvik
val description = if (value < 0) {
    "negative"
} else if (value == 0) {
    "zero"
} else {
    "positive"
}
```

The condition must be `Boolean`, exactly as for statement `if`. An expression `if` must have an
`else`; a missing `else` is a dedicated compile-time error and does not also fabricate a branch-type
mismatch. Every normally completing branch must produce a tail result, and abrupt branches are
excluded from result joining:

```solvik
func requireName(name: String?): String {
    return if (name != null) {
        name
    } else {
        return "fallback"
    }
}
```

Statement-style `if` remains valid without `else`. Branch scopes are independent, and flow
narrowing, definite assignment, and write invalidation apply within each branch and at their join.
Interpretation as a statement or an expression is determined by syntax and AST context, never by
runtime behavior or an expected dynamic value.

### 21.5 `switch` expressions

The `switch` statement remains valid and unchanged. In expression position the same surface syntax
produces a value:

```solvik
val message = switch (status) {
    case Status.Ready:
        "ready"

    case Status.Running:
        "running"

    default:
        "done"
}
```

The scrutinee is evaluated exactly once. Case labels are tested in source order, only the first
matching body executes, and there is no implicit fallthrough. Every expression `switch` must contain
exactly one `default`, and it must remain last. `switch` does not gain enum or sealed exhaustiveness;
that remains the responsibility of `match`. Requiring `default` makes value production explicit for
`Int`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do
nothing when no label matches.

Every normally completing case body, including `default`, must end in a tail expression; statements
may precede it. An abrupt case contributes no result type. Existing rules for constant labels, label
assignability, multiple labels, regex labels, duplicate and default placement, and direct `break` in
a case continue to apply. Regex expression cases keep the same spelling and matching behavior:

```solvik
val kind = switch (input) {
    case regex r#"^\d+$"#:
        "number"

    case regex r#"^[A-Za-z]+$"#:
        "word"

    default:
        "other"
}
```

### 21.6 Existing `match` expressions

No new `match` form is added. Existing single-expression branches are unchanged, and because a block
is an expression a branch may use a block expression for multiple statements:

```solvik
Ok(value) => {
    println("ok")
    value
}
```

The block follows the same tail-result, semicolon, scope, abrupt-completion, and typing rules as any
other block expression. Pattern order, reachability, binding, and exhaustiveness rules are unchanged.

### 21.7 Result types

Every value-producing construct uses one shared join algorithm: the result is the nearest common
declared supertype to which every normally completing branch result is assignable, including the
existing nullability rules. No numeric promotion, structural typing, dynamic typing, implicit
conversion, or inferred union type is introduced. If exactly one branch can complete normally, its
result type is the construct's result type. If no branch can complete normally, the construct has
type `Nothing`, and no runtime value is invented for it. `Unit` participates in the join as any other
non-null value type.

```solvik
val both = if (flag) { 1 } else { "text" } // type Object
```

A set of branches whose only shared supertypes are incomparable has no single nearest result and is a
compile-time error. A path that reaches the closing brace without a tail expression is not a result
and is a compile-time error.

### 21.8 Expression contexts

Block, `if`, and `switch` expressions are accepted wherever the grammar accepts an expression,
subject to ordinary precedence and any required parentheses, including local initializers,
assignment right-hand sides, call arguments, explicit `return` values, operands and nested expression
constructs, and `match` branch results:

```solvik
var score: Int = 0
score = if (enabled) { 10 } else { 0 }

println(if (debug) { "debug" } else { "normal" })

func classify(value: Int): String {
    return switch (value) {
        case 0:
            "zero"
        default:
            "nonzero"
    }
}
```

Assignments remain statements and are not usable as tail expressions or nested values, and a function
body does not implicitly return its final expression:

```solvik
func invalid(): Int {
    42 // compile error: a value-returning function requires return 42
}
```

### 21.9 Required diagnostics

| Code name | Stable code | Primary span |
|---|---|---|
| `TYPE_BRANCH_RESULT` | `SOLV-TYPE-038` | whole `if` or `switch` expression |
| `SEM_BLOCK_RESULT_REQUIRED` | `SOLV-SEM-041` | offending block or case body |
| `SEM_IF_EXPRESSION_MISSING_ELSE` | `SOLV-SEM-042` | whole `if` expression |
| `SEM_SWITCH_EXPRESSION_MISSING_DEFAULT` | `SOLV-SEM-043` | whole `switch` expression |

`TYPE_BRANCH_RESULT` reports normally completing branches with no single nearest common declared
supertype. `match` keeps its existing result and exhaustiveness diagnostics, and existing type errors
inside a tail expression keep their existing codes.
