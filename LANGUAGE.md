# Solvik Language Specification

**Status:** Language specification — declaration, nullability, exception, and standard-I/O revision  
**Target:** Java 25  
**Compatibility:** Intentionally supersedes parts of the implementation described by the previous `LANGUAGE.md`  
**Priorities:** (1) correctness and consistency, (2) generated-program performance, (3) clean generated Java source

Solvik is an independent, statically typed, Rust-inspired language. It is **not** a Rust dialect. It compiles to ordinary, readable Java 25 source and then to JVM bytecode with `javac`. A generated Solvik program requires the JDK and any Java libraries explicitly supplied for native bindings, **not** a Solvik runtime JAR.

This document specifies the **intended language after the declaration and nullability changes**. It is not a claim that the compiler already implements those changes. Code examples use the **new** syntax. In particular, existing compiler sources, examples, packaged libraries, and tests must be migrated together.

The intended changes are:

1. Remove the `var` keyword entirely.
2. Require an initializer on **every local or entry-file binding and every static or instance field declaration**. Module bodies still contain only types, functions, and nested modules, not variable declarations. Parameters and declarations that do not bind stored values (such as methods and types) are different constructs.
3. Use `:=` to declare a binding with an inferred type; use `name: Type = expression` to declare one with an explicit type. Use `=` **only** to assign to an existing mutable binding.
4. Keep variables immutable by default; prefix a declaration with `mut` to allow subsequent assignment. Keep `pub` and `static` for their existing purposes.
5. Make `Null` a **first-class, singleton type and value**, spelled `Null` in source. Eliminate the lowercase `null` literal and the previous special “unfixed slot” state.
6. Make all ordinary types non-nullable by default. Use `T?` for a nullable type. Use `name? := value` to request an inferred nullable type.
7. Preserve the independent nullability of an array and its elements, and require complete initialization of non-nullable elements.
8. Preserve existing Solvik functionality unless this specification explicitly changes it. Exception handling (`try` / `catch` / `finally`) is planned but is not declared implemented here.

## 1. Program structure

A `.sol` entry file contains imports, declarations, module blocks, and executable top-level statements:

```solvik
import "include.sol"

module geometry {
    pub type Point {
        pub x := 0
        pub y := 0

        pub func Point(x: Integer, y: Integer) {
            self.x = x
            self.y = y
        }
    }

    pub func area(width: Integer, height: Integer) -> Integer {
        return width * height
    }
}

origin := geometry::Point(0, 0)
TextWriter(Stdio.stdout()).writeLine((geometry::area(2, 3)) .. (" ") .. (origin.x))
```

- Top-level statements in the entry file execute in source order inside the generated program entry point.
- Imported files contribute **declarations only**; executable top-level statements in imports are an error (`SOL-I003`).
- `import "relative/path.sol"` imports a **file**, not a module. Paths resolve relative to the importing file. Transitive imports are supported; the same physical file is loaded once; cycles are rejected (`SOL-I001`–`SOL-I004`).
- Files declare their own module blocks. A file path does not determine its namespace.
- Compiler-packaged Solvik standard-library resources are automatically loaded before user files; their names remain fully qualified.
- A newline normally ends a statement; semicolons are allowed when multiple statements occupy one line. Parenthesized argument lists and other delimited constructs may span lines.
- Braces delimit blocks. There is no whitespace-sensitive indentation grammar.
- A statement may also be continued explicitly. When the first non-blank character of the line after a newline is `\`, that marker cancels the newline and the statement continues on the marker's line. The marker is layout, not program text; inside a delimited construct it is unnecessary — though harmless — because a newline there is already layout. Blank lines and comments may stand between the newline and the marker, so one marker continues the statement across every newline since its last line. A marker never cancels a semicolon, and a `\` that is not the first non-blank character of a continuation line is `SOL-L006`.

```solvik
message := "a"
\ .. "b"
\ .. "c"
TextWriter(Stdio.stdout()).writeLine((message))          // abc

mut total := 0
total = 1
\ + 2
\ + 3
TextWriter(Stdio.stdout()).writeLine((total))            // 6
```

## 2. Values, types, and nullability

### 2.1 Type hierarchy

The ordinary non-null type hierarchy is:

```text
Any
├── Number
│   ├── Byte, Short, Integer, Long
│   ├── Float, Double
│   └── BigInteger, BigDecimal
├── String
├── Boolean
├── Array<T>
├── Exception            // the one unchecked exception type (§7.4)
└── user and native types

Null                      // distinct singleton type, not a subtype of Any
T?                        // type constructor: T or Null
```

`Any` is the root of **non-null values**. `Null` is separate. `Any?` is the broadest nullable value type. `Null` is assignable to `T?` for any valid non-null `T`, but never directly to `T`. The exact type `Null` has one possible value: `Null`.

**The token `Null` has two roles distinguished by syntactic context:** in a type position it names the `Null` type; in an expression position it denotes that type's unique value. This is one singleton, **not** an allocated object or a call to a constructor. `Null()` is invalid. Lowercase `null` is not part of the revised language.

```solvik
empty := Null                    // inferred type: Null
explicitEmpty: Null = Null      // explicitly typed Null
optionalName: String? = Null    // String or Null
name: String = "Solvik"        // never Null
```

`T?` is an optional type constructor, permitted once at each type level. A repeated suffix such as `T??` and a redundant `Null?` are **syntax/type errors**. Nested qualifiers at different levels, such as `T?[2]?`, remain valid. Nullability does **not** create a general-purpose union type mechanism.

### 2.2 Nullability rules

| Source type | Legal values | Notes |
| --- | --- | --- |
| `String` | Non-null strings | Does not accept `Null` |
| `String?` | A string or `Null` | Nullable |
| `Integer` | Non-null integers | Numeric representation as specified below |
| `Integer?` | An integer or `Null` | Nullable |
| `Any` | Any non-null ordinary value | Does not accept `Null` |
| `Any?` | Any ordinary value or `Null` | Top nullable type |
| `Null` | Exactly `Null` | Singleton type |

Rules:

- `T` is assignable to `T?`; `Null` is assignable to `T?`.
- `T?` is **not** assignable to `T` unless control-flow analysis proves the value is non-null or an explicit checked conversion is performed.
- Member access, array indexing, arithmetic, and other operations requiring a value reject a potentially null receiver or operand at compile time unless the operation is specifically defined for nullable values.
- `==`, `!=`, `===`, and `String(value)` are null-safe where described below.
- Static flow narrowing may be applied after a reliable null check; it must never assume a mutable value remains non-null if it can be changed by another code path. If sound narrowing cannot be proven, require a checked conversion or a local non-null value.
- Nullable values are represented using Java references and Java `null`. `T?` does not require a wrapper object or a Solvik runtime library.

### 2.3 Runtime type representation

| Solvik | Java representation | Notes |
| --- | --- | --- |
| `Any` | `java.lang.Object` | Non-null guaranteed at Solvik-checked boundaries |
| `Number` | `java.lang.Number` | Numeric supertype |
| `Byte`, `Short`, `Integer`, `Long` | Corresponding boxed Java type | Signed 8-, 16-, 32-, 64-bit |
| `Float`, `Double` | Corresponding boxed Java type | IEEE 754 |
| `BigInteger`, `BigDecimal` | `java.math` types | Arbitrary precision |
| `String`, `Boolean` | Corresponding Java reference type | Non-null in Solvik unless marked `?` |
| `T?` | Same Java reference type as `T` | Java `null` represents `Null` |
| `Null` | Java `null` at value sites | A Java reference carrier is chosen where a declaration is required |
| `Array<T>` / fixed-size array | Fixed-size `java.util.List<T>` representation | Length and non-null element rules are enforced by Solvik |
| `Exception` | Generated nested `RuntimeException` subclass | Unchecked; carries an `Integer` code and a `String` message (§7.4) |
| User `type` | Generated final Java class | No class inheritance |
| Native `type` | Generated nominal adapter | Owns a Java delegate |
| `trait` | Generated Java interface | Required methods and stateless defaults |

A Java carrier type does **not** define Solvik assignability or null-safety. Compile-time checks and necessary Java-boundary validation preserve the language contract.

## 3. Binding declarations and assignment

### 3.1 Three distinct operations

| Syntax | Meaning | Example |
| --- | --- | --- |
| `name := expression` | Declare; infer non-nullable/exact expression type | `count := 1` |
| `name: Type = expression` | Declare; check against explicit type | `count: Integer = 1` |
| `name = expression` | Assign to existing mutable binding | `count = 2` |

These forms are not interchangeable. There is no `var` keyword. `name := expression` always **introduces a new binding**, never updates an existing one. Re-declaration in the same lexical scope is an error; shadowing across scopes follows the existing scoping rules.

```solvik
count := 10                 // immutable Integer
mut total := 0             // mutable Integer
total = count + 1          // assignment, not declaration

