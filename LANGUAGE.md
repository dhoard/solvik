# Solvik Language Specification

This document is the normative description of the Solvik language as
implemented by the Rust compiler and bytecode VM.

Solvik is a statically typed, class-based language. All behavior lives in
class and interface methods: there are no free functions, no closures, and no
function values. Programs are compiled to bytecode for a stack-based virtual
machine with a managed heap.

## 1. Program structure

A program is a single source file (the entry file) with this shape:

```solvik
package org.example.app

class Main {

    public static run(args: String...): Long {
        // ...
        return 0
    }
}
```

- `package org.example.app` declares the required Java-style, lowercase dotted
  package name. Type names remain local to the source file unless a future
  multi-file loader resolves a qualified name.
- Dependencies use `use file:<name> [as <alias>]` or `use url:<name> [as <alias>]`
  before declarations, where `<name>` is a dotted identifier such as
  `vendor.stringkit` (the value is stored verbatim as metadata). The `use`
  statement is parsed and preserved as package metadata; the current compiler
  remains single-file, so external loading is not yet performed.
- The entry point is `Main.run`, a public static method taking a variadic
  `String` argument list and returning `Long` (the process exit code).
- Top-level declarations are classes, interfaces, and enums.

### Naming conventions

- Class, interface, and enum names must start with an uppercase ASCII letter.
- Method names must start with a lowercase ASCII letter.
- Fields and enum variants are members; their names must start with a lowercase
  ASCII letter.
- Parameters, local variables, loop variables, catch variables, and pattern
  bindings are variables; their names must start with a lowercase ASCII letter.
- Type parameters are conventionally uppercase (`T`, `A`, `B`) and are exempt
  from these declaration-name rules.
- Reserved words (`let`, `mutable`, `class`, `interface`, `implements`,
  `extends`, `delegate`, `to`, `static`, `if`, `while`, `for`, `switch`,
  `try`, `catch`, `match`, and the other keywords) are reserved at the lexer
  level: an identifier matching a keyword token can never be used as a name.
  `let` is reserved as part of block scoping. The removed
  object-model words `super`, `override`, `protected`, and `private` are no
  longer keywords and parse as ordinary identifiers.

### Comments

- Line comments: `// ...`
- Block comments: `/* ... */`, nestable.

### Statements

Statements are separated by newlines or `;`. Newlines are the canonical style;
semicolons remain accepted for compatibility. A newline does not terminate a
statement when the current line ends inside an unbalanced `(` or `[`.

Newlines (and comments) are ignored between a construct's header and its
opening brace: class, interface, and enum bodies; method bodies; and the
blocks of `if`, `else`, `while`, `for`, `switch` (including case bodies),
`try`, `catch`, `finally`, and `match`. Both placements below are equivalent:

```solvik
public run(): Long { return 0 }

public run(): Long
{ return 0 }
```

This does not affect statement termination: a newline still terminates a
complete statement, and a `{` on the line after a statement begins a map
literal, not a block.

### Formatting

Formatting is not enforced by the parser but is the canonical output of
`solvik --format`:

- Indentation is four spaces per level.
- Non-empty class, interface, and enum bodies include a blank line after the
  opening declaration line.
- Brace placement (same line as the header vs. next line) is accepted in both
  forms and preserved as written; the formatter does not normalize it.

## 2. Types

### Primitive types

| Type         | Description                          |
| ------------ | ------------------------------------ |
| `Boolean`    | `true` / `false`                     |
| `Byte`       | 8-bit signed integer (-128..=127)    |
| `Short`      | 16-bit signed integer                |
| `Integer`    | 32-bit signed integer                |
| `Long`       | 64-bit signed integer                |
| `Float`      | IEEE-754 single                      |
| `Double`     | IEEE-754 double                      |
| `BigInteger` | Arbitrary-precision signed integer   |
| `BigDecimal` | Arbitrary-precision decimal          |
| `Char`       | Unicode scalar value                 |
| `String`     | Immutable UTF-8 text                 |

