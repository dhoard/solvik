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
package <name>

class Main {
    pub static run(args: String...): Int {
        // ...
        return 0
    }
}
```

- `package <name>` declares the package name (required).
- The entry point is `Main.run`, a public static method taking a variadic
  `String` argument list and returning `Int` (the process exit code).
- Top-level declarations are classes, interfaces, and enums.

### Comments

- Line comments: `// ...`
- Block comments: `/* ... */`, nestable.

### Statements

Statements are separated by newlines or `;`. A newline does not terminate a
statement when the current line ends inside an unbalanced `(` or `[`.

## 2. Types

### Primitive types

| Type    | Description                          |
| ------- | ------------------------------------ |
| `Int`   | 64-bit signed integer                |
| `Float` | IEEE-754 double                      |
| `Byte`  | 8-bit signed integer (-128..=127)    |
| `Bool`  | `true` / `false`                     |
| `Char`  | Unicode scalar value                 |
| `String`| Immutable UTF-8 text                 |

### Reference types

- `Object` — the top type; every value is an `Object`.
- `List<T>` — ordered, growable collection.
- `Map<K, V>` — hash table; keys compared by content equality.
- `Stack<T>` — LIFO collection.
- `Set<T>` — unordered collection of unique elements; membership compared by content equality.
- User-defined classes, interfaces, and enums.

### Nullability

Every reference type has a nullable variant written `T?`:

```solvik
n: Int? = null
m: String? = "x"
```

- A non-nullable reference (`T`) may not hold `null`.
- A nullable reference (`T?`) may hold `null` or a value of `T`.
- `T` is a subtype of `T?`.
- The `??` (coalesce) operator recovers a non-nullable value:
  `(n ?? 10)` has type `Int`.
- After a `== null` / `!= null` test, the compiler narrows the type within
  the branch.

### Conversions

There are no implicit conversions between primitive types. Convert explicitly
through the static `from` constructor of the target type:

```solvik
a: Int    = Int::from("42")
b: Float  = Float::from(3)
c: String = String::from(99)
d: Bool   = Bool::from(0)
e: Byte   = Byte::from(7)
f: Char   = Char::from('x')
```

`Int::from` accepts `Int`, `Float` (truncating), `Bool`, `Char`, and numeric
strings. Out-of-range conversions are runtime errors.

### Introspection

```solvik
Type::of(value)          -> String   // runtime type name
Type::isType(value, name) -> Bool    // dynamic type test
```

## 3. Literals

- Integers: decimal, `0x` hex, `0o` octal, `0b` binary; `_` digit separators.
- Floats: decimal with optional fraction/exponent.
- Strings: `"..."` with escapes (`\n \t \\ \" \u{...}`); raw strings
  `r"..."` disable escaping.
- Chars: `'a'`, `'\n'`.
- Bools: `true`, `false`.
- Null: `null`.
- Lists: `[1, 2, 3]` (trailing comma allowed).
- Maps: `{ "k": 1, "j": 2 }`.

## 4. Variables and fields

### Local variables

```solvik
x: Int = 5            // immutable local
mut y: Int = 10       // mutable local
```

The type annotation is required. An immutable variable cannot be reassigned.

### Class fields

Fields are private by default. Visibility modifiers:

```solvik
class Account {
    id: String          // private
    pub name: String    // public (accessible from other classes)

    mut {               // grouped mutable fields
        enabled: Bool
        loginCount: Int
        lastAudit: String?
    }
}
```

- Fields declared in a `mut { ... }` block are mutable; the block is field
  syntax only and never applies to methods.
- A single field may also be declared mutable with a leading `mut`.
- Immutable fields are initialized at construction and cannot be assigned
  afterwards. Mutable fields can be assigned from any method of the class.
- Field access uses `.`: `self.name`, `obj.name` (public fields only across
  classes).

## 5. Classes

```solvik
class Animal {
    pub name: String

    pub static new(name: String): Self {
        return Self { name }
    }

    pub speak(): String {
        return "..."
    }
}

class Dog extends Animal {
    override pub speak(): String {
        return "woof"
    }
}
```

- `extends` declares the single parent class.
- Constructors are ordinary static methods returning `Self`; by convention
  they are named `new`. Construction uses the object literal
  `Self { field: value, ... }` where omitted fields default to `null`/zero
  and shorthand `field` copies the local of the same name.
- Inherited constructors work transparently: `Dog::new("rex")` allocates a
  `Dog` even when `new` is defined on `Animal`.
- Methods are dispatched virtually through the vtable; `override` marks an
  intentional override of an inherited method.
- `super.method(...)` calls the parent's implementation.
- `Self` refers to the current class type (in declarations and construction).
- Method visibility: omitted = private, `protected`, or `pub`.

## 6. Interfaces

```solvik
interface Greetable {
    greeting(): String                    // abstract requirement
    farewell(): String {                  // default implementation
        return "bye from " .. greeting()
    }
}

class Bot implements Greetable {
    override pub greeting(): String { return "bot" }
}
```

