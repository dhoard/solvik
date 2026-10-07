# Solvik Language Specification

Specification version: `2026.11-draft` (pre-1.0 development baseline; see "Versioning" below).

Status: normative implementation baseline.

### Versioning

This document is the normative language baseline. It carries an explicit, independent
language-specification revision so that TCK releases, requirement inventories, and conformance
reports can be bound to a durable semantic identity. The identifier `2026.10-draft` is a
**pre-1.0 development revision**: it is intentionally not `1.0`, because the Maven artifact version
`1.0.0-SNAPSHOT` is a build coordinate, not a language-specification version, and the language is
not yet declared stable. A source-control commit hash may identify audit input but is not a semantic
version and grants no compatibility promise. A new specification revision is declared only when the
normative semantics change; released revisions are immutable.
Revision `2026.11-draft` fixes the vocabulary of declaration and closure. `var` is the one binding
keyword and `mutable` follows it to permit reassignment, so `val` is removed and replaced by `var`;
`open` is removed and replaced by `mutable`; `sealed` is removed and replaced by `abstract`. The
unlock markers obey one rule: no marker means locked, `mutable` unlocks, and `abstract` locks
construction while opening extension. `override` and every other keyword are
unchanged, and `final` remains a prose term for the default state rather than a keyword, which is what
it has always been. `mutable` is rejected on an `abstract` class by the grammar, because `abstract`
already grants extension and no bit remains for `mutable` to flip. The revision retires closed class
subtype hierarchies: `abstract` classes are extendable from any file, so no class type has a knowable
subtype set, `match` over a class type requires a wildcard branch, and the `2026.10-draft` sealed-prose
sections that required a closed transitive subtype set and a same-file extension boundary are
superseded. Exhaustiveness over `enum` and `error` variants is unchanged and remains the closed
sum-type mechanism. `override` is retained deliberately: `mutable` grants permission downward and
`override` asserts intent upward, and only the assertion can invalidate a stale signature, which is
what keeps a renamed or drifted supertype method from becoming a silent non-override. The removed
keywords are not reused as identifiers: a program that writes `val`, `open`, or `sealed` is reported as
`SOLV-PARS-006` naming its replacement. See `KEYWORD_CHANGES.md` for the migration and the retired
`SOLV-SEM-039`.

Revision `2026.11-draft` declares that a callable is a declaration rather than a value: there are no
function types, no function values, no anonymous functions, no closure capture lists, and no bound
method references (section 6). It supersedes the `2026.10-draft` first-class function values, and it
makes `func` a module-scope declaration while `method` declares class and interface members
(sections 6, 7, 8, and 9). Until this baseline is declared stable (`>= 1.0`), full-language
conformance reports must state that certification is withheld against a pre-1.0 specification.

`must` and `must not` define required behavior. Features explicitly marked `deferred` are not part of the language until this document defines them. An implementation must not invent semantics for a deferred or unspecified feature.

Solvik is a strongly and statically typed general-purpose language with familiar TypeScript/Kotlin-like syntax, explicit mutability, safe object-oriented defaults, composition/delegation, controlled inheritance, null safety, exhaustive pattern matching, first-class regular expressions, Rust-style raw strings, and physical-line statement termination.

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

Identifiers use `[A-Za-z_][A-Za-z0-9_]*`; keywords are reserved and `$` is not an identifier character. `//` starts a line comment. `/* ... */` is a non-nesting block comment. Comments are otherwise whitespace, but a newline inside a comment is still a physical newline for statement termination (section 16).

`mutable` and `abstract` are keywords and each opens a construct, so neither ever ends a line
(section 16). The keywords removed by this revision — `val`, `open`, and `sealed` —
are not identifiers: a program that writes one is reported as `SOLV-PARS-006` on that token, naming the
replacement. Reserving them keeps a removed keyword from silently changing what an existing program
means, which is what would happen if `val x = 1` became an assignment to a variable named `val`.

Decimal integer literals contain ASCII digits and have type `Integer` in the initial typed core. A literal outside the signed 32-bit range is a compile-time error until additional literal forms are specified.

Phase 7 adds `L`-suffixed `Long` literals and decimal floating-point literals with an optional exponent. Floating-point literals have type `Double`; an `F` suffix selects `Float`. `Byte` and `Short` values use explicit conversion.

A character literal uses single quotes and contains exactly one Unicode scalar value or one of the escapes supported by normal strings, for example `'A'` or `'\n'`.

## 2. Variables and Mutability

`var` declares an immutable binding/property.

```solvik
var name: String = "Doug"
var count: Integer = 1
```

Reassignment is illegal:

```solvik
var count: Integer = 1
count = 2 // compile error
```

`var mutable` declares a mutable binding/property.

```solvik
var mutable count: Integer = 0
count = count + 1
```

No other marker declares a binding, and `var` is the only binding keyword: a binding is immutable
unless `mutable` follows it. `mutable` is a modifier on the declaration, never a binding kind of its
own, so `var mutable` is the complete form and a bare `mutable` is a compile-time error. The canonical
forms are `var name: Type = expression` and `var mutable name: Type = expression`: the declaration
keyword comes first and the modifier that permits reassignment follows it. Every local declaration
writes an initializer and its type, and no type is ever inferred from the initializer.

Reassignment uses ordinary `=` assignment on an existing binding and is legal only when the binding
was declared `mutable`:

```solvik
var mutable count: Integer = 0
count = 1
count = count + 1
```

`var` freezes the binding, not the complete reachable object graph, and neither form is a compile-time
constant: the initializer is an ordinary runtime expression. That is why the writable form is not
named `const`, which is reserved for future compile-time constants.

```solvik
class User {
    var mutable name: String

    User(name: String) {
        this.name = name
    }
}

var user: User = User("Doug")
user.name = "Douglas" // valid
user = User("Other")  // compile error
```

## 3. Static and Strong Typing

Solvik uses nominal static typing.

Two unrelated classes with identical members are not assignment-compatible.

```solvik
class A {
    var value: String
}

class B {
    var value: String
}

var a: A = B("x") // compile error
```

`Any` must never behave like TypeScript's `any`. Assigning a value to `Any` does not disable type checking.

```solvik
var x: Any = "hello"
var n: Integer = x // compile error
```

A checked cast or type refinement is required.

Assignments are statements, not value-producing expressions. The target must be a `var mutable` local or a `var mutable` property. Calls require exact arity, and each argument must be assignable to its declared parameter type.

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

`&&` and `||` short-circuit and require `Boolean` operands. Unary `!` requires `Boolean`. The initial arithmetic and ordering operators require a numeric operand; operands of the same numeric type produce that type (or `Boolean` for ordering), and mixed numeric operands are widened as defined in section 4. Integral division truncates toward zero and division by zero raises a Solvik runtime arithmetic error. `..` concatenates: both operands are rendered through `toString` and the result is always `String`, so `1 .. "x"` is `"1x"` and `"x" .. null` is `"xnull"`. Concatenation binds looser than arithmetic, so `a + b .. c` is `(a + b) .. c`, and it is left-associative. Solvik performs no other implicit conversion to `String`.

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
other; the result type is `Boolean`. Two `Integer` values are comparable, a `Point` and `Any` are
comparable, and unrelated nominal classes are not directly comparable even though `equals` accepts
`Any?`. A caller that intentionally wants an arbitrary comparison may use an `Any`-typed value or
call `equals` explicitly.

#### The universal equality member

Every non-null value has the built-in member:

```solvik
method mutable equals(other: Any?): Boolean
```

It is a language-defined universal member, not operator overloading, and its explicit call and `==`
use the same semantic equality definition:

```solvik
value.equals(other)
value == other
```

A user class may declare exactly:

```solvik
method override equals(other: Any?): Boolean
```

The compiler requires `override`, exactly one explicit parameter typed exactly `Any?`, and return
type exactly `Boolean`. The inherited root member is mutable, and an override follows the ordinary
`mutable`/final rules for further subclasses. An interface cannot redeclare `equals`, and a property
or delegate cannot use the reserved name `equals`. A direct call on a nullable receiver follows
ordinary nullable-member rules: `value?.equals(other)` is safe and has result `Boolean?`, while
`value.equals(other)` is an error when `value` may be null. Built-in scalar, enum, and
reference-backed built-in implementations are fixed and cannot be overridden. A bare `value.equals`
member read is invalid, exactly like a bare `value.toString` read.

#### Semantic equality algorithm

After both operands have been evaluated, `left == right` performs these steps in order:

1. if both values are `null`, the result is `true`;
2. if exactly one value is `null`, the result is `false` and no user code runs;
3. else, if the left value is a built-in scalar, its fixed rule below applies;
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
| `Byte`, `Short`, `Integer`, `Long` | same-type integral value |
| `Float`, `Double` | same-type IEEE 754 `==` |
| `Boolean` | Boolean value |
| `Character` | character value |
| `String` | character-sequence content |
| `List`, `Set`, `Map`, `Stack` | reference identity |
| Function value | reference identity |
| `Regex` | exact pattern source text |
| `RegexMatch` | immutable snapshot: `value`, `start`, `end`, `groupCount`, and every captured group |

These rules compare two values already of the same built-in type; `==`/`!=` between two numeric
operands of different types first widen both to their least common widened numeric type (section 4),
so `1 == 1L` compares as `Long` and `1.5f == 1.5` compares as `Double`.

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

#### The universal hash member

`Any` declares:

```solvik
method mutable hashCode(): Integer
```

It is the hash companion of `equals` and follows the same structural rules: a language-defined
universal member available on every non-null value, declared by a user class only as exactly
`method override hashCode(): Integer`, requiring `override`, no parameters, and return type exactly
`Integer`. An interface cannot redeclare `hashCode`, and a property or delegate cannot use the
reserved name `hashCode`. A bare `value.hashCode` member read is invalid, exactly like a bare
`value.equals` or `value.toString` read, and a call on a possibly-null receiver must use `?.`,
producing `Integer?`.

