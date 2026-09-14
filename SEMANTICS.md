# Solvik Semantics

This document specifies the operational and type-theoretic semantics of
Solvik as implemented by the Java 17 transpiler in `src/main/java`.

## 1. Compilation pipeline

```
source -> Lexer -> Parser (AST) -> SemanticAnalyzer (names/types/diagnostics)
       -> SolvikProgram / SolvikStmt / SolvikIr (typed IR)
       -> IrOptimizer (exact constant folding / branch simplification)
       -> JavaIr -> JavaEmitter -> package-free Java 17 source
       -> javac -> HotSpot JVM
```

- The **typed IR stage is mandatory**: the Java lowering consumes only the
  resolved declaration/statement/expression IR, never the parser AST.
- The **SemanticAnalyzer** is the sole place where Solvik meaning is decided:
  names, scopes, mutability, types, numeric promotion, overloads, trait
  conformance, delegation, and diagnostics. Diagnostics exit with code 1.
- The **IrOptimizer** folds only what is exact for Solvik semantics; it never
  rewrites an expression that would overflow or divide by zero, so runtime
  errors are preserved. See [TRANSPILER_JAVA.md](TRANSPILER_JAVA.md).

## 2. Type system

### Subtyping

- `T` <: `T?` for every reference type `T`.
- `T?` <: `Object?`.
- A struct implementing trait `I` gives `S` <: `I` (directly or
  transitively through trait inheritance).
- Interface `A extends B` gives `A` <: `B`.
- **Composition creates no subtype relationship.** Structs do not inherit
  from structs, so there is no `S extends P` subtyping, and holding a value
  in a field never makes the holder a subtype of the held type.
- Generic type arguments are **invariant**: `G<A>` <: `G<B>` only when
  `A == B` exactly (including nullability). In particular
  `List<Long>` is not a subtype of `List<Object>`.
- Primitives are nominal: no implicit widening or narrowing.

### Nullability rules

- `null` has type `Null`, a subtype of every `T?`.
- Dereferencing (member access, method call) of a nullable receiver is
  compiled with a runtime null check that raises a `null reference`
  exception when the receiver is `null`; prior narrowing omits the check.
- `??` on `T?` yields `T`.

### Generics and erasure

- Type parameters are checked statically at declaration and instantiation
  sites (including trait constraints).
- At runtime each generic method compiles exactly once; type parameters are
  erased to `Object`. No reified generics exist.
- Instantiating a type argument that violates a constraint is a compile
  error (code C1xx).

### Dispatch

- Concrete struct methods are never overridden by other structs (there is no
  struct inheritance), so a call on a statically known struct receiver
  targets a known function directly through that struct's method table.
- Interface calls resolve at runtime through the receiver's struct trait
  table. A struct's table is built from its **effective implementations**:
  explicit struct methods first, then delegation wrappers, then trait
  defaults (see the precedence rule below).
- Explicit trait delegation is lowered at compile time to an ordinary
  forwarding method: load `self`, load the private delegate field, evaluate
  each argument once left to right, and perform an ordinary trait call.
  There is no runtime delegation object, delegate chain, or delegation
  indirection.
- Calls on `Object`-typed receivers use per-struct dynamic dispatch by method
  name. The dynamic table exposes only the struct's public effective methods;
  private methods are never reachable dynamically.
- Every value supports `toString()` via native dispatch.
- String concatenation (`..` with at least one statically String operand)
  uses the same built-in value formatting as `print`/`println`, including
  `"null"` for null and the message for runtime exception objects. It does
  not dispatch user-defined `toString` methods. Operands are evaluated once,
  left to right, before formatting; the result is a non-null String.

### Effective method resolution

For each trait method requirement, the implementing source is selected
once, deterministically:

1. an explicit method declared on the struct;
2. an explicit `delegate` targeting a private field;
3. the most-specific unambiguous trait default;
4. otherwise the struct does not conform and compilation fails.

Explicit methods and delegation are checked for full signature
conformance (arity, parameter and return compatibility, nullability, and
generic substitution) exactly as if the forwarding method had been written
out by hand.

## 3. Value model

Runtime values are either primitives (`Boolean`, `Byte`, `Short`, `Integer`,
`Long`, `Float`, `Double`, `Char`) or heap references. Each numeric type keeps
its own boxed or primitive representation, so the static type is observable
at runtime; generated code dispatches arithmetic and comparison on the
operand types (integral operands compute at the wider operand's width with
checked overflow; floats compute in the wider precision). Heap objects: instances, strings,
lists, maps, stacks, sets, enum values, exceptions, big integers, big
decimals, threads, mutexes, semaphores, processes, streams, and regexes.