### Reference types

- `Object` — the top type; every value is an `Object`.
- `List<T>` — ordered, growable collection.
- `Map<K, V>` — hash table; keys compared by content equality; retrieval
  order is unspecified. Keys must be immutable values (primitives, strings,
  enums); mutable values are rejected at runtime.
- `Stack<T>` — LIFO collection with deque-style accessors.
- `Set<T>` — unordered collection of unique elements; membership compared by
  content equality; iteration order is unspecified. Members must be
  immutable values; mutable values are rejected at runtime.
- User-defined classes, interfaces, and enums.

### Nullability

Every reference type has a nullable variant written `T?`:

```solvik
n: Long? = null
m: String? = "x"
```

- A non-nullable reference (`T`) may not hold `null`.
- A nullable reference (`T?`) may hold `null` or a value of `T`.
- `T` is a subtype of `T?`.
- The `??` (coalesce) operator recovers a non-nullable value:
  `(n ?? 10)` has type `Long`.
- After a `== null` / `!= null` test, the compiler narrows the type within
  the branch.

### Conversions

Numeric primitives form a widening lattice (Java-style):

```
Byte < Short < Integer < Long < Float < Double
```

A value of a narrower numeric type is implicitly widened when it flows into
a wider numeric target: variable declarations and assignments, field writes,
call arguments, return positions, collection literals, and match arms.
Widening never changes a value except for the deliberate precision loss of
`Long -> Float` / `Long -> Double`. `BigInteger` and `BigDecimal` do not
widen implicitly; they only convert through their `from` factories.

Narrowing is explicit, through the static `from` constructor of the target
type (range/validity checked at runtime):

```solvik
a: Long    = Long.from("42")
b: Double  = Double.from(3)
c: String = String.from(99)
d: Boolean = Boolean.from(0)
e: Byte   = Byte.from(7)
f: Char   = Char.from('x')
g: Short  = Short.from(300)
h: Integer = Integer.from(100000)
i: Float  = Float.from(1.5)
j: BigInteger = BigInteger.from("123456789012345678901234567890")
k: BigDecimal = BigDecimal.from("1.5")
```

One Java exception applies: an *integer literal* that fits the target may be
assigned to a narrower integral type without a call (`let b: Byte = 127`).
Out-of-range explicit conversions are runtime errors.

Arithmetic and comparison promote mixed numeric operands to the wider type
(`Integer + Long -> Long`, `Long + Double -> Double`, ...). Integral
arithmetic is checked: overflow is a runtime error.

### Introspection

```solvik
Type.of(value)          -> String   // runtime type name
Type.isType(value, name) -> Boolean // dynamic type test
```

## 3. Literals

- Integers: decimal, `0x` hex, `0o` octal, `0b` binary; `_` digit separators.
  An unsuffixed integer literal is `Integer` when it fits 32 bits, `Long`
  when it fits 64 bits, and `BigInteger` beyond that.
- Floats: decimal with optional fraction/exponent. Unsuffixed (or `d`/`D`)
  literals are `Double`; the `f`/`F` suffix selects `Float`; the `bd`/`BD`
  suffix selects `BigDecimal` with exact decimal text (`1.5bd` is exactly
  1.5, unlike the nearest binary float).
- Strings: `"..."` with escapes (`\n \t \r \0 \\ \" \'`, two-digit `\xHH`,
  four-digit `\uHHHH`, eight-digit `\UHHHHHHHH`, and `\u{...}` with
  1–6 hex digits). Unicode escapes must encode a Unicode scalar value; raw strings
  `r"..."` disable escaping.
- Chars: `'a'`, `'\n'`.
- Booleans: `true`, `false`.
- Null: `null`.
- Lists: `[1, 2, 3]` (trailing comma allowed).
- Maps: `{ "k": 1, "j": 2 }`.