#### The equals/hashCode pairing rule

A class that declares `method override equals` must also declare `method override hashCode` in the same
class declaration, and a class that declares `method override hashCode` must also declare
`method override equals`. Each violation is a compile-time error reported on the single unpaired
member, so a class missing one of the two produces one diagnostic.

The rule is checked per declaration and is never satisfied by inheritance. An inherited `hashCode`
is precisely the hazard the pairing exists to remove: a subclass that adds equality-relevant fields
and overrides `equals` would otherwise inherit a `hashCode` that ignores them. Java reports this
only as a warning because it cannot see the declaration; Solvik can, so it rejects it.

A class that overrides neither member inherits both root defaults, which is valid because both
root defaults are reference identity. Inheritance therefore satisfies the pairing: a subclass of a
class that overrides both members needs no override of its own.

#### Semantic hash algorithm

`value.hashCode()` mirrors the structure of the semantic equality algorithm, so a hash can never
drift from equality:

1. if the value is `null`, the result is `0`;
2. else, if the value is a built-in scalar, its fixed rule below applies;
3. else, if the value is an enum value, the variant identity is combined with the semantic hash of
each payload, left to right;
4. else, if the value is a user-defined class instance, its effective `hashCode()` override
dispatches dynamically; when no class in its hierarchy overrides `hashCode`, the root default is
the reference identity hash;
5. else the fixed rule for the remaining built-in value below applies.

**Invariant.** When `left == right` is `true`, `left.hashCode() == right.hashCode()` is `true`. The
converse is not required: unequal values may share a hash.

The fixed built-in rules read exactly the fields the equality table reads:

| Type | Semantic hash |
|---|---|
| `Byte`, `Short`, `Integer`, `Long` | hash of the integral value |
| `Float`, `Double` | hash of the value, with negative zero folded onto zero (see below) |
| `Boolean` | a fixed value per `true`/`false` |
| `Character` | hash of the character value |
| `String` | hash of the character-sequence content |
| `List`, `Set`, `Map`, `Stack` | reference identity hash |
| Function value | reference identity hash |
| `Regex` | hash of the pattern source text |
| `RegexMatch` | hash of the immutable snapshot fields, with a null group distinct from an empty one |

Solvik floating equality is IEEE `==`, under which `0.0` equals `-0.0` while `NaN` is unequal to
everything including itself. Boxed Java hashing separates `0.0` from `-0.0`, so the hash folds
negative zero onto zero to stay consistent with equality. Two `NaN` values hash alike, which is
permitted: they are unequal, and the invariant constrains only equal values.

`super.hashCode()` behaves like `super.equals(other)`. When a superclass in the hierarchy supplies
an override, the call reaches that override. When none does, the call reaches the root identity
default and does not re-dispatch to the current class's own override, which would recurse.

#### Reference identity

`===` answers whether two values are the same Solvik allocation. It never invokes `equals`, another
guest method, or Java `Object.equals`. `!==` is its exact logical negation.

The identity-bearing static types are exactly:

- user-defined class types, including abstract classes and parameterized class applications;
- interface types, including parameterized interface applications;
- `List<T>`, `Set<T>`, `Map<K, V>`, and `Stack<T>`;
- nullable forms of the preceding types.

The following types are not identity-bearing: `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`,
`Boolean`, `Character`, and `String`; enum types; `Regex` and `RegexMatch`; `Any`;
unbounded type parameters; `Nothing` and a bare null literal. A value held in `Any` must
first be narrowed or checked-cast to an identity-bearing type, which prevents a JVM representation
choice from becoming observable when the runtime value is a scalar, string, enum, or regex.

Identity operands must also satisfy the ordinary equality comparability rule: one operand type must
be assignable to the other. After removing nullability, at least one operand must establish an
identity-bearing type and the other must be compatible with it. A null literal is permitted only
against a nullable identity-bearing operand, so `null === null` is a compile error. A failure of
assignability uses the ordinary invalid-operand diagnostic; a compatible pair with no identity-bearing
operand uses `SOLV-TYPE-039`.

```solvik
var a: Point = Point(1, 2)
var b: Point = Point(1, 2)
var c: Point = a

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
├── Number
│   ├── Byte
│   ├── Short
│   ├── Integer
│   ├── Long
│   ├── Float
│   └── Double
├── Boolean
├── Character
├── String
├── Regex
├── RegexMatch
├── List<T>
├── Set<T>
├── Map<K, V>
├── Stack<T>
└── user-defined classes / interfaces / enums
```

Built-in types may use compiler/runtime-defined inheritance regardless of user-visible restrictions.

Phase 4 implements `Integer` as the initial numeric type. `Byte`, `Short`, `Long`, `Float`, and `Double` are reserved built-in names and become usable when the complete root hierarchy is implemented in Phase 7.

**Implicit widening.** A numeric value converts implicitly to a wider numeric type only when the conversion loses neither integral range nor representable precision. This *widening* relation holds for exactly:

- integral to integral along `Byte` `->` `Short` `->` `Integer` `->` `Long`;
- `Byte` or `Short` to `Float`, and `Byte`, `Short`, or `Integer` to `Double` (a value with at most 32 significant bits fits the 53-bit `Double` significand, and at most 15 bits fit the 24-bit `Float` significand);
- `Float` to `Double`.

No other conversion is implicit. `Integer` does not widen to `Float` (the 24-bit `Float` significand cannot hold every `Integer`), and `Long` widens to neither `Float` nor `Double` (64 bits exceed the 53-bit significand). No narrowing is implicit. Every other conversion, including all narrowing and every precision-losing conversion, uses an explicit built-in type call such as `Long(value)`; an out-of-range integral conversion raises a Solvik runtime arithmetic error and an out-of-range constant conversion is a compile-time error. Because a widening never overflows or loses precision, an implicit widening introduces no new runtime arithmetic error.

Widening is a coercion applied at conversion sites, not a subtype relation: numeric types remain siblings under `Number`, nominal assignment, generics, hashing, type tests, and casts are unchanged, and a widening never appears in a type join (section 21.7). A widening is applied where an expression must match a declared target type (a `var` or property initializer, a function or method argument, a `return`, an assignment, or a collection element/key/value) and, for arithmetic, ordering, and equality operators, by widening each operand to the least common widened numeric type of the two operands — the unique minimal type both operands can widen or stay to. When two numeric operands have no such common type (for example `Long` and `Float`) the operator is ill-typed. Widening never applies to identity operators (`===`/`!==`), which remain governed by section 3.

Integral arithmetic is checked and raises a Solvik runtime arithmetic error on overflow. `Float` and `Double` follow IEEE 754 arithmetic. Arithmetic operands are widened as described above and produce their common widened type; operands of the same type produce that type.

`Any` is the sole top type for every non-null Solvik value, including every class, interface, and enum value. `Nothing` is a subtype of every type.

`Any` declares the universal members `method toString(): String`, `method mutable equals(other: Any?): Boolean`, and `method mutable hashCode(): Integer` (section 3). They are available on every non-null value. Built-in scalars provide fixed, non-overridable implementations: `Integer`, `Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style floating-point text, `Boolean` renders `true` or `false`, `Character` renders its character, and `String` renders its contents. A built-in scalar cannot be extended and its `toString` cannot be overridden. A user-defined class inherits the default representation (its class name) and may declare `method override toString(): String` for a class-specific representation (section 7).

A callable that completes without producing a value has no value to represent: its result is not a
type in this hierarchy, has no members, and is not a subtype or supertype of anything. `Nothing` is
the bottom type and has no values.

Guest exceptions form a nominal reference hierarchy rooted at `Exception`:

```text
Exception
├── RuntimeException
└── ApplicationException
```

`Exception`, `RuntimeException`, and `ApplicationException` are predeclared nominal class types with
no source declaration. User-defined exception classes derive from `RuntimeException` or
`ApplicationException` (or from a further subclass). Guest exceptions are unchecked: a function need
not declare the exception types it may throw, and callers are not forced to handle them. The
`throw`/`try`/`catch`/`finally` constructs and the program-boundary rules are specified in section 22.

## 5. Nullability

Types are non-null by default.

```solvik
var name: String = "Doug"
var bad: String = null // compile error
```

`T?` denotes a nullable type.

```solvik
var name: String? = null
```

`null` is assignable only to nullable types. If `S` is a subtype of `T`, then `S` is assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null `T`.

Safe member access:

```solvik
var rendered: String? = name?.toString()
```

Null coalescing:

```solvik
var display: String = name ?? "Unknown"
```

For `receiver?.member`, the member is evaluated only when the receiver is non-null and the result type is the member type made nullable. For `left ?? right`, `left` must be nullable; the result is the common type of non-null `left` and `right`.

Flow-sensitive narrowing is required:

```solvik
if (name != null) {
    print(name) // name is String here
}
```

The reference-identity null tests `name === null` and `name !== null` narrow the same way.

Narrowing is permitted only when the analyzed value cannot be written or invalidated along that control-flow path. A write to a `var mutable` invalidates its prior narrowing.

## 6. Functions

Preferred syntax:

```solvik
func add(a: Integer, b: Integer): Integer {
    return a + b
}
```

Parameter types must be explicit. A callable's return type is written only when the callable produces a value: a declaration that writes no `: Type` produces no value at all, and no source type names that result. There is no `Unit` type in the language, and a written `: Unit` is an unknown type (`SOLV-RESOL-003`). A local declaration always writes its type as well; nothing is inferred from an initializer.

A callable that completes normally without a value produces no value, and that result has no source spelling: it cannot be bound, passed, returned, printed, compared, or joined into a type. `Nothing` remains the bottom type for computations that never complete normally.

A callable is a declaration, never a value. There are no function types, no function values, no anonymous functions, no capture lists, and no bound method references; a callable cannot be assigned, stored in a property or collection, passed as an argument, returned, compared, or displayed. The only way to run a callable is to call it, and the callee of a call names the declaration rather than denoting a value.

The program scope contains declarations and executable statements, which may be interleaved freely. When the root source uses compile-time inclusion (section 20), the declarations and statements of every expanded file participate in this one program; a file that declares a `module` places its top-level declarations in that module's namespace, and an included module may be referenced through a namespace prefix (section 20). The top-level statements, in include-expansion order, form the body of an implicit `func main()`; a top-level `var`, whether or not it is `mutable`, is therefore a local of the implicit main, not a global. Declaration lookup remains order-independent within a module, so a declaration may be referenced from a physically earlier file or statement. The entry point is always implicit: declaring a function named `main` explicitly, in the root or in any included file, is a compile-time error. A program with no executable top-level statements has no entry point and does nothing. A call may be used as a statement. Other value-producing expressions cannot stand alone as statements. `return;` is valid only in a function declared without a return type; `return value` requires the value to be assignable to the declared return type.

Functions are not overloaded in the initial language: two functions with the same name in one scope are a compile-time error. The executable entry point is the implicit `main` formed by the program's executable top-level statements. Command-line argument binding is deferred. A program that reaches the end of its entry point exits with status `0`; the predeclared `exit(code: Integer)` function terminates the program immediately with the given status.

Names use lexical scope. Redeclaration in the same scope is an error. A nested block may shadow an outer declaration. A local variable must be definitely initialized before it is read.

### Scope blocks

A brace-delimited block may stand alone as a statement. A scope block introduces a new lexical scope for the statements it contains; sibling blocks are independent scopes, so the same local name may be declared in each without any shadowing between them.

```solvik
{
    var result: String = parseHeader()
    handleHeader(result)
}