limit: Long = 100L         // immutable Long
mut threshold: Long = 10L  // mutable Long
threshold = 20L
```

Every binding declaration shown in this section has an initializer. The following are **invalid**:

```solvik
// value                      // no declaration operator or initializer
// mut value                  // missing initializer
// value: Integer             // missing '=' and initializer
// value :=                   // missing expression
// value = 10                 // undeclared assignment
// var value : 10             // removed syntax
// mut var value : 10         // removed syntax
```

A parameter is bound by the caller, not by a field/local initializer, so parameter declarations still use `name: Type` without `=`. A function declaration, type declaration, trait signature, and module declaration are not value-binding declarations.

### 3.2 Nullable inferred declarations: `name? := expression`

To declare an **inferred nullable** binding, place `?` immediately after the declared identifier:

```solvik
name? := "Ada"            // inferred String?
mut count? := 1            // inferred Integer?
mut later? := Null         // inferred Any?
```

`?` here is part of the **declaration syntax**, not part of the identifier. Read and write the binding using its ordinary name (`name`, `count`, or `later`), without `?`.

The inference rules are deterministic:

1. `name := value` infers the exact static type of `value`; a `Null` initializer therefore infers `Null`.
2. `name? := value`, when the initializer has a non-null type `T`, infers `T?`.
3. **`name? := Null` infers `Any?`**, because `Null` alone does not identify a narrower non-null type. It does not establish a future type-inference hole.
4. `name: T? = Null` declares the specific nullable type `T?` and is preferred whenever the intended type is known.
5. A binding's static type never changes on later assignment. Mutation changes its value, not its type.
6. `name?: Type = value` and `name?: Type? = value` are invalid: use `name: Type? = value` for explicitly typed declarations, avoiding two competing nullability annotations.

Examples:

```solvik
empty := Null                   // type Null, immutable
mut untyped? := Null           // type Any?, mutable
untyped = "hello"              // legal: String <: Any
untyped = 42                   // legal: Integer <: Any

mut title: String? = Null      // type String?, mutable
title = "Solvik"              // legal
title = 123                   // error: Integer is not String

mut inferred? := "hello"      // type String?, mutable
inferred = Null               // legal
inferred = "world"            // legal
```

Because `Any?` exposes only the operations valid for arbitrary nullable values, a declaration such as `mut later? := Null` does **not** magically become `String?` when assigned a string. Use an explicit type to retain static members and operations of `String?`.

### 3.3 Mutability and visibility

- Bindings and fields are immutable by default.
- `mut` enables assignment **after initialization**; it does not change the binding's type, visibility, or nullability.
- `pub` publishes a declaration within the existing module/type visibility rules.
- `static` is permitted **only on type members**, not on a type, module, local, or parameter. Write `static` first, followed by `pub`, then `mut` where applicable.
- `..=` appends to a mutable `String` binding, preserving existing string-conversion semantics. It is not a declaration operator.

```solvik
pub type Counter {
    pub mut count := 0
    static pub mut instances := 0

    pub func Counter() {
        instances = instances + 1
    }

    pub func increment() {
        count = count + 1
    }
}
```

Field declarations **also require initializers**. There are no uninitialized field slots. Constructors may assign instance fields during construction under the existing constructor-initialization rule; after construction, only `mut` fields may be assigned. Any constructor-time reassignment must preserve the field's declared static type and non-nullability. The compiler should avoid redundant Java initializations when it can prove removing them preserves observable behavior.

```solvik
type Point {
    pub x: Integer = 0
    pub y: Integer = 0

    pub func Point(x: Integer, y: Integer) {
        self.x = x
        self.y = y
    }
}
```

With this policy, generic data fields that have no universal non-null initial value must use a nullable initializer or a different construction pattern:

```solvik
type Box<T> {
    mut value: T? = Null

    pub func Box(value: T) {
        self.value = value
    }
}
```

This example guarantees initialization but deliberately does **not** pretend that the field has type `T`. A method returning `T` must first prove its nullable backing field is non-null. A future constructor-required-field design would be a **separate** language feature.

### 3.4 Static members

```solvik
type Visitors {
    static mut count := 0
    static label := "visits"
    static pub kind := "counter"
    static pub mut limit := 2

    pub func Visitors() {}

    pub func visit() -> Integer {
        count = count + 1
        return count
    }

    static pub func reset() {
        count = 0
    }

    static pub func report() -> String {
        return label .. "=" .. count
    }
}

visitor := Visitors()
visitor.visit()
Visitors.reset()
Visitors.limit = 5
TextWriter(Stdio.stdout()).writeLine((Visitors.report()))
```

```solvik
type Registry {
    static mut trace := "one"
    static pub first := trace
    static {
        trace = trace .. "-two"
    }

    static pub func state() -> String {
        return trace .. ":" .. first
    }
}
```

- Every static field has an initializer, evaluated once in source order during generated Java class initialization.
- A type declares **at most one** `static { ... }` block. By convention it is written after all fields and before the first function, so the fields are initialized before the block runs. A second block is `SOL-P004`.
- A `static { ... }` block is a statement list evaluated **once**, after the static field initializers that precede it, as part of class initialization. `Registry.state()` above sees `one-two:one`.
- Class initialization happens on the first **active use** of the type: construction, a static function call, or a static field read/write. A type used only in a declaration, signature, `instanceof`, or a `Null` reference is not initialized.
- A static block has no `self` and cannot use the enclosing generic type's parameters. Its locals are scoped to the block. It may call static members, write `static mut` fields, and construct values (including the enclosing type through a type-private constructor). A write to a non-`mut` static field is `SOL-T010`.
- `return` is not allowed in a static block (`SOL-T005`); there is no result to return. `break` and `continue` keep their usual outside-loop diagnostics.
- A static block may `raise` or call `exit`, but it runs during class initialization, so the failure is not catchable by a Solvik `try`: the program terminates and the CLI reports the underlying failure (`SOL-R013` for a raised exception, the exit status for `exit`, or the usual `SOL-Rxxx` code for an abort). A failed initialization marks the type erroneous; later active uses fail rather than rerunning the block.
- Static blocks may read only fields whose initializers have already run for a meaningful value. Fields declared later still hold their Java defaults at that point; source order is the contract.
- Static blocks are permitted only in concrete types. A trait or a `native "class"` binding cannot declare one (`SOL-P004`).
- A static method has no `self` and cannot access instance members without an instance.
- Static fields and methods are accessed through their declaring type; instance-qualified static access is invalid.
- Static members of generic types cannot use those types' type parameters in a Java-incompatible static context.
- Constructors cannot be `static`.

## 4. Expressions, literals, and precedence

### 4.1 Literal forms

```solvik
answer := 42
fraction := 1.5
active := true
message := "line one\nline two"
raw := r#"C:\path\name"#
missing := Null
```

- String escapes: `\\`, `\"`, `\n`, `\t`, `\r`, and `\0`.
- Raw strings use Rust-style `r#"..."#`; backslash escapes are not interpreted.
- Lowercase `null` is invalid. `Null` is the singleton value.
- Integer literals default to `Integer`; fractional literals default to `Double`, except where an expected numeric type applies (such as an array element).