## 4. Variables and fields

### Local variables

```solvik
let x: Long = 5            // immutable local
let mutable y: Long = 10   // mutable local
let z: Long                // declared, assigned before use
```

The type annotation is required. An immutable variable cannot be reassigned.

`let` is mandatory on every local declaration. A declaration without `let`
is a parse error.

#### Definite assignment

Every local must be definitely assigned on every path that reads it
(Java-style). The compiler tracks assignments through branches, loops, and
`try`/`catch`/`finally`; reading a variable that may be unassigned is a
compile error (`C239`). An immutable local declared without an initializer
may be assigned exactly once per execution path; a second assignment on the
same path is `C134`. Code after an unconditional `return`, `throw`,
`break`, or `continue` in the same block is unreachable and rejected
(`C245`).

### Scoping and name rules

Names are scoped like Java:

- A method body, each `if`/`else` branch, each loop body, each `switch`
  case body, each `try`/`catch`/`finally` body, and each `match` arm is an
  independent scope.
- A binding is visible from its declaration until the end of the block in
  which it is declared. When a block exits, its bindings are hidden again.
- Redeclaring a name that is still visible in an enclosing or current scope
  is a compile error (`C240`); shadowing does not exist.
- `for-in` loop variables, catch parameters, and match pattern bindings
  participate in the same lookup, so reusing a visible name there is also
  `C240`.
- Fields are class members, not locals, and never take `let`.

#### Explicit scope blocks

A standalone `{ ... }` block creates a fresh name scope:

```solvik
{
    let x: Long = 5
    // x is visible here only
}
// x is no longer visible
```

- Bindings declared inside a scope block must use names that are not
  visible in any enclosing scope (`C240`). When the block exits, its
  bindings are hidden again.
- This is identical to scoping in `if`/`else` branches, loop bodies,
  `switch` case bodies, and `try`/`catch`/`finally` bodies.
- `break` and `continue` resolve through scope block boundaries to the
  nearest enclosing loop. If no enclosing loop exists, a compile error is
  emitted.
- `return` is **not** allowed inside a scope block (error `C141`). Scope
  blocks are not function bodies.
- Scope blocks are statement-only; they cannot be used as expressions.

### Class fields

Every user-defined class field is **private to the class that declares it**.
There are no public, protected, package-visible, or inherited fields, and
field declarations take no visibility modifier. The field modifiers are
`mutable` (instance and static fields) and `static` (class-level fields):

```solvik
class Account {

    id: String
    mutable enabled: Boolean
    mutable loginCount: Long
    mutable lastAudit: String?
}
```

- `mutable` is a field modifier and must appear on each mutable field.
- Immutable fields are initialized at construction and cannot be assigned
  afterwards. Mutable fields can be assigned from methods of the declaring
  class.
- Field access is explicit: `self.name`. A bare field name is not an
  implicit alias for `self.field`.
- Only a method of the declaring class may read or write a field. External
  code must go through a method:

```solvik
let account: Account = Account.new(...)
let id: String = account.id()      // valid: method
let bad: String = account.id      // compile error: field is private
```

### Static fields

A `static` field is class-level state: one slot per declaring class, shared
by every instance and every thread, alive for the lifetime of the program.

```solvik
class Counter {

    static count: Long = 0
    static mutable total: Long = 0
    static mutable cache: Map<String, Long> = {}
    static label: String = "counter"
}
```

- Declaration syntax is `static [mutable] name: Type = expr`. The
  initializer is **required**: a non-null declared type must never hold
  `null`, so a static field cannot be left uninitialized.
- `static` is written before an optional `mutable`; `mutable` keeps its
  usual meaning (an immutable static field may not be assigned after
  initialization).
- Fields stay private: `public`/`protected` remain invalid on fields, and
  only methods (instance or static) of the declaring class may read or
  write the field.