{
    var result: String = parseBody()
    handleBody(result)
}
```

A scope block is neither a loop nor a function boundary: `break`, `continue`, and `return` inside it apply to the enclosing loop or function. A block nested inside another block may still shadow an outer declaration, exactly like the body of an `if`, `while`, or `for`.

The initial predeclared I/O functions are `print(value: Any?)` and `println(value: Any?)`. Both accept every value including `null`; `null` displays as `null`. A value displays as its `toString()` representation (section 4): strings and characters as their contents, numbers in decimal or Java-style floating-point text, Boolean values as `true` or `false`, and an ordinary object as its class name unless the class overrides `toString`. Because display is defined by `toString`, a class override is honored by `print`, `println`, and `..`. `println` appends the platform line separator. The predeclared `exit(code: Integer)` function runs no further Solvik code: it terminates the program with `code` as the process exit status and returns no value. Input APIs are deferred.

### Callable arity

The number of explicit arguments supplied to a statically resolved callable must satisfy the callable's parameter requirements. Argument-count validation occurs during semantic analysis: the parser only recognizes an argument list, and an arity mismatch is a source-located compile-time error that prevents the program from being lowered or executed.

For a callable declared with only required parameters, the required count is the number of declared parameters:

```solvik
func add(a: Integer, b: Integer): Integer {
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

The receiver of an instance method is not an explicit argument and does not contribute to source-level arity. In `user.setName("Doug")`, a method declared as `method setName(name: String)` has source-level arity `1`. Constructors, interface methods, and built-in functions follow the same rule. The predeclared `print`, `println`, and `exit` functions each declare exactly one parameter, so a call that supplies a different number of arguments is a compile-time error; built-ins participate in the ordinary resolved-callable model rather than receiving separate arity rules.

A statically resolved call's arity is verified before its argument types and before generic type-argument inference. A call with the wrong number of arguments therefore reports an arity error rather than a misleading argument type error, and the incorrect count suppresses the argument type checks and inference for that call.

The initial language has no default parameters and no variadic parameters, so every callable has
exactly one permitted argument count. A runtime arity check remains only as an internal invariant:
source programs cannot reach it because an invalid count is rejected during semantic analysis.

### Callable declarations: `func` and `method`

A callable declaration writes its keyword, an optional modifier sequence, its name, an optional type
parameter list, its parameter list, an optional `: Type` return type, and its body.

```solvik
func add(a: Integer, b: Integer): Integer {
    return a + b
}

func log(message: String) {
    print(message)
}
```

`func` declares a free function, and a free function is declared at module scope: directly in a file
or directly inside a `module Name { ... }` block. A `func` inside a class or interface body is
rejected (`SOLV-PARS-013`), and the diagnostic names `method` as the replacement.

`method` declares a class or interface member (sections 7 and 8). A `method` outside a class or
interface body is rejected (`SOLV-PARS-001`): a module-scope member is written `func`.

```solvik
class Counter {
    var mutable value: Integer = 0

    method bump(by: Integer) {
        this.value = this.value + by
    }
}
```

Neither keyword declares a nested callable: a callable declaration is never a statement, so a
declaration inside a callable body is a parse error.

A callable declaration whose body produces no value writes no `: Type`. No other spelling exists, and
no expression has that result as its type, so the result cannot be used as a value:

```solvik
func log(message: String) {
    print(message)
}

log("a")                    // valid: a call in statement position
var x: Integer = log("a")   // compile error: the call produces no value
```

### Return statements

`return` with no value is valid only in a callable that writes no `: Type`; `return value` requires
the value to be assignable to the declared return type. A callable that writes no `: Type` and falls
off the end of its body completes normally and produces no value.

A `return` with a value in a callable that writes no return type is `SOLV-TYPE-011`, and a bare
`return` in a value-returning callable is `SOLV-TYPE-010`, reported on the return statement. A
value-returning callable whose control flow can reach its end without returning is `SOLV-TYPE-010`
as well, reported on the callable.

### Equality, identity, hashing, and display

A callable declaration has no value, so no callable can be compared, hashed, or displayed. The
built-in scalars, enum values, `Regex` and `RegexMatch` values, and class instances keep the
equality, identity, hashing, and display rules of sections 3 and 4 unchanged.

### Type tests, casts, and other constructs

A type test or cast cannot name a callable: `x is f` and `x as f` are rejected when `f` is a
function, and a method name can never be written in a type position.

### Required diagnostics

| Code name | Stable code | Trigger and primary span |
|---|---|---|
| `PARSER_REMOVED_DECLARATION` | `SOLV-PARS-013` | a declaration shape the revision retired (a `func` written as a class or interface member, a local declaration without a type, `delegate var`, a file-level `module` header, an `include ... alias ...` suffix, a modifier written before its construct keyword, or a modifier sequence in a non-canonical order); the retired shape |
| `PARSER_REMOVED_FUNCTION_VALUE` | `SOLV-PARS-014` | function-value syntax (a function type reference, or an anonymous function expression with or without a capture list); the retired expression or type |
| `TYPE_FUNCTION_AS_VALUE` | `SOLV-TYPE-014` | a callable name is used as a value (a bare reference to a function or method, or a callable written in an initializer, argument, return, comparison, or display position); the reference |

A generic function or generic method name used as a value is `TYPE_FUNCTION_AS_VALUE`
(`SOLV-TYPE-014`) as well: a declaration denotes no value, so there is nothing to instantiate.
Section 3 and section 23.4 retain their existing bare-member-read rejections unchanged.

## 7. Classes

Classes are final by default. A class that is not declared `mutable` or `abstract` cannot be extended,
and a method that is not declared `mutable` cannot be overridden: `mutable` unlocks, and no marker
means locked.

A class modifier follows the `class` keyword, and a class declares at most one of them: `class mutable
Name` opens the class for extension, `class abstract Name` makes it abstract, and `class Name` is
final. A modifier written before the keyword, or both modifiers together, is rejected
(`SOLV-PARS-013`).

A class and interface member is declared with `method`, and a member modifier follows that keyword in
one canonical order: `method static name(...)`, `method override name(...)`, `method mutable
name(...)`, and `method override mutable name(...)`. A property writes `var static name: Type` or
`var static mutable name: Type`, and the class initializer block keeps the spelling `static { ... }`.
A member modifier written before its construct keyword, or a modifier sequence in any other order, is
rejected (`SOLV-PARS-013`) rather than reordered silently.

```solvik
class User {
    var id: Long
    var mutable name: String

    User(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

A property is referenced through `this`. Inside a method or constructor a bare name never resolves to a
property: it denotes a local, a parameter, a function, or a top-level declaration, and if none is visible
the reference is `SOLV-RESOL-001`. `this.name` resolves against the enclosing class's properties including
inherited ones, and a local may shadow a property name without either reference becoming ambiguous.
`this` outside an instance method or constructor is `SOLV-RESOL-005`.

A class must explicitly opt into inheritance. `mutable` opens a class for extension by anyone:

```solvik
class mutable Animal {
    method mutable speak(): String {
        return "..."
    }
}
```

An `abstract` class (section 12) is also extendable, and is the other way a subclass may legally name a
superclass:

```solvik
class abstract Shape {
    Shape() {
    }
}

class Square extends Shape {
}
```

Extending a class that is neither `mutable` nor `abstract` is `SOLV-SEM-008`.

Single inheritance only:

```solvik
class Dog extends Animal {
    method override speak(): String {
        return "woof"
    }
}
```

Multiple class inheritance is forbidden.

A class with no written superclass derives directly from `Any`. Writing `extends Any` is the explicit spelling of direct root derivation: it does not create a source class symbol for `Any`, does not introduce an `Any` constructor, and does not make `super(...)` or `super.member` available. `Any` is the only non-user-defined superclass target accepted by `extends`.

Overrides must always use `override`.

Members are not overridable unless the declaration permits it.

The initial language has no visibility modifiers; declared members are externally accessible. Object storage remains encapsulated behind declared properties, and undeclared member access is illegal.

A class declares its constructor as a class member whose name is the class name, with a parameter list and a body, and without the `func` keyword or a return type:

```solvik
class User {
    var id: Long
    var mutable name: String

    User(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

Calling the class name invokes its constructor. A class has at most one constructor declaration. Every property without a declaration initializer must be assigned exactly once on every successful constructor path before it is read; a `var` property cannot be assigned afterward.

A constructor is not a method. It is not inherited, cannot carry `mutable` or `override`, is not declared by an interface, is not forwarded by a `delegate`, and cannot be invoked as `this.User(...)`. For a generic class `Box<T>`, the constructor is named `Box`, not `Box<T>`. A class member declaration other than the constructor cannot have the same name as its class.

A class with no explicit constructor has an implicit zero-argument initializer only when all properties have declaration initializers. A subclass constructor must invoke `super(arguments)` as its first statement when the superclass has no zero-argument initializer; otherwise `super()` is implicit. `super.member` accesses the immediate superclass implementation.

An overriding method must have exactly the inherited parameter types and may return a subtype of the inherited return type. A `mutable` member may be overridden; all other members are final. An override of a `mutable` member is itself final unless it is declared `mutable` as well, which is how an override re-opens the chain for one more level.

The inherited `Any.toString()` is a mutable member, so a class may declare `method override toString(): String` for a class-specific string representation. Because the built-in member is always inherited, declaring `toString` without `override`, changing its parameter list, or returning a type other than `String` is a compile-time error, and a stored member may not reuse the reserved name `toString`.

### Static members and class initialization

`static` is a reserved keyword. It marks a member as belonging to the class itself rather than to each
instance. A class-level member is written `static` followed by a property or method declaration, and a
class may additionally declare one class initializer block, which is `static` followed by a block. Only
these three forms exist: `static delegate`, a static constructor, and a static member of an interface or
enum are parse errors rather than semantic ones.

```solvik
class Counter {
    var static limit: Integer = 10
    var static mutable attempts: Integer = 0

    method static reset() {
        Counter.attempts = 0
    }

    static {
        Counter.attempts = 1
    }

    var id: Integer = 1
}
```

A static member is referenced through the class name: `Counter.limit`, `Counter.attempts = 5`, and
`Counter.reset()`. The class name in that position is a receiver, not a value: it is legal only as the
root of a static member reference, and a class name used anywhere else remains `SOLV-TYPE-016`. In
particular `var c: Counter = Counter` and a read through an instance such as `instance.limit` are rejected. A
module-qualified class reaches the same members, as in `math::Counter.reset()`.

The class name is required even inside the class's own static members: a static property is read and
written as `Counter.attempts`, never as a bare `attempts`, because a bare name never resolves to a
property of any kind. A static *method* of the same class may be called unqualified from inside a static
method or class initializer block, in the same way an unqualified call to a top-level function is legal.

A static member is **not inherited** and is **not overridable**. It is reached only through the name of
the class that declares it, so a superclass and a subclass may each declare a static member of the same
name as two independent members, and a subclass does not expose its superclass's static members. A
static member never enters the virtual dispatch table and never participates in `delegate` forwarding or
interface conformance. Declaring `mutable` or `override` on a static member is `SOLV-SEM-047`.

A static member has no receiver. `this` and every `super` form are rejected inside a static method body
and inside a class initializer block: `this` is `SOLV-RESOL-005` and `super` is `SOLV-RESOL-006`. An
unqualified call inside either context resolves only among the static methods of the same class; naming
an instance method there is `SOLV-RESOL-001`, because the call would need a receiver that does not exist.

A static member may not mention a type parameter of its enclosing class, which is `SOLV-SEM-048`: the
member is reached through the bare class name, where no instantiation of that parameter exists. A static
method may declare and use its own type parameters.

A static member shares the class's member namespace: a static property and a static method may not reuse
the name of an instance property, an instance method, or another static member of the same class, which
is `SOLV-RESOL-002`. A static member is still subject to the rule that a member may not be named after
its class.

The `toString`/`equals`/`hashCode` reserved-name rules apply to **instance** members only, so a static
member may use those names. Those names are reserved to protect the universal `Any` members, which are
instance members reached through virtual dispatch; a static member never enters the dispatch table, so a
`method static toString()` cannot replace `Any.toString()` any more than an instance method of another name
can, and `instance.toString()` keeps reaching the universal member. Both spellings are reachable at once:
for a class declaring `var static toString: Integer` and inheriting the default `Any.toString()`, the
expression `C.toString` reads the static cell and `instance` formatting still calls `Any.toString()`.

A class declares **at most one** class initializer block. A second block is `SOLV-SEM-046`, reported on
the later block. The block holds statements, not declarations; a bare `return` exits it early, and a
`return` with a value is `SOLV-TYPE-011` because the block returns nothing.

Static storage is one cell per static property per class, separate from every object's property storage.
Each cell begins at its declared type's zero value — `0` for every integer type, `0.0` for
`Float`/`Double`, `false` for `Boolean`, the NUL character `'\0'` for
`Character`, and `null` for every reference type — whether or not the declaration supplies an
initializer, so a static property needs no initializer and a class holding one still has an
implicit zero-argument constructor. A static declaration initializer that is not assignable to the
declared type is `SOLV-TYPE-001`.

A class is initialized **lazily, on its first active use**, exactly as in the initialization model this
language's static members follow. A class is *actively used* by the first of these that executes:

- reading or writing one of its static properties, or calling one of its static methods;
- constructing one of its instances.

On the first active use, and before that use reads any cell or evaluates any call argument, the class
runs its initializer once, and only once, in this order:

1. its direct superclass is initialized first, transitively up to the root, so a base class is always
   set up before a derived one that relies on it;
2. within the class, the static property declaration initializers in source order, then the class
   initializer block.

A class that is never actively used is never initialized: an unused class's `static` block does not run,
and its static cells keep their type defaults. Because initialization is triggered by use rather than by
a fixed program-start schedule, the result depends only on the dependency graph between classes, never
on the order in which the classes happen to be declared. Initializing a class that is already being
initialized — an initialization cycle — returns immediately and lets the in-progress class observe the
still-default values of the cells it has not yet assigned, rather than looping.

Reading a static member, writing one, calling a static method, and construction are the only triggers;
a type test such as `instanceof`, a `match` on a class pattern, and merely naming a type as a declared
variable type are not. After the first active use, the guard is a single boolean test and costs nothing.

| Code name | Stable code | Reported for |
|---|---|---|
| `SEM_DUPLICATE_STATIC_BLOCK` | `SOLV-SEM-046` | a class declares more than one class initializer block |
| `SEM_INVALID_STATIC_MODIFIER` | `SOLV-SEM-047` | a static member declared `mutable` or `override` |
| `SEM_TYPE_PARAMETER_IN_STATIC_MEMBER` | `SOLV-SEM-048` | a static member mentions a type parameter of its class |

## 8. Interfaces

Interfaces define nominal contracts and may have default method implementations.

```solvik
interface Named {
    method name(): String

    method greeting(): String {
        return "Hello " .. name()
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

Delegation removes forwarding boilerplate. A delegate is declared with the `delegate` keyword, a
name, and an interface type: `delegate name: InterfaceType`. The `delegate` keyword is followed
directly by the name, so the retired `delegate var name: Type` spelling is rejected
(`SOLV-PARS-013`) rather than read as a property.

```solvik
interface UserRepository {
    method find(id: Long): User?

    method save(value: User)
}

class UserService implements UserRepository {
    delegate repository: UserRepository

    UserService(repository: UserRepository) {
        this.repository = repository
    }
}
```

The compiler synthesizes forwarding behavior for interface members supplied by a delegate. A delegate is an immutable, explicitly typed property that must be initialized under the normal constructor rules. The declared type of a delegate property must be an interface type written without type arguments: forwarding is synthesized from the declared interface contract, so a delegate whose type annotation instantiates a generic interface is rejected as `SOLV-SEM-025` (`SEM_INVALID_DELEGATE_TYPE`).

Explicit methods declared on the class take precedence over delegated members.

Ambiguous delegation must be a compile-time error.

```solvik
class X implements Printable {
    delegate a: PrinterA
    delegate b: PrinterB

    // compile error if both supply print() and X does not explicitly resolve it
}
```

## 10. Built-in Types and Runtime Representation

Language-level primitive types are class types conceptually, but the runtime may specialize them to efficient JVM/Truffle primitive representations.

For example:

```text
Integer / Long -> primitive integral representations where profitable
Boolean    -> boolean
Double     -> double
```

The language object model must not force unnecessary boxing.

## 11. Generics

Solvik supports nominal generics.

```solvik
class Box<T> {
    var mutable value: T
}

var names: List<String> = List("a", "b")
```

Generic type arguments are invariant. The initial runtime uses erasure while preserving complete compile-time checking. A runtime type test against a non-reified type argument is a compile-time error.

`List<T>`, `Set<T>`, `Stack<T>`, and `Map<K, V>` are the initial built-in mutable collection types. They are nominal generic types deriving from `Any`; their type arguments are invariant and erased at runtime.

A collection is constructed with a class-style call. The type arguments may be written explicitly
(`List<Integer>(1, 2, 3)`) or omitted to infer them from the declared type of the left-hand side
(`var names: List<String> = List("a", "b")`); a construction that writes neither is a compile-time
error. A call with no value arguments constructs an empty collection (`List<Integer>()`).

For `List`, `Set`, and `Stack`, the value arguments are the initial elements and each must be
assignable to the element type; `Set` keeps only the first of equal elements. `Map` takes
`key: value` entries, each key assignable to `K` and each value assignable to `V`; a repeated key
keeps its position and takes the latest value. A `key: value` entry is meaningful only in a `Map`
construction, and a positional value is not valid in a `Map` construction.

* `List<T>`: `var isEmpty: Boolean`, `var size: Integer`, `method add(element: T)`, `method get(index: Integer): T`,
  `method removeAt(index: Integer): T`, `method set(index: Integer, element: T)`, `method clear()`. An invalid index
  raises a Solvik runtime bounds error.
* `Set<T>`: `var isEmpty: Boolean`, `var size: Integer`, `method add(element: T): Boolean`,
  `method contains(element: T): Boolean`, `method remove(element: T): Boolean`, `method clear()`.
* `Map<K, V>`: `var isEmpty: Boolean`, `var size: Integer`, `method put(key: K, value: V)`,
  `method get(key: K): V`, `method containsKey(key: K): Boolean`, `method remove(key: K): Boolean`,
  `method clear()`. `get` for a missing key raises a Solvik collection error.
* `Stack<T>`: `var isEmpty: Boolean`, `var size: Integer`, `method push(element: T)`, `method peek(): T`,
  `method pop(): T`, `method clear()`. `peek` and `pop` on an empty stack raise a Solvik collection error.

Collection literals beyond a constructor call, iteration protocols, and collection variance remain
deferred.

## 12. Enums, Abstract Classes, and Exhaustive Match

Enums may carry values.

```solvik
enum Result<T, E> {
    Ok(T)
    Err(E)
}
```

The `Result` enum shown here is the conventional success/failure carrier. When a program declares a
two-parameter enum named `Result`, a value of that type `Result<T, E>` receives the synthesized
`Result` operations specified in section 23. Those operations identify the success and error payloads
positionally — the first variant (`Ok`) carries the success payload of type `T`, the second variant
(`Err`) carries the error payload of type `E` — so they apply to this declaration unchanged, and the
second variant's name is not significant to them.

Enum variants are nested nominal constructors. Outside a context that already establishes the enum type, qualify them as `Result.Ok(value)`. Inside a `match` over a known enum, `Ok(value)` is permitted.

`match` is expression-oriented and exhaustive where the compiler knows a closed variant set. Each
branch result is an expression; because a block is an expression (section 21), a branch may use a
brace-delimited block for multiple statements followed by a tail result.

```solvik
var message: String = match result {
    Ok(value) => "value=" .. value
    Err(error) => "error=" .. error
}
```

Missing a known enum variant is a compile-time error unless a wildcard pattern handles it.

`abstract` declares a class that cannot be constructed and exists to be extended.

An `abstract class` is not constructible: naming it as a constructor is `SOLV-SEM-028`, and a program
must construct one of its subtypes instead. It may declare a constructor, which a subclass reaches
through `super(...)`. `abstract` grants extension, so `mutable` has no bit left to flip on it and
`class abstract mutable` and `class mutable abstract` are both parse errors rather than semantic ones.
An `abstract` class may be extended from any file, including a file brought in by `include`.

A closed variant set is known only for `enum` and `error` types. A class type — `abstract`, `mutable`,
or otherwise — has no knowable subtype set, so a `match` whose matched type is a class type is
exhaustive only when it has a wildcard branch. `match` over an `abstract` class type is therefore legal
and useful, and it always requires `_`.

Initial `match` patterns are enum variant patterns, binding patterns of the form `name: Type`, and
wildcard `_`. Branches are checked in source order, duplicate or unreachable branches are errors, and
every known variant must be covered unless `_` is present. Branch reachability is decided by type
subsumption and is independent of exhaustiveness: a branch whose type is a supertype of an earlier
branch's type is unreachable even when a wildcard makes the match exhaustive. The result type is the
nearest common declared supertype to which every branch result is assignable; if none exists, the match
is ill-typed.

## 13. switch

`switch` is a statement for straightforward value dispatch and Regex matching. It is also available
as an **expression** that produces a value; the statement and expression forms share their surface
syntax and are distinguished by syntactic context (section 21).

Cases never implicitly fall through.

```solvik
switch (value) {
    case 1 {
        print("one")
    }

    case 2 {
        print("two")
    }

    default {
        print("other")
    }
}
```

No `break` is required to terminate a case.

Cases are tested in source order and exactly the first matching case executes. Every `case` and
`default` body is a real braced lexical scope (section 16): the body's `{` closes the label's line,
there is no colon after a label, and bindings declared in one case body are independent of every
other body. A `break` inside a case is illegal unless it exits a loop nested inside that case.

Constant case expressions must be compile-time constants assignable to the switched value's type. Regex cases require a `String` switch value. A switch contains at most one `default`, and it must be last.

Initial Solvik does not provide a `fallthrough` keyword. Shared cases are expressed directly, for example:

```solvik
case 1, 2 {
    print("one or two")
}
```

## 14. Regex

`Regex` is a first-class built-in type.

Minimum conceptual API:

```solvik
class Regex extends Any {
    method matches(value: String): Boolean
    method find(value: String): RegexMatch?
    method findAll(value: String): List<RegexMatch>
    method replace(value: String, replacement: String): String
}
```

The initial portable pattern syntax supports literals, `.`, `^`, `$`, character classes, capturing groups, alternation, `*`, `+`, `?`, `{m}`, `{m,}`, `{m,n}`, and the ASCII classes `\d`, `\s`, and `\w` with their uppercase negations. Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.

`matches` requires the complete input to match. `find` returns the first non-overlapping match and `findAll` returns all non-overlapping matches from left to right. `replace` replaces all non-overlapping matches and treats the replacement as literal text; capture substitution is deferred.

`RegexMatch` exposes immutable `value: String`, `start: Integer`, `end: Integer`, `groupCount: Integer`, and `method group(index: Integer): String?`. Offsets are zero-based character offsets and `end` is exclusive. Group zero is the complete match.

Regex construction accepts raw strings:

```solvik
var number: Regex = Regex(r#"^\d+$"#)
```

Regex patterns may be used in `switch` cases:

```solvik
switch (input) {
    case regex r#"^\d+$"# {
        print("number")
    }

    case regex r#"^[A-Za-z]+$"# {
        print("word")
    }

    default {
        print("other")
    }
}
```

Regex cases do not establish exhaustiveness. A wildcard/default is required where exhaustiveness is required.

Regex match/capture binding in `switch` is deferred.

## 15. Strings

### Normal strings

Normal strings cannot contain an unescaped physical newline. They support exactly `\\`, `\"`, `\n`, `\r`, `\t`, `\0`, and `N` (`\N`). Any other escape is a lexical error.

```solvik
var message: String = "hello\nworld"
```

String interpolation is deferred. A `$` has no interpolation meaning in the initial implementation.

##### The `\N` escape

`\N` is a Solvik-specific ordinary-string escape that represents the native line separator of the
target execution platform. Its concrete value is platform dependent:

| Platform       | Value          |
|----------------|----------------|
| Windows        | `\r\n` (CRLF) |
| Linux / macOS  | `\n` (LF)      |

On the JVM, the resulting character sequence equals `System#lineSeparator()`.

`\N` is case-sensitive: `\n` (lowercase) always represents LF (U+000A), while `\N` (uppercase)
represents the platform-native line separator. `\r\n` explicitly represents CRLF as two characters.

The compiler does not expand `\N` at compile time. It leaves a sentinel character (U+00A6, the
broken bar `¦`) in the decoded string, and the runtime substitutes the executing platform's
`System#lineSeparator()` for each sentinel occurrence. This keeps portable compiled artifacts free
of the build host's newline and guarantees that a program compiled on one platform produces the
correct native line separator when executed on another.

Examples:

```solvik
var onlyNative: String = "\N"
var leading: String = "\Nindented"
var trailing: String = "end\N"
var multiple: String = "a\Nb\Nc"
var explicitLf: String = "\n"
var explicitCrlf: String = "\r\n"
var escapedBackslashN: String = "\\N"  // literal backslash followed by 'N'
```

Raw strings do not process `\N`; the two characters remain literal inside a raw string.

When an ordinary string containing `\N` is used as a regular-expression pattern, the regex engine
receives the already-decoded string (with the platform-native separator substituted). A raw string
or an escaped backslash followed by `N` passes a literal backslash and `N` to the regex engine,
which applies its own semantics.

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
var regex: Regex = r#"\d+\s+"#
var json: String = r#"{"name":"Doug","path":"C:\temp"}"#
var sql: String = r#"
SELECT *
FROM users
WHERE name = 'Doug'
"#
```

## 16. Statement Termination and Brace Placement

Solvik is a physical-line language. A physical newline ends the statement, declaration, or member
that precedes it, and the grammar requires a separator between every two constructs.

```solvik
var x: Integer = 1
var y: Integer = 2
```

The semicolon is a separator, not a terminator. It may separate two constructs written on the same
physical line:

```solvik
var a: Integer = 1; var b: Integer = 2; print(a + b)
```

It never ends a line. A `;` that is followed by another physical line, by end of file, or by a
stand-alone closing brace did not separate two constructs on its line and is rejected as
`SOLV-PARS-012` (`SEMI_ENDS_LINE`) at the semicolon. So all of these are errors:

```solvik
var x: Integer = 1;
```

```solvik
println("one"); // comment
```

```solvik
foo(); bar();
```

```solvik
var value: Integer = {
    42;
}
```

and `foo(); var a: Integer = 1; var b: Integer = 2` is the correct spelling of three statements on one line. A
construct followed by nothing but comment to the end of its line is complete at the boundary;
comment cannot make a `;` into a separator.

Termination is decided before parsing by a line-boundary stage over the token stream, never by a
parser error or by any insertion heuristic. The lexer preserves every physical newline (including a
newline inside a comment, because it is still a physical newline), and the stage forwards exactly one
parser-visible boundary token per line boundary whose last token ends a line: an identifier, a
literal, `break`, `continue`, `return`, `)`, `]`, `}`, an explicit `;`, a completed `?` propagation,
or `this`, `super`, and `null`, end a line; keywords that open a construct (`var`, `else`, `mutable`,
`class`, `func`, `method`, `try`, `throw`, `static`, `case`, `default`, `enum`, `module`, `abstract`, `match`),
an operator, and a comma do not. Blank lines and comment lines never
add a second boundary, and the final physical line of a file is terminated by end of file, to which
the same rule applies without a following token. Indentation has no syntactic meaning.

### Expression continuation

A line that cannot end is a continuation: the grammar itself absorbs the boundary tokens at the
positions where a construct may spread across lines - after a binary operator, after a comma, before
a `.`, `?.`, or `::`, around argument, type-argument, and pattern lists, and before a closing
delimiter. There is no lookahead exception table and no heuristic join: a line break the grammar
does not admit is an error at the break.

```solvik
var total: Integer = price +
    tax +
    shipping