### 4.2 Numeric literal suffixes

| Suffix | Type | Example |
| --- | --- | --- |
| `B` | `Byte` | `-128B` |
| `S` | `Short` | `120S` |
| `I` | `Integer` | `42I` |
| `L` | `Long` | `9223372036854775807L` |
| `F` | `Float` | `1.25F` |
| `D` | `Double` | `1.25D` |
| `BI` | `BigInteger` | `12345678901234567890BI` |
| `BD` | `BigDecimal` | `1.50BD` |

A suffix fixes the literal's type even in a typed array. Unsuffixed numeric array elements may adopt their declared element type. Literal values must fit their carrier; invalid suffixes and out-of-range values are compile-time errors. `BigDecimal` literals preserve scale (`1.50BD` has scale 2). Fractional literals may use `F`, `D`, or `BD`, not integer carrier suffixes. An array size follows the positive-size rule in §5; its source spelling is an `Integer` literal, not `Long`.

### 4.3 Precedence

From lowest to highest precedence:

1. `||`
2. `&&`
3. `==`, `!=`, `===`
4. `<`, `<=`, `>`, `>=`
5. `..`
6. `+`, `-`
7. `*`, `/`, `%`
8. Unary `-`, `!`
9. Postfix calls `()`, member access `.`, and indexing `[]`

Grouping parentheses override precedence. Evaluation is left-to-right as defined by the language; `&&` and `||` short-circuit. Java lowering must preserve exactly-once evaluation and exception/side-effect order.

### 4.4 Arithmetic and conversion

`+`, `-`, `*`, `/`, and `%` operate on numeric types. Primitive numeric promotion follows the existing Java-aligned rules (`Byte`/`Short` to `Integer`, then `Long`, `Float`, `Double`). Integral overflow wraps as defined by the selected Java carrier. `BigInteger` and `BigDecimal` use exact big-number operations; they do not mix implicitly with primitive numeric carriers. `%` is unsupported on `BigDecimal`.

Integral or big-number division/remainder by zero preserves the existing `SOL-R001`/`SOL-R002` diagnostics; non-terminating `BigDecimal` division preserves `SOL-R003`; floating-point division follows IEEE 754.

Conversions use a type name with one argument:

```solvik
wide := Long(123)
precise := BigDecimal("1.50")
text := String(precise)
flag := Boolean("true")
```

Numeric conversions are range-checked; provably invalid literal conversions fail during compilation, otherwise invalid conversions report the established runtime diagnostics. `String(value)` accepts `Null` and yields `"null"`. `Boolean` accepts a Boolean or exactly the strings `"true"` / `"false"`. Numeric text is not trimmed or normalized.

**Nullable conversion rule:** `T?(value)` is **not** an implicit assertion that `value` is non-null. If an explicit checked nullable-to-non-null conversion is supported by the compiler, it must be spelled using a documented operation with runtime failure behavior; otherwise, require control-flow narrowing. Never silently unbox `Null` into an ordinary `T`.

Public Java instance methods on built-in `BigInteger` and `BigDecimal` remain available when their signatures map to Solvik types; unsupported Java signatures require explicit native bindings or produce a type diagnostic. No runtime reflective dispatch is introduced.

### 4.5 Equality and identity

- `==` and `!=` are **null-safe**. `Null == Null` is true; `Null == 1` is false.
- Numeric `==` follows the existing statically determined promotion rules for primitive numeric carriers. Big-number equality retains its existing Java-boxed meaning, including `BigDecimal` scale sensitivity.
- `===` compares value identity for built-in scalar values and reference identity for arrays and user/native objects, retaining current behavior. `Null === Null` is true.
- Arrays and user/native objects compare by reference with `==` unless a future language change explicitly says otherwise. Calling `.equals(...)` is separate from the equality operator.
- `.equals`, `.hashCode`, and `.toString` on a potentially null receiver are not implicitly safe. A nullable value must be narrowed or checked first.
- A user override of the Java object contracts uses `toString() -> String`, `hashCode() -> Integer`, and `equals(other: Any?) -> Boolean` in the revised null-safe type system. The Java bridge must still meet Java's primitive return and `Object` signature requirements. The `Null` case of `equals` returns false without invoking a peer-specific body.
- A `Null` value may be passed only to parameters that accept it (`Null`, `T?`, or `Any?`), not to plain `Any`.

### 4.6 Strings and output

`..` concatenates operands using Solvik's value-rendering convention. `..=` appends to a mutable string.

```solvik
name := "Solvik"
TextWriter(Stdio.stdout()).writeLine("Hello, " .. name)
TextWriter(Stdio.stdout()).writeLine("missing=" .. Null)
```

There are **no output built-ins**. Text output is ordinary standard-library API use through
`TextWriter` (§11.6), constructed over a writable `Channel`:

```solvik
out := TextWriter(Stdio.stdout())
out.write("no terminator is added")
out.writeLine("")                         // exactly one TextWriter.LINE_ENDING
```

`writeLine(text)` writes `text` followed by `TextWriter.LINE_ENDING`, the platform's line
terminator (`"\n"` on POSIX systems, `"\r\n"` on Windows), read once at type initialization.
`write(text)` writes the text's UTF-8 bytes and translates nothing: a `"\n"` inside `text` stays
`"\n"` on every platform. A program that needs a fixed terminator writes it explicitly, for
example `out.write(text .. "\n")`. `Null` renders as `null` (lowercase **text**, not lowercase
Solvik syntax), arrays render as `[a, b]`, and `BigDecimal` renders using plain notation and
preserves scale; concatenation and `String(value)` use that same rendering. Neither writer
function returns a value. `TextWriter.LINE_ENDING` is a public immutable static `String`, so
`writer.LINE_ENDING` is invalid and assigning it is `SOL-T010` (§3.4, §13.2).

## 5. Array types, values, and nullability

Arrays have fixed length, mutable elements, and reference identity. Solvik's array operations lower to Java operations without a dedicated runtime dependency.

**Array and element nullability are independent.** The `?` operator modifies the type immediately to its left.

| Type | Array itself may be `Null`? | Elements may be `Null`? |
| --- | --- | --- |
| `String[2]` | No | No |
| `String?[2]` | No | Yes |
| `String[2]?` | Yes | No |
| `String?[2]?` | Yes | Yes |