- Access is explicitly type-qualified; there is no bare-name alias
  (the single exception is the static block, where the declaring class's
  static members resolve by bare name; see below):
  `Counter.total` and `Self.total` are equivalent inside `Counter`. Reads,
  plain assignment, and compound assignment (`+= -= *= /= %=`) are all
  supported. `obj.total` never resolves a static field; it is a compile
  error naming the field as static.
- Static fields are not instance slots. `Self { ... }` object literals
  initialize instance fields only: a static field name inside `Self { ... }`
  is an error, and a missing static field is not reported as an
  uninitialized construction field.
- A class may not declare two fields with the same name, and a static field
  may not share a name with an instance field.
- A static field's declared type may not mention the class's own type
  parameters: generic statics would be erased (one erased class per generic
  class) and admit type confusion. Static *methods* are unaffected.
- Initializers run exactly once, before `Main.run`, in class declaration
  order and, within a class, field declaration order. An initializer is any
  expression valid in a static context (no `self`, no instance fields, no
  locals), with one restriction: it may not read any static field, directly
  or through `Self.field`. This removes initialization-order hazards
  entirely. Calls are permitted inside initializers, including construction
  and collection/map literals; a method invoked during static initialization
  must not depend on static state that has not been initialized yet. A
  failing initializer propagates as a normal runtime error and aborts
  startup.
- `delegate I to field` targets instance fields only; delegating to a
  static field is a compile error.

### Static blocks

A class may declare **at most one** static block: a `static { ... }` member
that runs once at startup, like Java's static initializer block.

```solvik
class Counter {

    static mutable total: Long = 0
    static limit: Long = 10

    static {
        // Runs exactly once, before Main.run, after every static field
        // initializer of this class has completed.
        let mutable i: Long = 0
        while i < limit {
            total += 1
            i += 1
        }
    }
}
```

- The block may appear in any position among fields, methods, and
  `delegate` clauses. A second `static { ... }` in the same class is a
  compile error; only classes have static blocks (not interfaces or enums).
- The block runs exactly once, after **all** of the class's static field
  initializers, before `Main.run`, in the same class declaration order used
  by static field initialization. A class with a static block but no static
  fields still runs its block at startup.
- The body is an ordinary statement block checked in a static context: no
  `self`, no instance fields, no parameters. Local variables, control flow,
  and method calls are allowed.
- Unlike static *field initializers*, the block may read and write (mutable)
  static fields of the declaring class, because every initializer has
  already run when the block executes. Writing an immutable static field is
  still an error.
- Inside the block, static members of the declaring class resolve by **bare
  name**: `total`, `limit`, and `bump(...)` need no `Counter.` or `Self.`
  qualifier. This is the one place where bare names alias static members;
  everywhere else (including static methods) type-qualified access remains
  mandatory. Local variables take precedence over static field names in
  bare-name lookup inside the block.
- The block has no return value. A bare `return` exits the block early,
  skipping its remaining statements; `return expr` is a compile error.
- A runtime error thrown inside the block propagates as a normal runtime
  error and aborts startup, exactly like a failing static field
  initializer.

## 5. Classes

A class is a nominal reference type with private state and methods. It may
declare fields, instance methods, static methods, at most one static block,
`implements` clauses, and `delegate` clauses. A class may **not** extend
another class.

```solvik
class Person implements Named {

    nameValue: String

    public static new(name: String): Self {
        return Self {
            nameValue: name,
        }
    }

    public name(): String {
        return self.nameValue
    }
}
```

- Construction uses the static factory convention: a `public static new`
  method returning `Self`, and the object literal `Self { field: value, ... }`.
  Fields are always named, commas are required between entries, and a
  trailing comma is allowed. Every instance field must be initialized exactly
  once; omitting one is a compile error. Static fields are not instance
  slots and never appear in `Self { ... }`.
- `Self` refers to the current class type, in declarations and construction.
  Because there is no class inheritance, `Self` is never a “most derived”
  type: it is exactly the declaring class.