```

`return` followed by a newline is a complete bare return; the next line begins a new statement. The
same holds of `throw` and every other keyword that ends a line: the newline terminates the
statement, and the grammar never joins the following line.

### Member chaining

Solvik supports TypeScript/Kotlin-style leading-dot chains:

```solvik
var result: Result<String, String> = service
    .load()
    .transform()
```

A line ending in `.` or `?.` cannot end, so the chain continues. This is grammar, not lookahead: the
member-suffix position is written to absorb the boundary, and a `.` may never begin a new statement.

### Brace placement

Every multiline construct - a function, method, constructor, static block, control-flow body,
`case` or `default` body, class body, interface body, enum body, `switch` body, `match` body, block
expression, and stand-alone scope - is written with its braces on their own physical lines:

```solvik
if (condition) {
    work()
}
else {
    recover()
}
```

Four rules hold without exception, checked against the token sequence after parsing so each is
reported once, at its own position:

1. an opening `{` is the last token of the line of the construct that introduces its scope. The
   body it opens begins on the following line. `if (c) { work()` and `func f() {}` are violations;
   the diagnostic names the offending token (`SOLV-PARS-010`).
2. a `{` that begins a physical line never opens the body of a construct whose header ended on an
   earlier line. The body brace must sit on its header's line (`SOLV-PARS-009`). A stand-alone scope
   block has no header, so its brace may begin a line, and a scope block may follow any complete
   construct on the next line.
3. a closing `}` is the only significant token on its physical line. Nothing but whitespace and a
   comment may share it (`SOLV-PARS-007`), which makes `} else {`, `};`, `})`, and `foo() }` all the
   same mistake.
4. a clause keyword - `else`, `catch`, `finally` - begins its own physical line, so a clause is
   written `}` newline `else {` and never `} else {` (`SOLV-PARS-008`).

An empty body is written as an opening brace on one line and a closing brace on the next; `{}` is
rejected by rule 1.


## 17. Control Flow

Standard forms:

```solvik
if (condition) {
    ...
}
else {
    ...
}

while (condition) {
    ...
}

for (i in 0..<limit) {
    ...
}
```

Parentheses around conditions are retained for TypeScript/Java familiarity.

`if` and loop conditions must have type `Boolean`. `while` is a pre-test loop. There is no
three-clause `for` statement: it would require semicolons inside its header, which section 16
reserves for separating constructs on one line. Initializer-scoped counting loops are written as a
scope block around a `while` loop. `break` and `continue` are valid only inside a loop.

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

The loop variable is an implicitly declared immutable `Integer` binding scoped to the loop body. Both bounds are `Integer` expressions evaluated once before the first iteration. `...` ascends from the start and includes the end, `..<` ascends from the start and excludes the end, and `..>` descends from the start and excludes the end. A reversed or empty range performs zero iterations rather than raising an error. `break` and `continue` behave exactly as in the three-clause `for`. Range `for`-in is a distinct loop construct; it does not introduce the general iteration protocol, which remains deferred (section 11). `in` is a reserved keyword.

## 18. Type Tests and Casts

Support type tests:

```solvik
if (value is String) {
    print(value)
}
```

The compiler must narrow the type where the checked value is stable and no intervening write can invalidate the refinement.

Checked cast syntax:

```solvik
var user: User = value as User
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
include r#"lib/generated.sol"#
```

`include` is a reserved keyword. An include may appear only as an item of a compilation unit: it is
not a statement and cannot appear in a function, method, constructor, block, loop, `switch`, or
`match` branch. The path is a normal or raw string literal, and the directive ends where its
physical line ends, like any construct (section 16).

An `include` includes source and binds no name. There is no alias clause, and an `include` that
writes one is rejected (`SOLV-PARS-013`) rather than reinterpreted: a module is referenced through
the name its own `module` block declares.

Included declarations are reached through that name with the `::` namespace separator:

```solvik
include "lib/math.sol"

math::add(1, 2)
var point: math::Point = math::Point(1)
var result: math::Result = math::Result.Ok(1)
```

### Modules and namespaces

A `module` declaration is a block that contains declarations, and it is an item of a physical file
rather than a file header:

```solvik
module com_example_util {

    func add(a: Integer, b: Integer): Integer {
        return a + b
    }
}
```

`module` is a reserved keyword. The written name is a single identifier: lowercase letters and
digits with parts joined by exactly one underscore, each part starting with a letter
(`[a-z][a-z0-9]*(_[a-z0-9]+)*`), and it is not a reserved word. Underscores replace Java package
dots, so `com.example.util` is written `com_example_util`. Module names never contain dots. A file
without a `module` block belongs to the implicit default module.

- A file's top-level `func`, `class`, `interface`, `enum`, and `error` declarations belong to the
  module that contains them, and a module block contains only declarations: an `include` and a
  statement are not module items. A module block is never nested inside another one.
- A file may declare several module blocks, and it may hold default-module declarations and
  executable statements in addition.
- Two files that declare the same module name are one module and their declarations merge; a
  duplicate declaration within the merged module is `SOLV-RESOL-002`.
- A module declaration contributes its name to the whole program: after expansion, `Name::member`
  names the declaration of a `module Name { ... }` block from any file of the program.
- A prefix that no module declaration of the program declares is unknown and is reported as
  `SOLV-RESOL-015` at the prefix.
- `::` is the namespace separator. A qualified declaration reference is written `p::Name`, where the
  prefix `p` must be a module name declared anywhere in the program; a qualified type is `p::Type`, a
  qualified call is `p::function(...)`, and a qualified enum variant is `p::Enum.Variant`. The `.`
  operator remains ordinary member access, so a qualified reference is never confused with member
  access and there is no name-collision rule between declarations and prefixes.  no name-collision rule between declarations and prefixes.

Unqualified name resolution within a file is, innermost first: lexical locals and parameters, the
file's own module, the implicit default module, and the built-in prelude. Built-in types and
functions are always visible unqualified and cannot be shadowed by a module name.

Top-level `var` declarations, mutable or not, and executable statements are not part of any module namespace:
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
line-boundary token stream, and parser. At an `include`, the target is recursively expanded and
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
top-level `var`, mutable or not, remains a local of that implicit main, so its visibility and definite
initialization follow statement order across file boundaries. An explicit `func main` in any
participating file remains `SOLV-SEM-001`. A fully expanded program with no executable top-level
statements has no entry point and does nothing.

Module resolution, include resolution, and file reads finish before semantic analysis and lowering.
There is no runtime module or include node and no runtime file I/O. Physical file identity is
preserved: an `abstract` class may be extended from any included file (section 12),
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
| `RESOL_MODULE_INVALID_NAME` | `SOLV-RESOL-012` | module declaration |
| `RESOL_UNKNOWN_MODULE` | `SOLV-RESOL-015` | qualified reference |

Messages for path failures include the written path and, when one exists, the resolved candidate.
Denied access and other I/O failures become `SOLV-RESOL-010` and never escape as host errors.

## 21. Expression-Oriented Constructs

A block, an `if`, and a `switch` may be used as values. The feature is additive: assignments remain
statements, a function still requires an explicit `return` for a value, and there is no implicit
function result. These rules are compiled before lowering: a construct
with an error never produces an executable call target, and no runtime node repairs an invalid
construct with `null`, zero, `false`, an empty string, or a host sentinel.

### 21.1 Terms

A **statement block** is a brace-delimited block in statement position; its behavior is unchanged.
A **block expression** is a brace-delimited block in expression position. It has its own lexical
scope and may contain zero or more statements followed by an optional **tail expression**.

A path **completes abruptly** when it executes `return`, or a valid enclosing-loop `break` or
`continue`, before reaching the construct's result. Abrupt completion carries no value and does not
participate in result joining. A path **completes normally without a result** when it reaches the end
of a value-required body without evaluating a tail expression; that is a compile-time error. A body
whose tail expression produces no value completes normally without a result as well, because "no
value" is not a type that could be bound.

### 21.2 Block expressions

```solvik
var answer: Integer = {
    var base: Integer = 20
    base + 22
}
```

The block has type `Integer` and value `42`. A block expression
introduces one lexical scope. Earlier statements execute in source order, and a local declared
inside the block is visible to later items in that block and nowhere outside it.

Every normally completing path through a value-required block must reach a tail expression that
produces a value. An empty block, a block ending in a local declaration, a block ending in an
assignment, and a block whose tail expression produces no value are invalid in expression position,
because none of them produces a value:

```solvik
var invalid: Integer = {
    var local: Integer = 1
}
```

A block whose tail produces no value may still be written as a statement; a standalone scope block
remains a statement block, and the existing rule that an unused value-producing non-call expression
cannot stand alone still applies.

A value-required block whose every path completes abruptly has type `Nothing` and never evaluates a
tail expression.

### 21.3 Semicolons and tail expressions

Items inside a block are separated by the separator of section 16 - a physical newline or, for items
sharing a line, an explicit `;` that separates two statements and never terminates one. Separation
never changes meaning: no separator token carries a value, and the last item of a value-required
block is its tail expression wherever it sits:

```solvik
var a: Integer = {
    42
}

var b: Integer = {
    1; 42
}
```

Both are `Integer` block expressions with value `42`: in the second the `;` separates two items on
one line, and the last item is still the tail. Writing `{ 42; }` is the `SEMI_ENDS_LINE` error of
section 16 - a `;` may not terminate the item before a line end or before `}`. Comments and blank
lines before `}` do not affect tail selection, and a terminal assignment is a statement and never a
tail expression.

### 21.4 `if` expressions

An `if` may be used in expression position:

```solvik
var description: String = if (value < 0) {
    "negative"
}
else if (value == 0) {
    "zero"
}
else {
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
    }
    else {
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
var message: String = switch (code) {
    case 200 {
        "ready"
    }

    case 201, 202 {
        "running"
    }

    default {
        "done"
    }
}
```

The scrutinee is evaluated exactly once. Case labels are tested in source order, only the first
matching body executes, and there is no implicit fallthrough. Every expression `switch` must contain
exactly one `default`, and it must remain last. `switch` does not gain enum exhaustiveness;
that remains the responsibility of `match`. Requiring `default` makes value production explicit for
`Integer`, `String`, and regex dispatch, while a statement `switch` may still omit `default` and do
nothing when no label matches.

Every normally completing case body, including `default`, must end in a tail expression; statements
may precede it. An abrupt case contributes no result type. Existing rules for constant labels, label
assignability, multiple labels, regex labels, duplicate and default placement, and direct `break` in
a case continue to apply. Regex expression cases keep the same spelling and matching behavior:

```solvik
var kind: String = switch (input) {
    case regex r#"^\d+$"# {
        "number"
    }

    case regex r#"^[A-Za-z]+$"# {
        "word"
    }

    default {
        "other"
    }
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
existing nullability rules. No numeric promotion or widening, structural typing, dynamic typing,
implicit conversion, or inferred union type is introduced: a numeric widening is a coercion at a
conversion site, never a join rule. An `if`/`else` expression whose branches yield an `Integer` and a
`Long` is written with each brace on its own line, and its join is `Number`, not `Long`. If
exactly one branch can complete normally, its
result type is the construct's result type. If no branch can complete normally, the construct has
type `Nothing`, and no runtime value is invented for it.