For the revised language, a fixed length `N` in `T[N]` is part of the fixed-array type. `T[2]` and `T[3]` are distinct fixed-length types; a size-erased `Array<T>` abstraction may be used where the API accepts varying lengths. Fixed-array length can be represented through metadata and validated statically when known; Java-native interop must also validate lengths when a fixed-size contract crosses the boundary.

### 5.1 Array construction

```solvik
names := String[2] {"Ada", "Grace"}
mut optionalNames := String?[2] {"Ada", Null}
emptySlots := String?[2] {}                // both elements initialized to Null
mut optionalArray: String[2]? = Null
mut everythingNullable: String?[2]? = Null
```

Construction rules:

- Array sizes are compile-time non-negative `Integer` literals within the supported JVM allocation range. This revision retains the existing size requirement `1..2147483647`; a zero-length array is not permitted until explicitly designed.
- For **non-nullable element types**, an initializer must provide exactly `N` elements. Short or empty initializer lists are errors; no implicit Java null slots are exposed as valid Solvik values.
- For **nullable element types**, the initializer may provide at most `N` elements; omitted elements are **explicitly defined by the array-construction rule** to be `Null`. This is **not** an implicit initializer for a variable declaration—the array construction expression itself is the initializer.
- Every provided element must be assignable to the element type. `Null` is accepted only by nullable elements.
- An array variable or field declaration still requires an initializer even when its array type itself is nullable.
- `a[i]` requires a non-null array receiver and a valid index. `a[i] = value` additionally requires an element-compatible value; element mutation is allowed even when the array binding is immutable, as in the existing language.
- `a.size` is the array's only member: an immutable `Integer` holding the length the array was
  constructed with. It requires a non-null receiver, is valid for a fixed `T[N]` and a
  size-erased `T[]`, and is read without parentheses. Because the member is immutable,
  `a.size = value` is an error, and the call form `a.size()` is not part of the language.
- Out-of-range access retains the existing `SOL-R004` runtime diagnostic. Numeric index requirements continue to follow current rules.

Examples of invalid source:

```solvik
// names := String[2] {}                    // uninitialized non-null elements
// names := String[2] {"Ada"}               // one missing non-null element
// names := String[2] {"Ada", Null}         // Null is not String
// names := String?[2] {"Ada", "B", "C"}   // too many elements
// names: String[2] = Null                  // array is non-nullable
// names.size()                            // the length is a member, not a function
// names.size = 2                          // the size member is immutable
```

### 5.2 Array nesting

Apply nullability recursively:

```solvik
rows := String[2][2] {
    String[2] {"a", "b"},
    String[2] {"c", "d"}
}

rowsWithMissingElements := String?[2][2] {
    String?[2] {"a", Null},
    String?[2] {"b", "c"}
}
```

`String?[2][2]` describes a non-null outer array of non-null inner arrays whose **strings** may be `Null`. `String[2]?[2]` would permit `Null` in the outer array as an **inner array element**. The compiler must preserve these distinctions through type checking and Java lowering.

The member reads one level at a time: `rows.size` is the outer length and `rows[0].size` is
the length of the first inner array (see the examples above).

## 6. Strings

The existing `String` member surface is retained:

- `size()`, `substring(start[, end])`, `indexOf`, `contains`.
- `startsWith`, `endsWith`, `strip`, `stripLeading`, `stripTrailing`.
- `toUpperCase`, `toLowerCase`, `replace`, `split`.

Indices are zero-based; existing text-index and split runtime diagnostics (`SOL-R009`, `SOL-R010`)
are preserved. `replace` uses literal replacement. `split` uses a non-empty literal separator,
retains empty parts, and returns an array of **non-null** strings. That array is size-erased
(`T[]`), so its length is read with the array member `size` from §5: `parts := text.split(",")`
then `parts.size`. A `String?` receiver must be proven non-null before invoking a string member.

## 7. Control flow

```solvik
mut remaining := 3
while remaining > 0 {
    remaining = remaining - 1
}

if remaining == 0 {
    TextWriter(Stdio.stdout()).writeLine(("done"))
} else {
    TextWriter(Stdio.stdout()).writeLine(("pending"))
}

for item in String[2] {"A", "B"} {
    TextWriter(Stdio.stdout()).writeLine((item))
}
```

- Conditions require non-null `Boolean`. There is no truthiness and no implicit conversion from `Boolean?`.
- `if`, `else if`, and `else` follow standard block-based control flow.
- `while`, `for ... in`, `break`, and `continue` preserve existing semantics.
- `break` and `continue` target the innermost loop, not a `switch`.
- `try`, `catch`, `finally`, and `raise` handle exceptions (§7.4). A `try` statement is a statement,
  not an expression, so it produces no value.

### 7.1 Null checks

```solvik
mut label: String? = Null
label = "ready"

if label != Null {
    // A soundly proven non-null label is usable as String here.
    TextWriter(Stdio.stdout()).writeLine((label.size()))
}
```

When type narrowing is implemented, it must operate on expression/binding identity and sound dataflow analysis, not just string matching of variable names. If the value might have been reassigned, require a safe local copy or an explicit checked conversion before dereferencing it.

### 7.2 Switch and regex cases

```solvik
switch status {
    case "ok" { TextWriter(Stdio.stdout()).writeLine(("accepted")) }
    case regex "ERR-[0-9]+" { TextWriter(Stdio.stdout()).writeLine(("error code")) }
    default { TextWriter(Stdio.stdout()).writeLine(("other")) }
}
```

A switch executes exactly one arm; there is no fall-through. Existing supported subject types (`String`, `Integer`, `Byte`, `Boolean`), literal-label restrictions, whole-string regex matching, and once-per-generated-class regex compilation remain unchanged. The switch subject must be non-null unless the revised type checker expressly adds a `Null` arm model. Do not silently treat a missing nullable value as a default-match value.

### 7.3 Termination

`exit()` and `exit(code)` terminate the program using the existing 0–255 status contract and `SOL-R007` validation.

### 7.4 Exception handling

Solvik has exactly one exception type, because it has no inheritance. `Exception` is a built-in,
**unchecked** type carrying an `Integer` code and a `String` message. A program raises one value and
recovers from it with `try`, `catch`, and `finally`, whose semantics are Java's own.

```solvik
type Codes {
    static pub Denied := 4001
}

func check(limit: Integer) {
    if limit < 0 {
        raise Exception(Codes.Denied, "negative limit")
    }
    TextWriter(Stdio.stdout()).writeLine(("limit ") .. (limit))
}

try {
    check(-1)
} catch (error: Exception) {
    TextWriter(Stdio.stdout()).writeLine((error))                        // [4001] negative limit
    if error.getCode() == Codes.Denied {
        TextWriter(Stdio.stdout()).writeLine(("denied: ") .. (error.getMessage()))
    }
} finally {
    TextWriter(Stdio.stdout()).writeLine(("done"))
}
```

**The value.** `Exception(code, message)` constructs one: the code is an `Integer` and the message a
non-null `String`. Two functions read it back and nothing else is readable or writable:
`getCode() -> Integer` and `getMessage() -> String`. Printing, `String(value)`, and `..` render
`[code] message`, and `value.type()` is `"Exception"`. The code is the dispatch key, because one type
cannot be matched by name: compare it against `static` constants, or `switch` on it with literal
labels (§7.2). Codes are non-negative; a negative code aborts with `SOL-R012`, exactly as a computed
out-of-range exit status aborts with `SOL-R007`. `Exception?` is an ordinary nullable type: a value
proven non-null by a check (§7.1) exposes its functions, and `Null` is never an exception.