- `Type.new(...)` resolves only to a static method declared directly on
  `Type` (or to a built-in constructor of a built-in type). Static methods
  are not inherited.
- There is no `extends`, no parent class, no inherited fields or methods, no
  inherited constructors, and no `super`.
- Methods are private by default. `public` exports a method to the class's
  external API. There is no `protected`, no `override`, and no explicit
  `private` keyword: omitting visibility already means private.

```solvik
class User {

    secret(): String {        // private
        return "internal"
    }

    public name(): String {   // public
        return "Alice"
    }
}
```

Composition replaces implementation inheritance. A class may hold another
object in a private field and forward an interface to it with `delegate`
(section 6). Composition never creates a subtype relationship: if `Employee`
holds a `Person`, `Employee` is not a `Person`.

## 6. Interfaces and delegation

An interface is a nominal behavioral contract with abstract requirements and
optional default implementations.

```solvik
interface Greetable {

    greeting(): String                    // abstract requirement

    farewell(): String {                  // default implementation
        return "bye from " .. greeting()
    }
}

class Bot implements Greetable {

    public greeting(): String {
        return "bot"
    }
}
```

- A class declares conformance with `implements` and must satisfy every
  required method of each direct and transitive interface through exactly one
  effective implementation.
- Interface methods are public contract members; visibility modifiers are
  not accepted on them.
- Interfaces may extend other interfaces (`interface A extends B`); this is
  contract refinement, not implementation inheritance.
- Default methods provide shared implementations. When a default calls a
  sibling interface method, the call dispatches through the receiver, so a
  class's own implementation is used.
- Calls through an interface-typed receiver dispatch at runtime to the
  class's effective implementation (explicit, delegated, or default). Calls
  through a concrete class type reach the same implementation.

### Explicit interface delegation

A class may forward an interface it implements to a private composed field:

```solvik
interface Named {
    name(): String
}

class Person implements Named {

    nameValue: String

    public static new(name: String): Self {
        return Self { nameValue: name, }
    }

    public name(): String {
        return self.nameValue
    }
}

class Employee implements Named {

    person: Person

    delegate Named to person

    public static new(name: String): Self {
        return Self { person: Person.new(name), }
    }
}
```

Then `employee.name()` behaves as if `Employee` declared an ordinary
forwarding method, while `employee.person` remains a compile error:
delegation exposes behavior, never state.

Rules:

- `delegate I to field` requires `I` to be an interface in the class's
  effective `implements` closure. Delegation never changes a class's public
  nominal type.
- The target must be a direct, non-nullable instance field whose declared
  static type conforms to `I` (including generic substitutions).
- Only the named interface's contract is exposed; unrelated methods of the
  target object are not promoted.
- A class may declare multiple delegates, including one field delegating to
  several interfaces. The same interface may not be delegated twice.
- If two delegates would supply different implementations for the same
  method, the class must declare that method explicitly.

### Effective method precedence

For each interface method requirement, the implementation is selected in
this order:

1. an explicit method declared on the class;
2. an explicit delegation;
3. the most-specific unambiguous interface default.

If none applies, compilation fails. Source order is never a tie-breaker.
Delegation is lowered at compile time to an ordinary forwarding method, so
no runtime delegation object or opcode exists.

## 7. Enums

```solvik
enum Color {

    red
    green
    blue(Long)      // payload variant
}

enum Verdict<T> {  // generic enum

    pass(T)
    fail(String)
}
```

- Variants are constructed qualified: `Color.red`, `Color.blue(255)`.
- Variants may carry a single payload of any type.
- Enums are matched with `match` (section 10).
- Enum values compare by variant identity (and payload equality).

## 8. Generics

Classes, interfaces, enums, and methods may declare type parameters:

```solvik
class Box<T> {

    value: T
    public static new(value: T): Self {
        return Self {
            value: value,
        }
    }

    public get(): T {
        return self.value
    }
}

p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
```