Equality semantics:

- Primitives compare by value; an integer and a float compare numerically
  (the integer is promoted), matching the static promotion rules.
- Strings compare by text.
- Enum values compare by variant (and payload).
- Collections compare structurally (element-wise / entry-wise).
- Other objects compare by identity, except through collection operations
  which use content equality for keys/elements.

### Universal object contract

Every reference value supports `toString(): String`,
`equals(other: Object?): Boolean`, and `hashCode(): Long`:

- `equals` uses exactly the content-equality semantics above; `x.equals(null)`
  is always `false`. Ordinary user objects compare by identity unless the
  struct defines its own `equals`.
- `hashCode` is deterministic and consistent with `equals`: values that
  compare equal always produce the same hash. Ordinary objects hash by
  identity (stable for the object's lifetime); strings hash by text; enums
  by variant and payload; lists/stacks as ordered folds, sets and maps as
  order-independent combinations of element/entry hashes.
- Map and Set membership use the same content equality as `==`/`equals`, so
  collection membership never diverges from the object contract.
- `==` keeps Solvik's structural behavior (the table above); `equals()` is
  the explicit method form of the same relation on reference values.

## 4. Memory management

- Objects are ordinary JVM objects. Reclamation is the JVM garbage
  collector's responsibility; collection timing and heap layout are not
  language guarantees.
- Object references preserve identity and aliasing. Copying a reference
  value does not copy the object.
- Roots are the generated program's static fields, live locals, thread
  runnables, and any JVM-reachable state; an object reachable only from a
  static field survives collection.
- Generated collections are ordinary Java collections with explicit
  synchronization; blocking operations do not hold a global heap lock.

## 4.5 Static fields

- **Storage.** Each struct that declares static fields owns one slot vector,
  held by the shared heap and therefore shared by all instances and all
  threads for the lifetime of the program. Static slots live outside any
  instance; construction allocates instance fields only.
- **Initialization.** The generator emits one static initializer per
  struct with static fields or a static block, evaluating each initializer in
  field declaration order and storing the results into the struct's slots,
  followed by the struct's single static block. Execution is lazy: the
  generated runtime runs the initializer at most once, immediately before
  the struct's first *active use* — a static field access, a static method
  call naming the struct, construction of the struct, or (for `Main` only)
  the entry-point dispatch. Type annotations, conformance checks, and method
  compilation never initialize a struct, and a struct that is never actively
  used never runs its initializer. Initializers are checked
  in a static context (no `self`, no instance fields, no locals) and may
  not read any static field, directly or through `Self.field`; calls inside
  initializers are permitted but must not depend on static state that has
  not been initialized yet.
- **Initialization state machine.** Each struct carries shared state visible
  to all Solvik threads: *uninitialized*, *initializing* (recording the
  owning thread), *initialized*, or *failed* (retaining the error message).
  Transitions happen under a per-struct lock.
  The owning thread runs the initializer as a bounded call on top of
  its current frames; caller handlers are hidden so an uncaught
  initializer error fails the active operation rather than landing in a
  caller catch handler. If the owning thread re-enters the same struct while
  it is initializing (recursive static calls, cyclic cross-struct
  dependencies), initialization is treated as already in progress and the
  struct's current default slots are exposed; initialization never reruns
  recursively. A different thread that reaches the struct while it is
  initializing waits on a condition variable — without holding a global lock
  and without running user code — and then observes either *initialized* or
  *failed*. A failed struct stays failed: later active uses fail with the
  cached error and user code never reruns. A language exception caught
  inside the initializer unwinds through the normal exception path, and the
  initializer completes if the function returns normally.
- **Static blocks.** A struct may declare at most one `static { ... }`
  block (a second is a parse error). Its statements are emitted into the
  same synthetic static initializer, *after* all of the struct's static
  field initializers, so the block observes fully-initialized static state
  and may read and write (mutable) static fields of the declaring struct.
  Inside the block, static fields and static methods of the declaring struct
  also resolve by bare name (no `Self.` qualifier): an
  identifier that is not a local loads the static slot, an assignment to
  such an identifier stores it, and a bare call targets the struct's static
  method. Locals take precedence over static field names in bare lookup;
  the rule applies only inside the
  block. The body is otherwise checked as a static-context statement block:
  no `self`, no instance fields, no parameters; locals and control flow are
  allowed; a bare `return` exits the block early and `return expr` is an
  error. A struct with a static block but no static fields still gets its
  synthetic initializer. A runtime error thrown in the block fails the
  first active use and marks the struct failed, like a failing initializer.
- **GC.** Static slots are reachable from the generated class and are kept
  alive by the JVM.
- **Threading.** Static field access is synchronized by the generated
  runtime, so there is no data race on the slot itself. Read-modify-write
  sequences across threads remain the programmer's responsibility and use
  the existing `Mutex`/`Semaphore` facilities.

## 5. Threading model

- Objects are shared through the JVM heap; there is no separate managed heap
  or global heap lock.
- `Thread.start` spawns a Java thread executing the `Runnable.run` method.
- `join`, `sleep`, process `wait`, and file I/O block the calling Java thread
  without holding a global lock.
- Collections are individually synchronized: each `List`, `Map`, `Stack`,
  and `Set` carries its own monitor, and every collection operation is
  linearizable under that monitor. Unrelated collections progress
  concurrently; operations on the same collection serialize.
- Data races are the program's responsibility; `Mutex`/`Semaphore` provide
  mutual exclusion and bounded concurrency.

## 6. Exceptions

- `throw` accepts only values whose type is `Exception`, a struct, or an
  trait (anything conforming to the built-in `Throwable` trait);
  other values are rejected at compile time (`C242`).
- `Exception.new(message)` constructs the built-in exception object carrying
  a `String` message.
- Catch clauses are typed (`catch (e: Type)`) and repeatable; clauses are
  tested in source order against the thrown value's runtime type (struct,
  trait conformance, or native kind). The first conforming clause binds
  the value and resumes at its handler.
