# Solvik Semantics

This document specifies the operational and type-theoretic semantics of
Solvik as implemented by the Rust compiler and bytecode VM.

## 1. Compilation pipeline

```
source -> lexer -> parser (AST) -> resolver (names/interfaces)
       -> checker (types + IR emission) -> IR module
       -> control-flow-aware IR optimization -> bytecode compiler
       -> code module -> binary encode/decode round trip -> verifier
       -> predecoded instructions -> VM (stack machine over a managed heap)
```

- The **IR stage is mandatory**: the bytecode compiler consumes only IR,
  never the AST.
- The **verifier** runs exact dataflow analysis over basic blocks: a worklist
  propagates a finite abstract state (operand height, active try-region
  stack, pending-transfer flag) and requires every join to receive one
  consistent state. It also checks underflow, unreachable code, terminator
  and return-shape discipline, region setup/cleanup, dispatch-target
  consistency, and rejects the obsolete `ListSpread` opcode. Programs that
  fail verification are rejected before execution; see
  [docs/VERIFIER.md](docs/VERIFIER.md) for the contract and guarantees.

## 2. Type system

### Subtyping

- `T` <: `T?` for every reference type `T`.
- `T?` <: `Object?`.
- A class implementing interface `I` gives `C` <: `I` (directly or
  transitively through interface inheritance).
- Interface `A extends B` gives `A` <: `B`.
- **Composition creates no subtype relationship.** Classes do not inherit
  from classes, so there is no `C extends P` subtyping, and holding a value
  in a field never makes the holder a subtype of the held type.
- Built-in parameterized types are covariant in their arguments:
  `List<Long>` <: `List<Object>`.
- Primitives are nominal: no implicit widening or narrowing.

### Nullability rules

- `null` has type `Null`, a subtype of every `T?`.
- Dereferencing (member access, method call) of a nullable receiver is
  compiled with a runtime null check that raises a `null reference`
  exception when the receiver is `null`; prior narrowing omits the check.
- `??` on `T?` yields `T`.

### Generics and erasure

- Type parameters are checked statically at declaration and instantiation
  sites (including interface constraints).
- At runtime each generic method compiles exactly once; type parameters are
  erased to `Object`. No reified generics exist.
- Instantiating a type argument that violates a constraint is a compile
  error (code C1xx).

### Dispatch

- Concrete class methods are never overridden by subclasses (there are no
  subclasses), so a call on a statically known class receiver targets a
  known function directly through that class's method table.
- Interface calls resolve at runtime through the receiver's class interface
  table. A class's table is built from its **effective implementations**:
  explicit class methods first, then delegation wrappers, then interface
  defaults (see the precedence rule below).
- Explicit interface delegation is lowered at compile time to an ordinary
  forwarding method: load `self`, load the private delegate field, evaluate
  each argument once left to right, and perform an ordinary interface call.
  There is no runtime delegation object, delegate chain, or delegation
  opcode.
- Calls on `Object`-typed receivers use per-class dynamic dispatch by method
  name. The dynamic table exposes only the class's public effective methods;
  private methods are never reachable dynamically.
- Every value supports `toString()` via native dispatch.
- String concatenation (`..` with at least one statically String operand)
  uses the same built-in value formatting as `print`/`println`, including
  `"null"` for null and the message for runtime exception objects. It does
  not dispatch user-defined `toString` methods. Operands are evaluated once,
  left to right, before formatting; the result is a non-null String.

### Effective method resolution

For each interface method requirement, the implementing source is selected
once, deterministically:

1. an explicit method declared on the class;
2. an explicit `delegate` targeting a private field;
3. the most-specific unambiguous interface default;
4. otherwise the class does not conform and compilation fails.

Explicit methods and delegation are checked for full signature
conformance (arity, parameter and return compatibility, nullability, and
generic substitution) exactly as if the forwarding method had been written
out by hand.

## 3. Value model

Runtime values are either primitives (`Boolean`, `Byte`, `Short`, `Integer`,
`Long`, `Float`, `Double`, `Char`) or heap references. Each numeric type keeps
its own runtime variant, so the static type is observable at runtime; the VM
dispatches arithmetic and comparison on the operand variants (integral
operands compute in i64 at the wider operand's width with checked overflow;
floats compute in the wider precision). Heap objects: instances, strings,
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
  class defines its own `equals`.
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