- Type arguments are written at use sites: `Box<Long>`,
  `Pair<Long, String>`. Generic built-in constructors may be written with
  explicit arguments (`List<Object>.new()`) or bare (`List.new()`); in an
  annotated declaration the element type is inferred from the declared type
  (`let l: List<String> = List.new()`), and a bare constructor without a
  declared type yields `List<Object>`.
- Generics use **type erasure**: each method compiles exactly once; inside
  the body, type parameters behave as `Object`. Type safety is enforced
  statically at call sites.
- Type parameters may have interface constraints:
  `class Max<T: Comparable>` (constraints checked at instantiation).
- Type arguments must be non-nullable: `List<String?>` and
  `Map<String, Long?>` are compile errors (`C103`). Nullability lives on
  the reference itself (e.g. `List<String>?`), not on its type arguments.
- Type arguments are **invariant** (Java-style): `List<Integer>` is not
  assignable to `List<Object>`, and `Map<String, Long>` is not assignable
  to `Map<String, Integer>`. Exact type-argument substitution is required
  for classes, interfaces, and enums alike.

## 9. Expressions and operators

Precedence (high to low):

1. Postfix: `.member`, `.method(...)`. A dot after an
   uppercase type name (or after `Self`) is a static member, method, or
   static-field access; a dot after a value is an instance member access.
2. Unary: `-` `!`
3. `*` `/` `%`
4. `+` `-`
5. `..` (string concatenation)
6. `<` `<=` `>` `>=`
7. `==` `!=`
8. `&&`
9. `||`
10. `??` (coalesce)

Notes:

- `+` is arithmetic only; string concatenation is `..`.
- When either operand of `..` has static type `String` (including `String?`),
  both values are automatically formatted using the same built-in conversion
  as `print`/`println`. For example, `"caught " .. e` and `42 .. " items"`
  produce strings; null formats as `"null"`. This conversion does not call
  user-defined `toString` methods; call those explicitly for custom formatting.
- Operands are evaluated once, left to right. Two integer operands retain
  range semantics; other pairs without a String operand are rejected.
  `Void` and range expressions cannot be concatenation operands.
- Comparisons produce `Boolean`; `==`/`!=` on references compares content
  (strings by text, collections element-wise, objects by identity unless
  both sides are the same enum/string/primitive wrapper).
- `&&` and `||` short-circuit.
- `??` evaluates the right side only when the left is `null`.
- Assignment `=` and compound updates `+= -= *= /= %= ..=` are statements.
  Their targets are locals, `self.field` instance fields, and type-qualified
  static fields (`Counter.total = 1`, `Self.total += 1`).
- Type-qualified static access (`Type.name`, `Self.name`) resolves to a
  static method, an enum variant, or a static field of that class. An object
  receiver never resolves a static field: `obj.staticField` is a compile
  error naming the field as static.
- Member access on a nullable reference is allowed: the compiler inserts a
  runtime null check that raises a `null reference` exception when the
  receiver is `null`. Prior narrowing omits the check.
- Calls may use positional or named arguments. Positional arguments must come
  first; named arguments may follow in any parameter order, and each parameter
  may be named at most once.

### Built-in methods

Every reference value supports the universal object contract:

```solvik
value.toString(): String
value.equals(other: Object?): Boolean
value.hashCode(): Long
```

- `toString` uses the default formatting unless a class defines its own.
- `equals` defaults to identity equality for ordinary objects; strings use
  content equality, enums use variant plus payload, and collections use
  structural equality (lists/stacks ordered, sets order-independent, maps
  entry-wise). `x.equals(null)` is always `false`.
- `hashCode` is identity-based for ordinary objects and content-based for
  strings, enums, and collections; equal values always hash equal.
- Classes may define their own `equals`/`hashCode` to override the defaults;
  user definitions take precedence over the built-ins.