- Unwinding searches enclosing handlers innermost-first; `finally` bodies
  run during both normal returns and unwinds.
- An exception with no conforming handler terminates the process (exit code
  2) after printing the exception's message.

## 5.5 Name resolution

- Innermost active binding wins. Within a single scope a name maps to one
  local slot; lookups resolve to the nearest declaration.
- Full block scoping: every statement-list body — while/for-in bodies,
  switch case bodies, try/catch/finally bodies, and match arms — is its own
  name scope. A local declared inside such a body is not visible after the
  body; loop variables and catch parameters are scoped to their body as well.
- Redeclaring a name that is still visible in the current or any enclosing
  scope is a compile error (`C240`); there is no shadowing. This applies to
  plain locals, `for-in` loop variables, catch parameters, match pattern
  bindings, and scope-block declarations alike.
- Fields are struct members resolved independently of local name lookup and
  are never redeclared as locals of the same name within a method body that
  uses them ambiguously; field access is always explicit (`self.f` or
  `Struct.f`).
- A standalone `{ ... }` statement (scope block) creates an independent name
  scope. The checker calls `begin_scope` / `end_scope` around the block body.
  Name rules work exactly as with existing scopes.
- Variable destruction at scope-block exit follows Solvik's existing resource
  management model.
- `return` inside a scope block is a compile error (`C141`). Scope blocks
  are not function bodies.
- `break`/`continue` resolve through scope block boundaries to the nearest
  enclosing loop. If no enclosing loop exists, a compile error is emitted.

## 7. Determinism

- Integer arithmetic is two's-complement 64-bit with runtime overflow
  checks (overflow is a runtime error).
- Map key iteration order and Set member iteration order are unspecified
  (hash layout); programs must not depend on them. Every Set member is
  visited exactly once by a `for-in` loop.
- `Time.now`, `Random`, and process interaction are the only
  nondeterministic sources.

## 8. Error model

| Stage      | Failure                | Outcome                    |
| ---------- | ---------------------- | -------------------------- |
| Lexer      | malformed token        | exit 1, `L###` diagnostic  |
| Parser     | syntax error           | exit 1, `P###` diagnostic  |
| Analyzer   | unknown name/trait | exit 1, `C###` diagnostic  |
| Analyzer   | type error             | exit 1, `C###` diagnostic  |
| generated  | runtime fault          | exit 2, `E###` diagnostic  |
| generated  | uncaught exception     | exit 2                     |
| any        | internal invariant     | exit 3                     |