- The heap stores objects in a vector with a free-slot list and tracing
  mark-and-sweep garbage collection. Values reference slots by `u32` handles.
- Roots: the operand stack, all call frames' locals, global values, thread
  runnables, and every static field slot.
- GC may run only when the VM thread is the sole active thread; blocking
  natives drop the heap lock so worker threads can make progress.

## 4.5 Static fields

- **Storage.** Each class that declares static fields owns one slot vector,
  held by the shared heap and therefore shared by all instances and all
  threads for the lifetime of the program. Static slots live outside any
  instance; `NewObject` allocates instance fields only.
- **Initialization.** The checker synthesizes one static-init function per
  class with static fields or a static block, evaluating each initializer in
  field declaration order and storing the results into the class's slots.
  The VM runs these functions in class declaration order exactly once, before
  dispatching the entry point. Initializers are checked in a static context
  (no `self`, no instance fields, no locals) and may not read any static
  field, directly or through `Self.field`; calls inside initializers are
  permitted but must not depend on static state that has not been
  initialized yet. A failing initializer propagates as a normal runtime
  error and aborts startup.
- **Static blocks.** A class may declare at most one `static { ... }`
  block (a second is a parse error). Its statements are compiled into the
  same synthetic static-init function, *after* all of the class's static
  field initializers, so the block observes fully-initialized static state
  and may read and write (mutable) static fields of the declaring class.
  Inside the block, static fields and static methods of the declaring class
  also resolve by bare name (no `Self.`/`ClassName.` qualifier): an
  identifier that is not a local loads the static slot, an assignment to
  such an identifier stores it, and a bare call targets the class's static
  method. Locals take precedence over static field names in bare lookup;
  the rule applies only inside the
  block. The body is otherwise checked as a static-context statement block:
  no `self`, no instance fields, no parameters; locals and control flow are
  allowed; a bare `return` exits the block early and `return expr` is an
  error. A class with a static block but no static fields still gets its
  synthetic initializer. A runtime error thrown in the block aborts startup
  like a failing initializer.
- **GC.** Static slots are GC roots: an object reachable only from a static
  field survives collection.
- **Threading.** Static access happens while the heap lock is held, so there
  is no data race on the slot itself. Read-modify-write sequences across
  threads remain the programmer's responsibility and use the existing
  `Mutex`/`Semaphore` facilities.

## 5. Threading model

- One shared heap; one global heap lock.
- `Thread.start` spawns an OS thread executing the `Runnable.run` method.
- `join`, `sleep`, process `wait`, and file I/O block while releasing the
  heap lock.
- Collections are individually synchronized: each `List`, `Map`, `Stack`,
  and `Set` carries its own lock, and every collection operation is
  linearizable under that lock. Unrelated collections progress concurrently;
  operations on the same collection serialize. Lock ordering: the heap lock
  is outermost; a collection operation may briefly take the heap lock while
  holding its collection lock (for key equality/hashing), and GC marking
  walks heap-to-collection only while the VM thread is the sole active
  thread, so the two orders never interleave.
- Data races are the program's responsibility; `Mutex`/`Semaphore` provide
  mutual exclusion and bounded concurrency.

## 6. Exceptions

- `throw` accepts only values whose type is `Exception`, a class, or an
  interface (anything conforming to the built-in `Throwable` interface);
  other values are rejected at compile time (`C242`).
- `Exception.new(message)` constructs the built-in exception object carrying
  a `String` message.
- Catch clauses are typed (`catch (e: Type)`) and repeatable; clauses are
  tested in source order against the thrown value's runtime type (class,
  interface conformance, or native kind). The first conforming clause binds
  the value and resumes at its handler.
- Unwinding searches try-regions innermost-first; `finally` bodies run during
  both normal returns and unwinds.
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
- Fields are class members resolved independently of local name lookup and
  are never redeclared as locals of the same name within a method body that
  uses them ambiguously; field access is always explicit (`self.f` or
  `Class.f`).
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
| Resolver   | unknown name/interface | exit 1, `C###` diagnostic  |
| Checker    | type error             | exit 1, `C###` diagnostic  |
| Verifier   | invalid bytecode       | exit 1, `V###` diagnostic  |
| VM         | runtime fault          | exit 2, `E###` diagnostic  |
| VM         | uncaught exception     | exit 2                     |
| any        | internal invariant     | exit 3                     |