```solvik
var both: Any = if (flag) {
    1
}
else {
    "text"
} // type Any
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
var mutable score: Integer = 0
score = if (enabled) {
    10
}
else {
    0
}

var mode: String = if (debug) {
    "debug"
}
else {
    "normal"
}
println(mode)

func classify(value: Integer): String {
    return switch (value) {
        case 0 {
            "zero"
        }

        default {
            "nonzero"
        }
    }
}
```

Assignments remain statements and are not usable as tail expressions or nested values, and a function
body does not implicitly return its final expression:

```solvik
func invalid(): Integer {
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
| `SEM_HASHCODE_WITHOUT_EQUALS` | `SOLV-SEM-044` | the `hashCode` override declared without `equals` |
| `SEM_EQUALS_WITHOUT_HASHCODE` | `SOLV-SEM-045` | the `equals` override declared without `hashCode` |

`TYPE_BRANCH_RESULT` reports normally completing branches with no single nearest common declared
supertype. `match` keeps its existing result and exhaustiveness diagnostics, and existing type errors
inside a tail expression keep their existing codes.

## 22. Unchecked Exceptions

Solvik provides unchecked exceptions for error conditions that unwind the call stack to a handler,
distinct from the `Result` value model (section 21.7 and the propagation operator) which carries
recoverable failures as ordinary values. Exceptions are unchecked: a function does not declare the
exception types it may throw, and no caller is required to handle them.

### 22.1 Exception types

The predeclared guest exception hierarchy (section 4) is nominal and rooted at `Exception`:

```text
Exception
├── RuntimeException
└── ApplicationException
```

`Exception`, `RuntimeException`, and `ApplicationException` are built-in nominal class types with no
source declaration. A user-defined exception is an ordinary final class that extends one of these
bases (or a further subclass) and may carry typed fields like any other class. Every class whose
declared superclass chain reaches one of the three built-in bases — directly or transitively — is
itself a guest exception type. A class whose chain does not reach a built-in base is not an exception
type. Single inheritance and the final-by-default rules of section 7 apply unchanged.

#### The exception message

Every guest exception type carries an optional message, following Java's empty-constructor and
message-constructor pattern. Construction accepts a single optional trailing `String?` argument:

```solvik
class ParseError extends RuntimeException {
}

