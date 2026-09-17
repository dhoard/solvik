# Solvik Test Plan

This document defines minimum acceptance coverage. Every language feature requires positive and negative tests at the lowest practical layer plus an end-to-end test when execution exists.

Tests for inherited SimpleLanguage behavior are temporary baseline tests, not compatibility requirements. Replace or remove them as their subsystem is converted. Do not retain a test solely to preserve SimpleLanguage syntax or semantics.

## Phase 1 AST

Before semicolon insertion exists, parser inputs use explicit semicolons:

```solvik
func add(a: Int, b: Int): Int {
    return a + b;
}
```

Tests must assert node kinds, child structure, source spans, and parser diagnostics. Phase 1 must not execute Solvik syntax.

## Parser / Lexer

### Semicolon insertion

Must parse:

```solvik
val x = 1
val y = 2
```

and:

```solvik
val x = 1; val y = 2;
```

Must preserve multiline expressions:

```solvik
val x = 1 +
    2 +
    3
```

Must treat:

```solvik
return
value
```

as a terminated `return` followed by another statement.

Must test leading-dot continuation:

```solvik
val x = service
    .load()
    .transform()
```

Test `?.` continuation at the token-stream layer before nullable member access becomes parser syntax in Phase 10.

Also test blank lines, line and block comments containing newlines, end of file, nested parentheses/brackets, `}` followed by `else`, and absence of duplicate semicolons.

### Raw strings

Test:

```solvik
r"abc"
r#"Test '"#
r##"contains "# text"##
```

Test multiline strings, backslashes, embedded quotes, empty raw strings, and unterminated delimiters.

Also test delimiter-count mismatch, an identifier beginning with `r`, comments after a raw string, a newline after a raw string, and multiple physical newlines inside one raw-string token.

## Static Types

Test lexical scope, legal inner shadowing, duplicate declarations, unknown names, use before initialization, exact call arity, argument types, explicit function returns, missing return paths, and the required `main` signature.

Positive:

```solvik
val x: Int = 1
var y: Int = x
y = 2
```

Negative:

```solvik
val x: Int = "x"
```

```solvik
val x = 1
x = 2
```

## Any

Positive:

```solvik
val x: Any = "hello"
```

Negative:

```solvik
val x: Any = "hello"
val y: String = x
```

Require narrowing/cast.

## Operators and Control Flow

Test precedence, unary operators, short-circuit Boolean evaluation, integer division, division by zero, String concatenation, invalid mixed-type operands, assignment to `val`, assignment as a rejected expression, and rejected implicit String conversion.

Test `if`, `while`, three-clause `for`, `break`, and `continue`, including non-Boolean conditions and loop-control statements outside a loop.

Test `print` and `println` with every Phase 5 scalar type.

## Classes

Test:
- construction;
- immutable fields;
- mutable fields;
- methods;
- unknown members;
- constructor initialization;
- initialization on every successful path;
- read before initialization.

Negative:
- write to `val`;
- undeclared property;
- wrong constructor argument type.

## Inheritance

Positive:
- extend `open class`;
- override `open func`;
- call inherited method;
- explicit and implicit `super` initializer calls;
- `super.member`.

Negative:
- extend final class;
- multiple class parents;
- override without `override`;
- override final method;
- incompatible return/parameter types;
- inheritance cycle.

Test every built-in numeric type, explicit conversions, out-of-range conversion behavior, and rejection of implicit widening or narrowing.

## Interfaces

Test:
- one interface;
- multiple interfaces;
- default method;
- explicit method overriding a default;
- conflicting defaults.

## Delegation

Test:
- delegated method satisfies interface;
- explicit class method overrides delegate;
- inherited method overrides delegate;
- delegate overrides interface default;
- ambiguous delegates fail;
- delegate definite initialization;
- delegation remains nominally typed.

## Nullability

Negative:

```solvik
val x: String = null
```

```solvik
val x: String? = null
print(x.length)
```

Positive:

```solvik
if (x != null) {
    print(x.length)
}
```

Test `?.` and `??`.

Test narrowing invalidation after writes and narrowing of stable `val` values.

## Generics

Test:
- `List<String>`;
- `Box<User>`;
- nested generic types;
- wrong generic argument assignment;
- generic functions and methods;
- invariance;
- rejected raw generic types;
- rejected runtime tests against erased type arguments.

Test `List<T>.size`, `get`, immutability, element typing, and bounds errors.

## Enums and Sealed Types

Test value-carrying variants, generic variants, invalid construction, closed variant metadata, and rejection of a sealed subtype declared outside the permitted scope.

## match

For:

```solvik
enum Result<T, E> {
    Ok(T)
    Error(E)
}
```

Positive: all variants covered.

Negative: one variant missing without wildcard.

Test branch result type unification.

## switch

Test:
- ordinary cases;
- grouped cases;
- `default`;
- no implicit fallthrough;
- no `break` requirement;
- rejected case-level `break` outside a nested loop;
- first-match source order.

## Regex

Test:
- direct Regex API;
- raw regex strings;
- `switch` regex case;
- regex case followed by another case does not fall through;
- invalid constant regex compilation diagnostic;
- invalid dynamic regex runtime error;
- one-time compilation of constant patterns.

## Truffle Runtime

Test:
- primitive values remain efficiently specialized;
- method calls work through call targets;
- field reads/writes use stable object shapes;
- polymorphic dispatch;
- language interop where retained;
- instrumentation/source sections remain correct;
- Solvik language id, MIME type, and `.sol` file detection;
- JVM and native launchers.

A source with any compile-time error must not produce or execute a Truffle call target.

## Removal Regression

As the Solvik production path becomes active, add negative tests proving that representative SimpleLanguage-only syntax is rejected. Do not add a legacy parser or compatibility option to make those tests pass.

The final suite must contain no positive test whose purpose is to preserve SimpleLanguage syntax, dynamic typing, implicit variable creation, or arbitrary object-member mutation.

## Regression Rule

Every discovered parser ambiguity, type-system bug, runtime mis-specialization, or diagnostic regression gets a minimized regression test before the fix is considered complete.