Strings: `length contains startsWith endsWith indexOf substring charAt
replace split trim toUpperCase toLowerCase`.

Lists: `new withCapacity add addAt get set size contains indexOf remove
removeValue reverse reversed sort clear join isEmpty addAll`.

Maps: `new withCapacity put putIfAbsent replace remove removeMapping get
getOrDefault containsKey containsValue keys values size clear isEmpty
putAll`.

Stacks: `new withCapacity push pop peek poll addFirst addLast removeFirst
removeLast peekFirst peekLast size isEmpty`.

Sets: `new withCapacity add remove contains size clear isEmpty addAll
containsAll toList`.

Collection signatures are generic over the receiver's type arguments:
elements, parameters, and results carry the declared element/key/value
types, not `Object`. Sizes and indices are `Integer`; capacity arguments
are `Integer`.

Return shapes follow the Java collections convention:

- `List.add` / `Set.add` / `Set.addAll` report acceptance as `Boolean`;
  `List.set`, `List.remove(index)`, and `Stack.pop` return the displaced or
  removed element; `Stack.peek` / `poll` / `peekFirst` / `peekLast` return
  the element or `null` when the stack is empty (`pop` on an empty stack is
  a runtime error).
- `Map.get` returns the value or `null` when the key is absent; `put`,
  `putIfAbsent`, `replace`, and `remove` return the previous value or
  `null` when there was none. These results are statically nullable, so
  they must be coalesced (`??`) or null-checked before use as non-null
  values. `getOrDefault` returns the non-nullable value or default.
- `removeValue` / `removeMapping` / `contains*` return `Boolean`.

Mutable values — user-defined class instances and collections — cannot be
used as `Map` keys or `Set` members; attempting to do so is a runtime error
(their state can change after insertion, invalidating the hash index).

Every collection is individually thread-safe: each operation is linearizable
under that collection's own lock, and unrelated collections progress
concurrently.

## 10. Control flow

```solvik
if cond {
    ...
} else if cond2 {
    ...
} else {
    ...
}

while cond {
    ...
}

for x in 1..10 {                 // range loop (start inclusive, end exclusive)
    ...
}
for item in someList {           // collection loop
    ...
}
for member in someSet {          // set loop (unspecified order)
    ...
}

break
continue

match subject {
    Pattern1 => expr1
    Pattern2 => expr2
    _ => exprDefault
}
```

- `match` arms are expressions; the arm bodies must all have compatible
  types. Patterns: literal ints, qualified enum variants (with optional
  payload bindings), `_` wildcard, and nested list/variant patterns.
- `break`/`continue` apply to the innermost loop.
- The iterator expression may be an integer range (`a..b`), a `List`, a
  `Stack`, a `Set`, a `String` (yielding `Char`), or a `Map` (yielding
  keys). Range loops and collection loops desugar to index loops; `Map` and
  `Set` iteration first materializes keys/members as a list.
- Iteration order is defined for ranges, lists, stacks, and strings. Map key
  order and Set member order are unspecified (hash layout); programs must
  not depend on them. Every Set member is visited exactly once.
- A nullable iterator holding `null` raises a `null reference` runtime error
  when the loop begins.

Control-flow conditions are written without redundant parentheses. Parentheses
remain available for grouping expressions. Match arms and enum variants are
newline-separated; commas are not accepted between them. Commas are reserved
for explicitly delimited lists such as parameters, arguments, generic type
arguments, list/map literals, and `Self { ... }` initializers.

## 11. Exceptions

```solvik
try {
    risky()
} catch (e: Exception) {
    handle(e)
} catch (e: MyError) {
    handleSpecific(e)
} finally {
    cleanup()
}
```

- `throw value` raises a value whose type is `Exception`, a class, or an
  interface (anything conforming to the built-in `Throwable` interface).
  Throwing a `String` or any other value is a compile error (`C242`).
- `Exception.new(message)` creates the built-in exception object carrying a
  `String` message.