throw ParseError()          // no message
throw ParseError("bad int") // message
```

The message is **not** a declared constructor parameter. It is a compiler-synthesized slot that is
private by construction: it is never a readable member, so `e.message` is not a member of any exception
class and is reported as `SOLV-RESOL-004`. The value is observed only through the synthesized accessor:

```solvik
method getMessage(): String?   // synthesized on every guest exception type
```

- A construction with no message argument stores no message; `getMessage()` then returns `null`.
- A message argument must be assignable to `String?`; anything else is `SOLV-TYPE-001`, and supplying
  more than one extra trailing argument is an arity error (`SOLV-TYPE-003`).
- The message is independent of the class's declared constructor, so a subclass keeps its own declared
  parameters and `super(...)` forwarding unchanged; the message is still the single trailing argument:
  `SubError(7, "sub message")` passes `7` to the declared constructor and stores the message.
- `getMessage()` is available on every exception type, including a handler written on a base type, and
  follows the usual nullable-receiver rules for a `String?` result (`e?.getMessage()` is safe).
- The names `message` and `getMessage` are reserved on every guest exception class, so a user member may
  not declare either (diagnostic `SOLV-SEM-037`). On a class that is not a guest exception, both names
  remain ordinary user-declarable members.

The built-in bases (`Exception`, `RuntimeException`, `ApplicationException`) have no declaration and are
not constructible; they serve as handler and superclass types, and the message pattern applies to the
user-defined exception classes that derive from them.

A generic class cannot be thrown or caught at all today: constructing it yields a parameterized type,
which neither a `throw` operand nor a `catch` handler type accepts. The synthesized message therefore
applies to non-generic exception classes; for a generic one, supplying more arguments than its declared
constructor has remains an ordinary arity error rather than a message.

### 22.2 `throw`

```solvik
throw ParseError()
```

`throw` is a statement. Its operand is any expression. The operand type must be assignable to a guest
exception type (an exception type per section 22.1); throwing any other value is the compile-time
error `SOLV-SEM-053` (`SEM_THROW_NON_EXCEPTION`), reported on the operand. A `throw` completes
abruptly (section 21.1) and produces no value, so a `throw` as the final statement of a
value-returning function satisfies the value-on-all-paths rule the same way `return` does.

Evaluating `throw` transfers control to the nearest dynamically enclosing handler that matches the
thrown value's runtime class (section 22.3), across function-call boundaries. If no such handler
exists, the value reaches the program boundary (section 22.5).

### 22.3 `try`, `catch`, and `finally`

```solvik
try {
    riskyOperation()
}
catch (e: ParseError) {
    recover(e)
}
catch (e: RuntimeException) {
    report(e)
}
finally {
    releaseResources()
}
```

A `try` consists of a `try` block, zero or more `catch` clauses, and an optional `finally` clause. A
`try` with neither a `catch` clause nor a `finally` clause is the compile-time error
`SOLV-SEM-056` (`SEM_TRY_NEEDS_HANDLER`), reported on the whole statement.

Each `catch` clause names an exception type and binds the caught value to an immutable local for the
duration of the handler body. The binding has the clause's declared type and is scoped to that
handler body only; it is not visible after the clause and may shadow an outer name. The binding is
initialized before the handler body runs, exactly as a parameter is, so the handler may read it
immediately and rethrow it with `throw e`. Because each
handler body has its own scope, two clauses in the same `try` may reuse the same binding name without
conflict. Declaring a handler type that is not a guest exception type is the compile-time error
`SOLV-SEM-054` (`SEM_INVALID_CATCH_TYPE`), reported on the type reference.

#### Handler selection

When the `try` block completes by throwing a value of runtime class `C`, the `catch` clauses are
tried in source order and the **first** clause whose declared type matches `C` runs. A handler type
`H` matches a thrown runtime class `C` when `C` is `H` or `C` is a subtype of `H` in the exception
hierarchy — that is, when `C`'s superclass chain reaches `H`. A handler written on a base type
therefore catches every subclass thrown at runtime, including subclasses whose chain passes through a
built-in base. If no clause matches, the value keeps propagating outward after the `finally` clause
(if any) runs.

Because selection is first-match-wins in source order, a clause whose handler type is a subtype of an
earlier clause's handler type can never run. Such an unreachable clause is the compile-time error
`SOLV-SEM-055` (`SEM_UNREACHABLE_CATCH`), reported on the offending clause's type reference. Two
clauses with the same handler type also trigger `SOLV-SEM-055` on the later one.

#### `finally`

The `finally` block runs on every exit path of the `try`, exactly once per entered `try`: after
normal completion of the `try` block when there is no `catch`, after a selected handler completes,
while an unmatched value propagates outward, and on a `return`, `break`, `continue`, or a `?`
propagation exit (section 23.3) that leaves the `try` block. Exactly one completion escapes once the
`finally` block finishes, chosen by Java's **last-abrupt-completion-wins** rule:

- If the `finally` block completes **normally**, the completion already in flight — a propagating
  throw, a `return`/`break`/`continue`/`?` transition, or normal completion of the whole `try` —
  continues unchanged.
- If the `finally` block completes **abruptly**, that completion **replaces** whatever was in flight:
  the replaced value or transition is discarded, and only the `finally` block's completion propagates
  (a `throw` from the `finally` block may be caught by an enclosing `catch`).

A replaced throw is discarded outright. Solvik does **not** attach a suppressed-exception chain: Java's
suppression belongs to `try`-with-resources and checked exceptions, neither of which Solvik has, and
unchecked exceptions carry no `addSuppressed`/`getSuppressed` surface.

```solvik
try {
    throw FirstError()
}
finally {
    throw SecondError() // FirstError is discarded; SecondError propagates
}
```

#### Return-path analysis of `try`

Because a `finally` completion can replace whatever the `try` was doing, whether a `try` statement can
still fall through governs the rule that a value-returning function must return on every path
(`SOLV-TYPE-012`), using Java's reachability rules:

- A `finally` block that always transfers control (`return` or `throw`) guarantees the whole statement
  transfers control, so neither the `try` block nor any handler needs its own.
- Otherwise the statement can fall through unless the `try` block always transfers control **and**
  every `catch` body also always transfers control. A handler that can complete normally leaves the
  statement reachable, so a following `return` is still required.

```solvik
func ok1(): Integer {
    try {
        return 1        // accepted: the try block always returns and no handler can fall through
    }
finally {
        println("cleanup")
    }
}