- A class implements an interface with `implements` and must provide every
  abstract method.
- Default methods provide shared implementations; they may call other
  interface methods virtually.
- Calls through an interface-typed receiver dispatch at runtime to the
  implementing class's method (or the default).
- Calls through a concrete class type also reach inherited interface
  defaults.

## 7. Enums

```solvik
enum Color {
    Red
    Green
    Blue(Int)      // payload variant
}

enum Verdict<T> {  // generic enum
    Pass(T)
    Fail(String)
}
```

- Variants are constructed qualified: `Color::Red`, `Color::Blue(255)`.
- Variants may carry a single payload of any type.
- Enums are matched with `match` (section 10).
- Enum values compare by variant identity (and payload equality).

## 8. Generics

Classes, interfaces, enums, and methods may declare type parameters:

```solvik
class Box<T> {
    value: T
    pub static new(value: T): Self { return Self { value } }
    pub get(): T { return value }
}

p: Pair<Int, String> = Pair<Int, String>::new(7, "seven")
```

- Type arguments are written at use sites: `Box<Int>`,
  `Pair<Int, String>`. Static constructors of generic built-ins require
  explicit arguments: `List<Object>::new()`.
- Generics use **type erasure**: each method compiles exactly once; inside
  the body, type parameters behave as `Object`. Type safety is enforced
  statically at call sites.
- Type parameters may have interface constraints:
  `class Max<T: Comparable>` (constraints checked at instantiation).

## 9. Expressions and operators

Precedence (high to low):

1. Postfix: `.member`, `.method(...)`, `::static(...)`, `?` (nullable access)
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
- Comparisons produce `Bool`; `==`/`!=` on references compares content
  (strings by text, collections element-wise, objects by identity unless
  both sides are the same enum/string/primitive wrapper).
- `&&` and `||` short-circuit.
- `??` evaluates the right side only when the left is `null`.
- Assignment `=` and compound updates `+= -= *= /= %= ..=` are statements.
- Member access on a nullable reference requires `?` or prior narrowing.

### Built-in methods

Strings: `length contains startsWith endsWith indexOf substring charAt
replace split trim toUpperCase toLowerCase`.

Lists: `new add get set size contains indexOf remove reverse sort clear
join isEmpty`.

Maps: `new put get remove containsKey keys values size clear isEmpty`.

Stacks: `new push pop peek size isEmpty`.

Sets: `new add remove contains size clear isEmpty`.

All collection accessors take and return `Object`-typed values refined to
the element type by the receiver's type argument.

## 10. Control flow

```solvik
if cond { ... } else if cond2 { ... } else { ... }

while cond { ... }

for x in 1..10 { ... }          // range loop (start inclusive, end exclusive)
for item in someList { ... }    // collection loop

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
- Range loops and collection loops desugar to index loops.

## 11. Exceptions

```solvik
try {
    risky()
} catch (e) {
    handle(e)
} finally {
    cleanup()
}
```

- `throw value` raises any value (conventionally a `String` or an
  `Exception` object).
- `catch (e)` binds the thrown value.
- `finally` runs on both normal and exceptional completion.
- An exception passing through a `finally` without a `catch` continues
  unwinding to the next enclosing handler.
- Uncaught exceptions terminate the program with exit code 2.

## 12. Concurrency

```solvik
interface Runnable {
    run(): Void
}

t: Thread = Thread::new(myRunnable)
t.start()
t.join()
```

- Threads share one managed heap. Synchronize with `Mutex`
  (`Mutex::new()`, `lock()`, `unlock()`) or `Semaphore`
  (`Semaphore::new(n)`, `acquire()`, `release()`).
- `Thread::join()` blocks until the worker finishes.
- Blocking natives (I/O, sleep, join) release the heap lock; garbage
  collection runs only when the VM thread is the sole active thread.

## 13. Standard library

Static namespaces (called with `::`):

- `Math` — `abs min max pow sqrt floor ceil round` (Float math).
- `Base64` — `encode decode`.
- `Hash` — `md5 sha1 sha256` (hex digests).
- `Json` — `stringify parse` (maps/lists/scalars).
- `Time` — `now` (epoch ms), `sleep(ms)`.
- `Random` — `seed nextInt nextFloat`.
- `File` — `read write exists delete listDir`.
- `Test` — `assert assertEqual` (test helpers).

Streams: `stdout.println(x)`, `stdout.print(x)` — every value has a
universal `toString()`.

Processes: `Process::new(command, argumentList)`, `start()`, `wait()`, `exitCode()`,
plus `stdin/stdout/stderr` stream handles.

Regex: `Regex::new(pattern)` with `matches find all replace`.

## 14. Diagnostics and exit codes

Diagnostics carry codes by family: `L###` lexer, `P###` parser, `C###`
semantic/compiler, `V###` verifier, `E###` runtime.

Exit codes:

| Code | Meaning                     |
| ---- | --------------------------- |
| 0    | success                     |
| 1    | compilation error           |
| 2    | runtime error / uncaught exception |
| 3    | internal error              |