**Raising.** `raise expression` raises a non-null `Exception`. It never completes normally, so a
statement after it in the same list is unreachable, and a function whose body can only raise needs no
return. `raise Null`, `raise 1`, and raising a possibly-null `Exception?` are compile errors
(`SOL-T001`, `SOL-T015`).

**Clauses.** A `try` statement has at most **one** `catch` clause, because `Exception` is the only
catchable type, and needs at least one of `catch` and `finally`; `try` on its own is `SOL-P002`, and a
second `catch` is too. `catch (name: Exception)` binds an immutable, non-null value that is visible
only inside its own block; the written type must be exactly `Exception` (`SOL-T016`). As with
`else`, a clause keyword follows the closing `}` on the same line (§1). `raise` is allowed in a
`catch` or a `finally`; `return` is not allowed in a `finally`, because it would silently discard a
pending exception or return value (`SOL-T017`), while `break` and `continue` keep Java's behavior.

**Control flow.** `finally` runs on every way out of its `try`: normal completion, `raise`, `return`,
labeled or unlabeled `break`/`continue`, an uncaught exception, and `exit()`. A `try` statement can
complete normally only if a clause it can fall out of can: `try { raise e }` with a `catch` that also
raises leaves, while the same body with a `finally` that completes normally does not.

**What is catchable.** Only what `raise` raises. A `SOL-Rxxx` runtime abort (division by zero, array
bounds, a failed conversion, a failed native call), an uncaught Java exception from a native call,
and `exit()` all pass a `catch` by and keep their existing behavior and codes; a `try` around them
still runs its `finally`. An exception that no clause handles reaches the top of the program, which
reports `SOL-R013` with the raised code and message and exits `1`.

**Unchecked.** There is no `throws` surface and no checked-exception taxonomy: `raise` is valid
anywhere a statement is, and a function declares nothing about what it may raise. Java checked
exceptions stay mapped to `SOL-R011` at the native-call boundary (§11.2).

## 8. Functions, constructors, and generics

```solvik
func factorial(n: Integer) -> Integer {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

func missing() -> String? {
    return Null
}

func noValue() {
    TextWriter(Stdio.stdout()).writeLine(("side effect"))
}
```

- Parameter declarations have the syntax `name: Type` and receive values from the caller. A nullable parameter must be declared `name: T?`.
- `-> Type` declares a value-returning function; omitting `->` declares a function that returns no value and is callable only as a statement.
- `Null` is a valid return type for a function that can only return `Null`; it is not the same as a function with no return value.
- Recursion and forward references remain supported.
- Overloads use function name and ordered parameter types. Return type, parameter names, visibility, and `static` do not distinguish signatures.
- Nullable and non-nullable Java-erasure-equivalent overloads (for example `f(x: String)` and `f(x: String?)`) **cannot coexist** when they would generate the same Java signature. The compiler must reject collisions deterministically.
- Overload applicability checks nullability. `Null` matches a nullable parameter, not an ordinary non-null parameter; multiple unrelated nullable overloads may be ambiguous (`SOL-T014`).
- Existing most-specific-overload behavior remains in force wherever consistent with explicit nullability.
- Generic arguments retain existing construction and binding rules; Java erasure collisions remain errors.

### 8.1 Concrete types and constructors

```solvik
type Counter {
    pub mut count := 0

    pub func Counter() {}

    pub func increment() {
        count = count + 1
    }

    pub func get() -> Integer {
        return count
    }
}

counter := Counter()
counter.increment()
TextWriter(Stdio.stdout()).writeLine((counter.get()))
```

- Types use composition; user types do not inherit concrete classes.
- Constructors are functions with the same name as the enclosing type. No implicit constructor is supplied.
- Fields and methods are private by default, `pub` when exported.
- Code in one concrete type may access private members of another value of the **same** type.
- Fields are immutable after construction unless declared `mut`. Constructor-time writes retain the existing special construction rule; they must never leave a field without an initialized value or violate its nullability.
- A user type derives from `Any` conceptually and may implement traits.

### 8.2 Generic types

```solvik
type Holder<T> {
    mut value: T? = Null

    pub func Holder(value: T) {
        self.value = value
    }

    pub func hasValue() -> Boolean {
        return self.value != Null
    }
}

box := Holder(41)
TextWriter(Stdio.stdout()).writeLine((box.hasValue()))
```

Nullability composes with generic parameters: `T` is non-null where the type parameter's declared contract requires a non-null value, and `T?` explicitly permits `Null`. Generic constraint and Java erasure validation must not confuse nullable and non-nullable representations. User-generic type argument inference remains constructor-based unless separately revised; native bindings may name type arguments as already supported.

## 9. Traits and interface behavior

Traits define nominal contracts and optional stateless defaults:

```solvik
trait Task {
    pub func run()

    pub func description() -> String {
        return "task"
    }
}

type Job implements Task {
    pub func Job() {}
    pub func run() {
        TextWriter(Stdio.stdout()).writeLine(("working"))
    }
}
```

- A trait contains public instance functions only, with no fields, constructors, static methods, type parameters, or trait inheritance under the current contract.
- Bodyless functions are required contracts. A function with a body is a default implementation.
- The default sees `self` as the declaring trait; it can call the trait's contract and `Any` methods but cannot access implementing-type-only fields.
- A concrete public implementation overrides a matching default and works through concrete and trait-typed references.
- Two defaults for the same signature require an explicit concrete override. One default may satisfy matching bodyless requirements; parameter and return types must match exactly, including nullability.
- Trait defaults cannot implement the reserved object-contract methods (`type`, `toString`, `equals`, `hashCode`) under the existing Java-interface mapping.

An override may invoke a default from a directly implemented trait:

```solvik
pub func delayMs(attempt: Integer) -> Long {
    return 2L * Retry.delayMs(self, attempt)
}
```

`Trait.method(self, args...)` selects that trait's default body instead of virtual dispatch. The explicit source-level `self` is omitted from the Java call. Module-qualified traits use the complete name (for example, `network::Retry.delayMs`).

## 10. Modules, visibility, and imports

```solvik
module geometry {
    pub type Point {
        pub x := 0
        pub func Point(x: Integer) { self.x = x }
    }

    pub func origin() -> Point {
        return Point(0)
    }
}

TextWriter(Stdio.stdout()).writeLine((geometry::origin().x))
```

- `module` declares namespaces; `::` selects a module-qualified member. `.` selects an instance or type member.
- Module paths map to Java packages; module functions compile into package-local generated functions holders as defined by the Java backend.
- Module blocks may be reopened in the same or different explicitly imported files. Equal full module paths merge; nested blocks merge recursively.
- Declaration privacy is at the containing **module** or declaring **type**, not the physical source file.
- Outside access requires `pub`. A public signature may not expose a private type.
- Types, child modules, and functions share a checked namespace. Duplicate declarations and cross-kind conflicts are errors; legitimate function overloads follow the signature rules.
- User source cannot open the compiler-reserved root `solvik` module. Only compiler-packaged resources can contribute to it.
- `java.*` names remain restricted by Java package rules; any remapping must remain deterministic and consistent with the backend contract.
- Importing a file does not implicitly import short names. Qualified standard-library names remain required.

## 11. Java interoperability and native bindings