func ok2(): Integer {
    try {
        println("body")
    }
finally {
        return 7        // accepted: the finally block always transfers control
    }
}

func needsMore(): Integer {
    try {
        return 1
    }
catch (e: RuntimeException) {
        println("handled")  // completes normally, so the statement can fall through
    }
    return 0                // ...and therefore still requires this return
}
```

### 22.4 Propagation across call boundaries

A thrown value crosses function-call targets during unwinding. A `throw` inside a called function is
caught by a handler in any dynamically enclosing function frame, including the caller and its
ancestors. A call target for an ordinary function does not itself terminate the guest program; it only
runs the function body and lets an uncaught thrown value continue unwinding toward the boundary.

### 22.5 Program boundary

The outermost execution boundary of a Solvik program is the boundary of the root source evaluation —
the point at which the implicit `main` of section 6 is invoked. When a thrown value reaches this
boundary, no Solvik handler remains, so the value is uncaught. An uncaught thrown value is a
guest-visible failure: it terminates the program with a non-zero exit status and reports the thrown
class together with its message when one is present (`uncaught guest exception of class 'ParseError'
with message 'bad int'`), and it is reported as an ordinary guest error, never as a host internal
error. The boundary is
the single point at which the catchable unwinding signal is turned into a program-level failure;
inner call targets must not perform this conversion, so that an enclosing handler anywhere above the
throw site still receives the value.

A host that executes a function value through the interoperation boundary stands where the root
source evaluation otherwise stands: no Solvik handler can be above such a call, so a thrown value
that escapes it is uncaught and is converted into a program-level failure at that call as well,
reported with the same thrown class and message, and the catchable unwinding signal itself is
never delivered to a host as the failure. A call from guest code is not that boundary — a guest
caller reaches a callable through the ordinary call path of section 6, so a handler in any
enclosing Solvik frame still receives the value.

### 22.6 Required diagnostics

| Code name | Stable code | Primary span |
|---|---|---|
| `SEM_THROW_NON_EXCEPTION` | `SOLV-SEM-053` | the `throw` operand expression |
| `SEM_INVALID_CATCH_TYPE` | `SOLV-SEM-054` | the `catch` clause's type reference |
| `SEM_UNREACHABLE_CATCH` | `SOLV-SEM-055` | the unreachable `catch` clause's type reference |
| `SEM_TRY_NEEDS_HANDLER` | `SOLV-SEM-056` | the whole `try` statement |

The message argument of an exception construction reuses existing codes and adds none: a non-`String?`
message is `SOLV-TYPE-001` (`TYPE_MISMATCH`) on the argument, an extra argument beyond one is
`SOLV-TYPE-003` (`TYPE_ARITY_MISMATCH`) on the call, and declaring `message` or `getMessage` on an
exception class is `SOLV-SEM-037` (`SEM_RESERVED_MEMBER`) on the member.

## 23. Result Operations

A `Result<T, E>` value (section 12) carries the following synthesized operations. They are members of
the `Result` type itself, not of `Ok` or `Err`, and are available on every `Result<T, E>` value — both
variants support the full set, and none is variant-exclusive. The success payload type `T` and the
error payload type `E` come from the receiver's declared type arguments.

```solvik
func f(): Result<Integer, String> {
    var r: Result<Integer, String> = compute()
    if (r.isOk()) {
        return Result.Ok(r.unwrap())
    }
    r.ignore()
    return Result.Err("unavailable")
}
```

| Operation | Signature | Result |
|---|---|---|
| `isOk` | `isOk(): Boolean` | `true` when the value is the success variant |
| `isErr` | `isErr(): Boolean` | `true` when the value is the error variant |
| `unwrap` | `unwrap(): T` | the success payload; faults on the error variant |
| `unwrapErr` | `unwrapErr(): E` | the error payload; faults on the success variant |
| `expect` | `expect(message: String): T` | the success payload; faults with `message` on the error variant |
| `ignore` | `ignore()` | consumes the value and produces no value |

- `isOk` and `isErr` are complementary tests over the variant. They never fault.
- `unwrap` returns the success payload of an `Ok`. On an `Err` it raises a runtime fault (section 23.1).
- `unwrapErr` returns the error payload of an `Err`. On an `Ok` it raises a runtime fault (section 23.1).
- `expect(message)` returns the success payload of an `Ok`, ignoring the message. On an `Err` it raises
  a runtime fault reporting `message` together with the carried error (section 23.1). The message must
  be assignable to `String` and is evaluated exactly once whenever the call runs, on either variant.
- `ignore` evaluates its receiver exactly once, discards the value, and produces no value. Because it
  produces no value rather than a `Result`, `result.ignore()` is a well-formed standalone statement and
  satisfies the must-consume rule (section 23.2).

These operations read a variant payload directly; they are not a `match`, so they do not narrow a
binding for the rest of a block. Where a handler must react to both payloads, a `match` (section 12)
remains the form that binds each payload. `unwrap`/`unwrapErr`/`expect` are the deliberate,
fault-on-wrong-variant accessors; the `?` propagation operator (section 23.2) is the non-faulting
control-flow counterpart inside a `Result`-returning function.

### 23.1 Wrong-variant faults

`unwrap` on an `Err`, `unwrapErr` on an `Ok`, and `expect` on an `Err` raise a Solvik runtime fault of
the same class as an arithmetic, cast, or bounds fault (an ordinary guest failure reported to the host
with a non-zero exit status, not an internal error). The fault message names the operation and the
variant actually present:

```text
type error: unwrap called on a Result holding 'Err'
type error: unwrapErr called on a Result holding 'Ok'
expect failed: <message> (error: <rendered error>)
```

A wrong-variant fault is a value-state error, not a type error: it can only occur on a receiver whose
static type is a `Result`, so it is detected at run time (at the operation) and carries the source
location of that operation.

### 23.2 Consuming a Result

A `Result<T, E>` must never be silently discarded. A `Result`-typed value used as a standalone
statement (a call expression whose result type is a `Result`) is the compile-time error
`SEM_UNUSED_RESULT` (`SOLV-SEM-052`). The permitted ways to consume a `Result` are:

- binding it and reading its payloads through `match` (section 12) or the operations of section 23,
  with the final read being a non-`Result` operation such as `unwrap`, `isErr`, or `ignore`;
- propagating it with the postfix `?` operator inside a function declared to return a `Result`
  (section 23.3); or
- calling `ignore()` to discard it deliberately, since `ignore()` produces no value.

A standalone `Result` call such as `compute()` therefore requires one of these consumptions;
`compute().ignore()` is accepted and `compute()` alone is rejected.

### 23.3 Propagation

The postfix operator `expression?` is the propagation form for `Result` values. Its operand must have
a `Result<T, E>` type; the value of the expression is the unwrapped success payload of type `T`, and
the operation is permitted only inside a function declared to return a `Result<T2, E2>`.

On `Ok(value)` the operand's success payload becomes the value of `expression?`. On `Err(error)` the
current function returns `Err(error)` immediately, without evaluating the rest of its body. The
operand is evaluated exactly once.

Propagation is type-checked against the enclosing function's declared `Result` boundary: the unwrapped
success type `T` must be assignable to `T2`, and the propagated error type `E` must be assignable to
`E2`. The operand type never widens the function's declared result types; only assignability is
required.

```solvik
func readConfig(): Result<Config, IoError> { ... }

