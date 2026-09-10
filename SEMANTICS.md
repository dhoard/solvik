# Solvik Semantics

This document specifies the operational and type-theoretic semantics of
Solvik as implemented by the Rust compiler and bytecode VM.

## 1. Compilation pipeline

```
source -> lexer -> parser (AST) -> resolver (names/hierarchy)
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
- A class `C extends P` gives `C` <: `P`.
- A class implementing interface `I` gives `C` <: `I`.
- Built-in parameterized types are covariant in their arguments:
  `List<Long>` <: `List<Object>`.
- Primitives are nominal: no implicit widening or narrowing.

### Nullability rules

- `null` has type `Null`, a subtype of every `T?`.
- Dereferencing (member access, method call) requires a non-nullable
  receiver; the compiler narrows after null tests and rejects otherwise.
- `??` on `T?` yields `T`.

### Generics and erasure

- Type parameters are checked statically at declaration and instantiation
  sites (including interface constraints).
- At runtime each generic method compiles exactly once; type parameters are
  erased to `Object`. No reified generics exist.
- Instantiating a type argument that violates a constraint is a compile
  error (code C1xx).

### Dispatch

- Class methods dispatch virtually through per-class vtables; slot order is
  fixed by the resolution pass (parent slots first, then own methods).
- Interface calls resolve at runtime: the receiver's class table entry wins;
  otherwise the interface default runs.
- Calls on `Object`-typed receivers use dynamic dispatch by method name.
- Every value supports `toString()` via native dispatch.
- String concatenation (`..` with at least one statically String operand)
  uses the same built-in value formatting as `print`/`println`, including
  `"null"` for null and the message for runtime exception objects. It does
  not dispatch user-defined `toString` methods. Operands are evaluated once,
  left to right, before formatting; the result is a non-null String.

## 3. Value model

Runtime values are either primitives (`Bool`, `Long`, `Double`, `Char`) or
heap references. Heap objects: instances, strings, lists, maps, stacks,
enum values, exceptions, threads, mutexes, semaphores, processes, streams,
and regexes.

Equality semantics:

- Primitives compare by value.
- Strings compare by text.
- Enum values compare by variant (and payload).
- Other objects compare by identity, except through collection operations
  which use content equality for keys/elements.

## 4. Memory management

- The heap stores objects in a vector with a free-slot list and tracing
  mark-and-sweep garbage collection. Values reference slots by `u32` handles.
- Roots: the operand stack, all call frames' locals, global values, and
  thread runnables.
- GC may run only when the VM thread is the sole active thread; blocking
  natives drop the heap lock so worker threads can make progress.

## 5. Threading model

- One shared heap; one global heap lock.
- `Thread.start` spawns an OS thread executing the `Runnable.run` method.
- `join`, `sleep`, process `wait`, and file I/O block while releasing the
  heap lock.
- Data races are the program's responsibility; `Mutex`/`Semaphore` provide
  mutual exclusion and bounded concurrency.

## 6. Exceptions

- `throw` sets a pending exception and unwinds frames.
- Unwinding searches try-regions innermost-first; a matching `catch` binds
  the value and resumes at the handler; `finally` bodies run during both
  normal returns and unwinds.
- An exception with no handler terminates the process (exit code 2) after
  printing the exception's `toString()`.

## 5.5 Name resolution

- Innermost active binding wins. Within a single scope a name maps to one
  local slot; lookups resolve to the nearest declaration.
- Full block scoping: every statement-list body — while/for-in bodies,
  switch case bodies, try/catch/finally bodies, and match arms — is its own
  name scope. A local declared inside such a body is not visible after the
  body; loop variables and catch parameters are scoped to their body as well.
- A later declaration of the same name in the same block *shadows* the
  earlier binding. The shadowed binding is dead from the shadow point until
  the end of the block in which the shadow is declared; the earlier binding
  is then restored and visible again.
- Shadowing with a different type is allowed and allocates a fresh local slot.
  IR references slots by absolute index, so a dead binding stays inert in
  codegen — it never reuses its slot.
- Null-narrowing is invalidated on any shadow: reusing a narrowed name on a
  shadowing declaration drops the name from every narrowing map. The outer
  binding's narrowing is not restored, so later uses in the outer scope may
  emit spurious nullability errors that the user resolves with a fresh
  narrowing check. Correctness (never unsound) is preferred over
  optimization.
- Fields are class members resolved independently of local name lookup and
  are never shadowed.

## 7. Determinism

- Integer arithmetic is two's-complement 64-bit with runtime overflow
  checks (overflow is a runtime error).
- Map iteration order is unspecified; programs must not depend on it.
- `Time.now`, `Random`, and process interaction are the only
  nondeterministic sources.

## 8. Error model

| Stage      | Failure                | Outcome                    |
| ---------- | ---------------------- | -------------------------- |
| Lexer      | malformed token        | exit 1, `L###` diagnostic  |
| Parser     | syntax error           | exit 1, `P###` diagnostic  |
| Resolver   | unknown name/hierarchy | exit 1, `C###` diagnostic  |
| Checker    | type error             | exit 1, `C###` diagnostic  |
| Verifier   | invalid bytecode       | exit 1, `V###` diagnostic  |
| VM         | runtime fault          | exit 2, `E###` diagnostic  |
| VM         | uncaught exception     | exit 2                     |
| any        | internal invariant     | exit 3                     |