**Java ecosystem interoperability is a primary Solvik design goal.** Solvik native declarations expose specifically selected Java APIs with Solvik names and types. These declarations generate ordinary Java calls with compile-time signature resolution, not reflection-based runtime dispatch.

```solvik
module jdk {
    pub type Math native "java.lang.Math" {
        static pub func abs(value: Integer) -> Integer
        static pub func max(a: Integer, b: Integer) -> Integer
        static pub func sqrt(value: Double) -> Double
    }
}

TextWriter(Stdio.stdout()).writeLine((jdk::Math.abs(-3)))
```

An explicit, typed collection binding might be:

```solvik
module bindings {
    pub type Map<K, V> native "java.util.HashMap" {
        pub func Map()
        pub func put(key: K, value: V)
        pub func get(key: Any) -> V?
        pub func removeValue(key: Any) -> V? native "remove"
        pub func size() -> Integer
    }
}

scores := bindings::Map<String, Integer>()
scores.put("ann", 10)
value := scores.get("ann")          // Integer?, not Integer
```

The nullable return on `get` matters: Java `HashMap.get` can return `null`. A Java method whose documented contract allows null should be declared with a nullable Solvik return type. Native signatures cannot promise non-null values without enforcing that contract.

### 11.1 Binding rules

- Bindings export **only** declared members. Other members of the Java class remain inaccessible through that Solvik binding.
- A native `type` targets a supported public concrete Java class and generates a nominal adapter; a native `trait` targets a supported public Java interface.
- Instance binding calls use a private delegate. Static calls target Java static methods directly. Constructors cannot be static.
- `native "javaName"` on a member maps a Solvik-facing name to another Java member name.
- Resolution is performed at compile time against exactly the configured JDK and application classpath; invalid or ambiguous signatures are errors (`SOL-T013`).
- Native bindings preserve existing generic arity and Java erasure restrictions.
- A Java return value may be ignored by a binding declaration without `->`, preserving the statement-only convention.
- Method calls must preserve Java argument evaluation order and exception behavior.
- Java-null acceptance for parameters is represented by nullable Solvik parameter types; unannotated Java APIs do not implicitly override Solvik's explicit nullability.
- If a declared **non-nullable** native return or result is `null` at runtime, the generated boundary must fail deterministically with the established null-required diagnostic (`SOL-R008`) rather than silently admit it into a non-null Solvik value.
- If a Java binding returns an array or collection claimed to contain non-null elements, the compiler must enforce that claim at the appropriate boundary or defer a guaranteed validation until element access; never present unchecked Java null contents as proven non-null Solvik values.
- Java method overloading cannot distinguish `T` from `T?` when their erased Java parameter types are identical.
- Unsupported Java shapes (fields, Java varargs, wildcards, callbacks, etc.) remain unsupported unless separately implemented.

### 11.2 Java exception boundary

The existing implementation maps checked Java exceptions to `SOL-R011`, while unchecked exceptions propagate. A `raise`d Solvik `Exception` is catchable (§7.4); a Java exception that reaches Solvik code from a native call is **not**, so native failure keeps its established classification rather than becoming a `catch` target. In all cases, Java interop must not make `Null` a throwable value.

### 11.3 Packaged standard library

The compiler packages standard-library `.sol` resources, loaded into every program, currently including:

| Namespace | Type | Java or Solvik implementation |
| --- | --- | --- |
| `solvik::collections` | `Map<K, V>` | `java.util.HashMap` |
| `solvik::collections` | `List<T>` | `java.util.ArrayList` |
| `solvik::collections` | `Stack<T>` | `java.util.Stack` |
| `solvik::concurrency` | `Task` | `java.lang.Runnable` |
| `solvik::concurrency` | `Thread` | Java virtual-thread adapter |
| `solvik::util` | `UUID` | Solvik source with JDK bindings |
| top level (no import) | `Channel` | Solvik declaration with bundled native bindings |
| top level (no import) | `Stdio` | Solvik static-only declaration with bundled native bindings |
| top level (no import) | `TextReader` | Solvik declaration with bundled native bindings |
| top level (no import) | `TextWriter` | Solvik declaration with bundled native bindings |
| top level (no import) | `ByteReader` | Solvik source over a borrowed `Channel` |
| top level (no import) | `ByteWriter` | Solvik source over a borrowed `Channel` |
| top level (no import) | `File` | Solvik source with bundled filesystem bindings |
| top level (no import) | `IoError` | Solvik static-only code namespace for I/O failures |

The standard library is not exempt from the revised type system. Its `.sol` files must migrate away from `var` and lowercase `null`, add all required initializers, and accurately specify nullable values returned by Java APIs. Packaged resources remain declaration-only; importing them as ordinary file paths is not how standard-library registration works.

The I/O types are declared at the top level (no module), so their names are available without
qualification exactly like the built-in scalar types. A program is not required to import them.
Only a packaged type a program actually mentions is emitted into the generated Java, so a program
that performs no I/O carries neither the I/O types nor their binding adapter.

### 11.4 Application classpath

```bash
./solvik run --cp "lib/a.jar:lib/b.jar" app.sol
```

Classpath entries are explicit readable JAR files, not directories, wildcards, automatic Maven downloads, or manifest-based dependency resolution. The same configured JARs participate in binding resolution, `javac` compilation, and JVM execution. JAR identity, order, and JDK version participate in compilation caching. Binding execution uses normal JVM permissions; it is not sandboxed.

### 11.5 Concurrency

The packaged `solvik::concurrency::Task` and `Thread` interfaces remain based on explicit task objects (not closures). `Thread.start(Task)` uses Java 25 virtual threads, `await()` maps to `join`, and `await(milliseconds: Long)` maps to timed `join`. Existing timeouts, interruption handling, and Java memory-visibility guarantees remain in force. Introducing nullable types does not change Java's `join` visibility semantics.

### 11.6 Bundled native declarations and standard I/O

Solvik's public standard-library types are `.sol` declarations. The inherently platform-specific
operations of `Channel`, `Stdio`, `TextReader`, and `TextWriter` are **bundled native declarations**:

```solvik
type TextWriter {
    static native func lineSeparator() -> String
    static pub LINE_ENDING := lineSeparator()

    pub native func TextWriter(channel: Channel)
    pub native func write(text: String)

    pub func writeLine(text: String) {
        write(text .. LINE_ENDING)
    }

    pub native func flush()
    pub native func close()
}
```

- `native` is a declaration modifier, written before `func`: `pub native func`,
  `static pub native func`, or `pub native func TypeName(...)` for a constructor. A native declaration
  has a signature and **no Solvik body**; a supplied body is a syntax error.
- Only a type parsed from a **compiler-packaged resource** may declare one. A program file that
  writes `native` is rejected (`SOL-T001`), so there is no arbitrary-Java escape hatch. A member
  of a `native "class"` binding or of a trait uses the existing forms instead.
- The compiler resolves each native declaration to an exact entry in its own binding registry.
  Owner, member name, static/instance designation, parameter types, and return type must match.
  A missing or mismatched entry is `SOL-T013`, never a stub, a `Null` fallback, or reflection.
- A type with any native member is a **native-backed Solvik type**. Its generated class is still an
  ordinary Solvik class — fields, `pub`, `mut`, `type()`, and Solvik-bodied methods behave as usual
  — and gains three compiler-generated members no Solvik identifier can name: a private `$payload`
  field holding the adapter value, a public static `$of(payload)` factory, and a public
  `$payload()` accessor. A native constructor computes the payload; a native instance method
  passes the receiver's payload; a native static method passes no receiver.