func loadApp(): Result<App, IoError> {
    var config: Config = readConfig()?   // or return Err(IoError) from loadApp
    return Result.Ok(App(config))
}
```

The required diagnostics:

| Code name | Stable code | Condition |
|---|---|---|
| `SEM_RESULT_PROPAGATION_INVALID_OPERAND` | `SOLV-SEM-049` | the `?` operand is not a `Result<T, E>` |
| `SEM_RESULT_PROPAGATION_NO_BOUNDARY` | `SOLV-SEM-050` | no enclosing function returns a `Result` |
| `SEM_RESULT_PROPAGATION_TYPE_MISMATCH` | `SOLV-SEM-051` | `T`/`E` is not assignable to the boundary `T2`/`E2` |
| `SEM_UNUSED_RESULT` | `SOLV-SEM-052` | a `Result` is used as a statement without being consumed |

Propagation is a control-flow transition, not a wrong-variant fault: an `Err` carried through `?`
returns normally as the enclosing function's `Result` value rather than raising a fault. This is the
key distinction from `unwrap`, which faults. The `?` operator and the operations of section 23 are
independent: neither is defined in terms of the other, and a program may use either or both.

### 23.4 Required diagnostics

The `Result` operations reuse existing diagnostic codes; no new codes are introduced.

| Code name | Stable code | Primary span |
|---|---|---|
| `RESOL_UNKNOWN_MEMBER` | `SOLV-RESOL-004` | a member of a `Result` receiver that is not a `Result` operation |
| `TYPE_ARITY_MISMATCH` | `SOLV-TYPE-003` | a `Result` operation call with the wrong argument count |
| `TYPE_FUNCTION_AS_VALUE` | `SOLV-TYPE-014` | a bare member read of a `Result` operation (no call) |

Wrong-variant faults at run time (section 23.1) are not compile-time diagnostics; they carry the
source location of the faulting operation.