- Catch clauses are typed and repeatable: `catch (e: Type)`. Clauses are
  tested in order against the thrown value; the first conforming clause
  handles it. A throw that matches no clause keeps unwinding.
- The catch parameter is scoped to its handler body; reusing a visible name
  for it is `C240`.
- `finally` runs on both normal and exceptional completion.
- An exception passing through a `finally` without a matching `catch`
  continues unwinding to the next enclosing handler.
- Uncaught exceptions terminate the program with exit code 2.

## 11. Object lifetime

Solvik objects have reference semantics. Multiple variables may refer to the
same object, and assignment does not make an implicit deep copy. Memory for
objects that are no longer reachable is reclaimed automatically by the
runtime; programs do not explicitly free managed objects and do not need to
mark one side of a cyclic object graph as weak. Reclamation timing is not a
source-language guarantee.

Object lifetime is separate from external-resource cleanup. Programs that use
files, sockets, locks, or other operating-system resources must use the
corresponding API's explicit cleanup or scope rules; memory reclamation is not
a substitute for that cleanup. Shared references also do not synchronize
mutable state: use `Mutex`, `Semaphore`, or the relevant synchronization API
when multiple threads mutate shared objects.

## 12. Concurrency

```solvik
interface Runnable {
    run(): Void
}

t: Thread = Thread.new(myRunnable)
t.start()
t.join()
```

- Threads share one managed heap. Synchronize with `Mutex`
  (`Mutex.new()`, `lock()`, `unlock()`) or `Semaphore`
  (`Semaphore.new(n)`, `acquire()`, `release()`).
- Collections are individually thread-safe: each `List`, `Map`, `Stack`,
  and `Set` serializes its own operations under a per-collection lock, so
  unrelated collections on different threads progress concurrently without
  extra synchronization.
- `Thread.join()` blocks until the worker finishes.
- Blocking natives (I/O, sleep, join) release the heap lock. Ordinary object
  lifetime is managed automatically; atomic reference counting handles the
  common case and the runtime schedules bounded cycle collection when it can
  coordinate the shared heap. Collection timing is not a language guarantee.

## 13. Standard library

Static namespaces and type-associated methods use the same dot syntax as
instance members. Uppercase type names make the two forms unambiguous:

- `Math` — `abs min max pow sqrt floor ceil round` (Double math).
- `Base64` — `encode decode`.
- `Hash` — `md5 sha1 sha256` (hex digests).
- `Json` — `stringify parse` (maps/lists/scalars).
- `Time` — `now` (epoch ms), `sleep(ms)`.
- `Random` — `seed nextLong nextDouble`.
- `File` — `read write exists delete listDir`.
- `Test` — `assert assertEqual` (test helpers).
- `System` — singleton with no constructor; static factories `out()` → `Writer`,
  `err()` → `Writer`, `in()` → `Reader`.

Streams: `System.out().println(x)`, `System.out().print(x)` — every value has a
universal `toString()`. Read with `System.in().readln()` / `System.in().readAll()`.

Processes: `Process.new(command, argumentList)`, `start()`, `wait()`, `exitCode()`,
plus per-process `stdin()`/`stdout()`/`stderr()` stream handles.

Regex: `Regex.new(pattern)` with `matches find all replace`.

## 14. Diagnostics and exit codes

Diagnostics carry codes by family: `L###` lexer, `P###` parser, `C###`
semantic/compiler, `V###` verifier, `E###` runtime, `W###` warnings.

Exit codes:

| Code | Meaning                     |
| ---- | --------------------------- |
| 0    | success                     |
| 1    | compilation error           |
| 2    | runtime error / uncaught exception |
| 3    | internal error              |

Warnings never change the exit code. A compilation that produces only
warnings succeeds (exit 0); warnings do not turn a successful compile into a
failure.

Redeclaring a visible name is an error (`C240`), not a warning: Solvik uses
Java-style local name rules and has no shadowing.