- The adapter that implements the registry is generated into the program only when a binding is
  reached. It is compiler-emitted Java that uses the JDK and the program's generated `Exception`,
  not an installed runtime, and its `$` name is not a Solvik identifier.

**Standard I/O.** `Channel` is an opaque byte endpoint with `read`, `write`, `writeAll`, `flush`,
`close`, `isOpen`, `canRead`, and `canWrite`; `Channel.bytes()` returns a new duplex in-memory
channel whose writes append and whose reads consume from the front. `Stdio` exposes the three
currently assigned standard channels and setters that replace one binding after validating that the
replacement is open and has the required direction. `TextReader` decodes strict UTF-8 and returns
each line without its LF, CRLF, or CR terminator: `""` for an empty line, the final unterminated
line at end of input, and `Null` only when no characters remain. `TextWriter` encodes strict UTF-8,
writes all bytes or raises, flushes after each successful write, and never translates line
separators. Readers and writers borrow their channel: `close()` releases only the reader or writer
and never closes the channel. The original standard channels are protected; closing one raises an
I/O error rather than closing a process handle.

**File and byte I/O.** `File.openRead(path)`, `File.openWrite(path)`, and `File.openAppend(path)`
open an owned single-direction file channel; `openWrite` creates or truncates, `openAppend` creates
or appends, and `openRead` requires an existing file. A `File` exposes that channel through
`channel()`, reports `path()` and `isOpen()`, and `close()` closes the owned channel. `File.exists`
and `File.delete` are static operations; `delete` returns `false` for an already-absent path and
raises `IoError.Io` for a directory rather than removing it. Paths
are ordinary JVM filesystem paths in the process working directory: there is no sandbox, no
automatic parent creation, and no symlink policy beyond the JVM's own. `ByteReader(channel)` reads
`Byte[]` chunks with the same EOF (`Null`) and length rules as `Channel.read`; `ByteWriter(channel)`
writes every byte through `Channel.writeAll`, and `flush` and `close` delegate to the channel.
Both wrappers borrow: closing one never closes its channel. A closed `File`, `ByteReader`, or
`ByteWriter` raises `IoError.Closed`; a read on a write-only or a write on a read-only channel
raises `IoError.Unsupported`. `File` and a borrowed channel are independent: closing the channel
directly is visible to every alias, while `File.close()` remains a harmless owned close.

**I/O failures.** `IoError` publishes the codes carried by the one `Exception` type:

| Constant | Code | Meaning |
| --- | --- | --- |
| `IoError.Io` | 5001 | a read, write, flush, or close failed |
| `IoError.Closed` | 5002 | the channel, reader, or writer is closed |
| `IoError.Unsupported` | 5003 | the channel has the wrong direction, or a standard channel was closed |
| `IoError.Encoding` | 5004 | malformed UTF-8 input or an unencodable string |
| `IoError.InvalidArgument` | 5005 | a non-positive read length or an invalid write range |

An I/O failure is a raised `Exception`, so `try`/`catch` recovers from it and `getCode()` dispatches
the category. It is not a `SOL-Rxxx` runtime abort.

## 12. Generated Java and compiler invariants

- Solvik emits standalone Java 25 `.java` files and compiles them using `javac`.
- The compiler should separate Solvik semantic analysis, Java lowering, typed Java IR, and deterministic Java source rendering.
- Source rendering must not decide language semantics or insert arbitrary executable Java fragments as its ordinary generation mechanism.
- Java output must preserve evaluation order, nullability guards, exact numeric semantics, error classifications, visibility, and deterministic naming.
- Nullable types use Java references, not nullable wrapper allocations. `Null` lowers to Java `null` where permitted by the Java carrier.
- An array's `size` member lowers to the carrier's `size()` call. It allocates nothing, adds no
  support member, and reads the same length for a fixed `T[N]` and a size-erased `T[]`.
- A non-null Solvik array must never be exposed to Solvik code with uninitialized non-null elements.
- `Exception` lowers to one nested `RuntimeException` subclass in the program class, emitted only when
  the program raises, catches, or constructs one. `raise` is a Java `throw`, `catch (e: Exception)` is
  a Java `catch` of that nested type, and `finally` is Java's `finally`, so unwinding, labeled loop
  control, and rethrow need no support code. Because the caught Java type is the Solvik exception
  class, a `SOL-Rxxx` abort, `exit()`, and a Java exception from a native call pass a `catch` by
  construction.
- An uncaught Solvik exception keeps its program-visible code and message (`SOL-R013`).
- A native-backed Solvik type emits its ordinary class plus a private `$payload` field, a static
  `$of(payload)` factory, and a `$payload()` accessor. Native members and constructors emit direct
  adapter calls with no reflection and no dynamic dispatch. The adapter is a generated source file
  (`solvik/Solvik$Io.java`) emitted only when a binding is reached; it is not a Solvik-visible type
  and not a runtime library.
- A packaged type the program does not mention is not emitted, so an unreached standard-library
  declaration costs no generated source and pulls in no binding support.
- Generated classes do not inherit from user classes. Traits emit Java interfaces; native declarations emit adapters or interface bridges.
- Modules emit Java packages. Static fields/methods emit Java static members; instance and type-member calls retain their ownership distinction.
- Intrinsics needed for rendering, conversions, exceptions, and diagnostics are generated into the program only as required. No external Solvik runtime library is introduced.
- For identical compiler version, source inputs, classpath, and options, emitted source must be deterministic.
- Prefer clean, readable, conventional Java rather than shortest-text output. Runtime performance remains higher priority than appearance.

## 13. Diagnostics, failures, and compatibility

The compiler reports file, line, column, and useful source context for lexical, syntax, type, and import errors. Existing diagnostic families remain:

| Family | Purpose |
| --- | --- |
| `SOL-L001`–`SOL-L007` | Lexing, strings, escapes, numeric suffixes, continuation markers |
| `SOL-P001`–`SOL-P004` | Parsing and declaration placement |
| `SOL-T001`–`SOL-T017` | Types, scopes, visibility, mutability, generics, bindings, overloads, exceptions |
| `SOL-I001`–`SOL-I004` | Imports and packaged-resource restrictions |
| `SOL-R001`–`SOL-R013` | Defined runtime errors, exception failures, and native-call failures |

New nullability and declaration failures must receive precise diagnostics. Preserve existing codes where the failure meaning is unchanged; introduce new stable codes, rather than reusing unrelated codes, when necessary. Do not claim an exact new code until it has been assigned in the compiler and test suite.

Exception handling assigns these codes (§7.4):

| Code | Meaning |
| --- | --- |
| `SOL-T016` | A `catch` clause whose written type is not `Exception` |
| `SOL-T017` | A `return` inside a `finally` block |
| `SOL-R012` | A negative exception code was constructed |
| `SOL-R013` | An exception reached the top of the program uncaught |

CLI statuses stay `0` on success, `1` for compilation failure or runtime abort, and `2` for invalid CLI usage.

### 13.1 Breaking changes from the prior language

| Former source | Revised source or behavior |
| --- | --- |
| `var x : 1` | `x := 1` |
| `mut var x : 1` | `mut x := 1` |
| `pub var x : 1` | `pub x := 1` |
| `static mut var n : 0` | `static mut n := 0` |
| `var x : String("a")` | `x := String("a")` |
| `var slot` | Invalid: an initializer is required |
| `var slot : null` | Replace with `mut slot: T? = Null` or `mut slot? := Null` (`Any?`) |
| `null` literal | `Null` singleton value |
| Implicitly nullable `String` | Non-null `String`; write `String?` explicitly |
| `Integer[2] {}` | Invalid because elements are non-null |
| `Integer?[2] {}` | Valid: both slots are `Null` |
| `Map.get(...) -> V` when Java can return null | `Map.get(...) -> V?` |
| Immutable `value` field with no initializer | Explicitly initialized field, possibly `value: T? = Null` |
| `a.size()` on an array | `a.size`: the length is a read-only member, not a function |
| `print(...)` / `println(...)` | Removed entirely. Write `TextWriter(Stdio.stdout()).write(...)` / `.writeLine(...)`; a multi-value line is one concatenation, e.g. `writeLine(a .. b)` |
| a fixed `"\n"` assumed as the output terminator | `TextWriter.writeLine` appends the platform terminator `TextWriter.LINE_ENDING`; write a fixed terminator explicitly through `write` |
| `try`, `catch`, `finally`, `raise` used as identifiers | Reserved keywords (§7.4) |
| `type Exception` (or any declaration named `Exception`) | Reserved: the built-in exception type (§7.4) |

The previously supported late type fixation of an uninitialized slot is **removed**. The type of every binding is known at its declaration. `foo? := Null` is nullable `Any?`, not an invitation to infer `foo`'s type later.

### 13.2 Source spelling and naming

- `Null` is capitalized in Solvik source even though `null` remains the Java lowering representation and the conventional output text.
- `?` is a **type suffix** in type positions and an **inferred-binding marker** only in `name? := expression` declarations; it is not part of identifiers and is not a postfix expression operator.
- `size` is the only member of every array type and is written without parentheses. `String` and the
  packaged collections keep the function form, so `text.size()` is a call while `values.size` is a
  member read.
- For explicit types, use `name: T? = expression`, not `name?: T = expression`.
- `:=` always declares; `=` never declares.
- All declarations that create stored values require initialization.
- No implicit `Null` default is supplied to a variable or field declaration.

## 14. Scope and future extensions

This specification keeps the existing exclusion of concrete class inheritance, stateful/inherited/generic traits, closures, function values, futures, resizable language arrays, static type declarations, static top-level or module functions, and unsupported Java binding shapes unless later documented changes explicitly add them.

Exception handling is part of the language (§7.4): one unchecked `Exception` with an `Integer` code and a `String` message, `raise`, and `try`/`catch`/`finally`. User-defined exception types, an `Any` payload on `Exception`, a second catchable type, catchable `SOL-Rxxx` aborts, catchable Java exceptions from native calls, and `try` as an expression are **not** introduced. General union types, safe-navigation operators (`?.`), implicit default values, automatic JAR-binding generation, and non-null constructor-required-field syntax are **not** silently introduced by this revision.

Filesystem access is limited to the `File` operations in §11.6. Directories, recursive deletion, rename/copy, metadata, permissions, timestamps, file size, temporary-file helpers, path joining/normalization, seeking, truncation at an offset, `fsync`/locking, memory mapping, sockets, pipes, processes, console control, buffered byte writers, nonblocking I/O, timeouts, and cancellation remain future work.

## 15. Acceptance tests for the revision

The compiler implementation of this specification should include positive and negative tests for every rule below:

```solvik
// VALID declaration forms
x := 10
mut y := 10
y = 11
name: String = "Solvik"
mut title: String? = Null
title = "ready"
maybeNumber? := 1
maybeEmpty? := Null                 // Any?
nothing := Null                     // Null

// VALID null-safe operations
TextWriter(Stdio.stdout()).writeLine("" .. Null)
TextWriter(Stdio.stdout()).writeLine("" .. (Null == Null))
TextWriter(Stdio.stdout()).writeLine(String(Null))

// VALID standard I/O
out := TextWriter(Stdio.stdout())
out.writeLine("hello")
out.close()
memory := Channel.bytes()
memory.writeAll(Byte[2] {65, 66})
reader := TextReader(memory)
first := reader.readLine()                       // "AB"
reader.close()
term := TextWriter.LINE_ENDING                  // immutable platform terminator

// VALID static initialization
type StaticProbe {
    static mut value := 0
    static {
        value = 7
    }
    static pub func valueNow() -> Integer { return value }
}
seen := StaticProbe.valueNow()                  // block runs once, before the call

// VALID file and byte I/O
file := File.openWrite("example.bin")           // creates or truncates
bytes := ByteWriter(file.channel())
bytes.write(Byte[2] {1, 2})
bytes.close()
file.close()
readBack := File.openRead("example.bin")
chunk := ByteReader(readBack.channel()).read(2)
readBack.close()
deleted := File.delete("example.bin")

// VALID arrays
values := Integer[2] {1, 2}
optionalValues := Integer?[2] {}
optionalArray: Integer[2]? = Null

// VALID exception handling
try {
    raise Exception(4001, "denied")
} catch (error: Exception) {
    TextWriter(Stdio.stdout()).writeLine((error.getCode()) .. (" ") .. (error.getMessage()))
} finally {
    TextWriter(Stdio.stdout()).writeLine(("done"))
}
try {
    TextWriter(Stdio.stdout()).writeLine(("cleanup only"))
} finally {
    TextWriter(Stdio.stdout()).writeLine(("done"))
}
```

```solvik
// INVALID cases — each line is an independent negative fixture
// var x : 1                       // removed var syntax
// x                              // missing initializer
// x: Integer                     // missing initializer
// x = 1                          // undeclared assignment
// x := 1; x := 2                 // same-scope redeclaration
// mut x := 1; x = Null           // Integer is non-null
// x: String = Null               // Null cannot inhabit String
// x: Integer? = 1; x = 2         // immutable binding cannot be reassigned
// x?: String = Null              // duplicate nullable declaration styles
// values := Integer[2] {}        // incomplete non-null array
// values := Integer[2] {1, Null} // non-null element cannot be Null
// Null()                         // Null is not a constructor
// missing := null                // removed lowercase literal
// try { TextWriter(Stdio.stdout()).writeLine("1") } // a try needs a catch or a finally
// raise Null                     // Null is not an exception
// raise 1                        // only an Exception can be raised
// catch (e: Integer) { }         // Exception is the only catchable type (§7.4)
// func f() -> Integer { try { return 1 } finally { return 2 } } // SOL-T017
// native func nope() -> Integer  // SOL-T001: only a packaged type may declare native
// w := TextWriter(Stdio.stdout()); w.LINE_ENDING        // SOL-T001: static member via instance
// TextWriter.LINE_ENDING = "x"                          // SOL-T010: immutable static field
// forged := File(Channel.bytes(), "x")                  // SOL-T001: the File constructor is private
// File.openReadChannel("x")                             // SOL-T001: a type-private binding
// type Bad { static { return } }                         // SOL-T005: a static block has no return
```

End-to-end validation must compile and run generated Java with Java 25 and no Solvik runtime on the generated-program classpath. Tests must cover native methods returning unexpected Java null, nullable array elements, overload collisions caused by nullable erasure, read/write safety, constructor-initialized immutable fields, and the existing regression corpus. The revised `example.sol`, imports, standard-library `.sol` resources, `JAVA_BACKEND.md`, CLI documentation, golden source tests, and compiler diagnostics must all be updated together when implementing the language change.
