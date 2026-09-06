# Solvik Completion Master Prompt

You are evolving the Solvik programming language repository toward a complete, coherent, modern, non-GUI general-purpose programming language.

This is a **phased language-engineering program**, not an open-ended refactor.

Your job is to execute **exactly one phase per invocation**, validate it completely, record the result, and stop at the phase boundary.

Do not skip ahead. Do not partially implement later phases. Do not change the language casually outside the active phase.

---

# 1. Repository and authority model

The repository is Solvik.

The intended authority hierarchy is:

```text
LANGUAGE.md
    ↓
solvik.py
    ↓
conformance tests
    ↓
Go implementation
    ↓
Rust implementation
```

Interpret this precisely:

1. `LANGUAGE.md` is the normative written language specification.
2. `solvik.py` is the normative executable semantic reference.
3. The conformance suite converts normative semantics into executable assertions.
4. Go and Rust are optimized implementations and must eventually match Python.
5. During language-design phases, Python is implemented first.
6. Do not use Go or Rust behavior to override correct Python semantics.
7. Do not simultaneously invent incompatible semantics in all three implementations.

When written specification and Python disagree:

- determine the intended language semantics,
- make `LANGUAGE.md` explicit,
- make Python match it,
- add conformance tests,
- document the change.

The repository must move toward a state where every meaningful user-visible semantic rule is represented by at least one executable test.

---

# 2. Core language identity

Preserve Solvik's existing shape unless a phase explicitly requires a semantic extension.

The intended design direction is:

```text
Swift-like value/type model
+ Rust-like traits and generics
+ Go-like simplicity and structural typing
```

Important principles:

- immutable by default
- `mut` remains explicit
- value semantics remain simple and predictable
- structural traits remain implicit
- no `implements` keyword unless an overwhelming language-design reason is discovered and documented
- no class hierarchy
- no inheritance
- no `extends`
- no virtual/abstract class system
- no Rust-style borrow checker
- no explicit lifetime system
- no user-facing `&T` / `&mut T` ownership model
- no macro system unless added by a future explicit phase
- no unnecessary type-level programming
- no hidden conversions except existing intentional numeric widening
- prefer simple explicit semantics over clever implementation tricks

Built-in values should behave like ordinary Solvik value types from the programmer's perspective.

Compiler/interpreter implementations may use intrinsic representations internally, but built-ins should participate in methods, traits, equality, generic constraints, iteration, and other language abstractions under the same semantic rules as user-defined values wherever practical.

---

# 3. Existing concepts that must be preserved

Unless an active phase intentionally changes them, preserve:

- `package`
- `use`
- top-level `func`
- structs
- traits
- enums
- immutable bindings by default
- `mut`
- `pub`
- named-field struct literals
- `self`
- `mut func`
- structural trait satisfaction
- nullable `T?`
- `??`
- `Any`
- `isType`
- `typeOf`
- numeric widening `Byte -> Int -> Float`
- exceptions
- `try/catch/finally/throw`
- first-match `switch`
- lists
- maps
- stacks
- string iteration
- map two-binding iteration
- package-qualified function access
- current standard-library behavior unless deliberately superseded

Do not perform broad syntax churn merely for stylistic preference.

---

# 4. Execution model

On every invocation, perform these steps.

## Step A — establish repository state

Inspect:

- `LANGUAGE.md`
- `README.md`
- `solvik.py`
- `PARITY.md` if present
- `PHASE_STATUS.md` if present
- build scripts
- conformance tests
- Go implementation
- Rust implementation
- recent changes relevant to the current phase

Do not assume the repository is identical to a prior invocation.

## Step B — determine the active phase

If `PHASE_STATUS.md` does not exist, create it.

It must contain at minimum:

```markdown
# Solvik Completion Status

Current phase: <number and title>
Status: pending | in_progress | complete | blocked

## Completed phases
- ...

## Current phase
Goal:
Entry criteria:
Work completed:
Validation completed:
Known limitations:
Deferred work:

## Next phase
...
```

If a phase is already complete, advance to the next incomplete phase.

If the user explicitly names a phase, execute that phase only if its entry criteria are satisfied.

## Step C — create or update a phase plan

Before implementation, write a concrete plan into `PHASE_STATUS.md`.

The plan must identify:

- semantic changes
- parser/AST changes
- static-analysis changes
- runtime/evaluator changes
- standard-library changes
- tests
- documentation
- expected Go/Rust impact
- compatibility considerations
- migration concerns

Do not create a second competing roadmap unless necessary.

## Step D — implement

Implement the active phase completely in the Python semantic reference.

Do not begin the next phase.

Where the active phase changes syntax or semantics, update the written specification at the same time.

## Step E — validate

Run all relevant checks.

At minimum:

```bash
python3 -m py_compile solvik.py
python3 tools/parity.py --reference-only
```

Also run the repository's normal tests/build commands where practical.

If existing build infrastructure runs Go/Rust and those implementations are intentionally behind the new Python semantics, do not weaken Python semantics merely to make them pass.

Instead:

- clearly distinguish expected parity gaps from regressions,
- record those gaps in `PARITY.md`,
- ensure existing unrelated Go/Rust behavior still builds where possible.

## Step F — review the implementation

Perform a deliberate self-review after tests.

Look for:

- parser ambiguities
- unsound typing
- missing runtime enforcement
- accidental implicit conversions
- broken mutability rules
- nullable edge cases
- incomplete generic substitution
- incorrect structural trait matching
- copy/value-semantic bugs
- inconsistent diagnostics
- incorrect package/type identity
- undocumented behavior
- test gaps
- implementation duplication

Fix issues discovered during review.

## Step G — phase exit

A phase is complete only if all exit criteria for that phase are satisfied.

Update `PHASE_STATUS.md`:

```text
Status: complete
```

Record:

- implemented behavior
- tests added
- validation commands run
- known intentional limitations
- changes deferred to future phases

Then STOP.

Do not automatically begin the next phase.

---

# 5. Global engineering constraints

Apply these constraints during every phase.

## 5.1 Preserve a readable Python oracle

`solvik.py` is not merely another implementation.

It is the executable semantic model.

Therefore:

- keep semantic rules easy to locate
- avoid unnecessary framework-like abstractions
- avoid excessive indirection
- centralize type rules
- centralize generic substitution
- centralize built-in behavioral metadata
- centralize trait satisfaction
- centralize value-copy semantics
- centralize callable semantics
- prefer explicit functions/classes with clear names

The Python implementation should remain understandable to a language implementer reading it top-to-bottom.

## 5.2 Specification-driven behavior

Every new user-visible semantic rule requires:

1. written specification
2. Python implementation
3. conformance coverage

Never implement a major language rule only in comments or only in tests.

## 5.3 Diagnostics

New static errors should:

- have stable diagnostic codes
- identify the primary source location
- explain what is wrong
- avoid implementation terminology
- be deterministic

Do not silently accept malformed or ambiguous programs.

## 5.4 Compatibility

Prefer additive evolution.

If a phase intentionally breaks syntax or semantics:

- document the incompatibility
- explain why it is necessary
- update examples/tests
- avoid unrelated breaking changes

## 5.5 No fake completion

Never mark a feature complete because:

- parsing works but typing does not
- typing works but runtime behavior does not
- the happy path works but invalid programs are not rejected
- Python works but tests are absent
- documentation describes behavior that the implementation does not provide

A feature is complete only when syntax, static semantics, runtime semantics, diagnostics, specification, and tests are coherent.

---

# 6. Phase 0 — Baseline integrity

## Goal

Establish a trustworthy starting point.

## Required work

- confirm Python is designated as executable semantic reference
- confirm `LANGUAGE.md` is normative written specification
- confirm conformance tooling exists
- confirm `PARITY.md` exists or create it
- confirm `PHASE_STATUS.md` exists or create it
- run baseline Python checks
- run existing repository tests
- identify existing failures before modifying semantics
- record the baseline

## Exit criteria

- baseline state documented
- Python reference runs
- existing conformance suite behavior recorded
- existing Go/Rust parity gaps recorded
- no unexplained baseline failures

Do not redesign language features in Phase 0.

---

# 7. Phase 1 — Generic/type-system foundation verification

This phase verifies and completes the generic/structural foundation already introduced.

## Goal

Make user-defined generics and built-in structural behavior reliable enough for all later phases.

## Required capabilities

### Generic functions

Support:

```solvik
func identity<T>(value: T) -> T {
    return value
}
```

### Generic structs

Support:

```solvik
struct Box<T> {
    pub value: T
}
```

### Generic traits

Support:

```solvik
trait Iterable<T> {
    func iterator() -> List<T>
}
```

### Generic constraints

Support a simple readable form such as:

```solvik
func show<T: Stringable>(value: T) -> String
```

and, if already chosen by the repository, multiple constraints using the established syntax.

### Required semantics

- deterministic type-parameter declaration
- type-argument substitution
- generic call inference
- explicit type arguments if the language supports them
- constraint validation
- recursive generic types where valid
- nullable generic values
- generics nested in List/Map/Stack
- generic struct field checking
- generic return checking
- structural trait compatibility after substitution

### Built-in structural behavior

Built-ins should participate coherently in established core traits such as:

- Stringable
- Equatable
- Comparable
- Hashable
- Countable
- Iterable<T>
- Collection<T>

Do not force heap/object wrappers for scalar values merely to model traits.

## Tests

Include positive and negative tests for:

- inference
- failed inference
- constraint success
- constraint failure
- nested generics
- wrong arity
- wrong type argument
- nullable generic values
- built-ins satisfying structural traits
- user structs satisfying structural traits

## Exit criteria

Generics are sufficiently stable that later closure, ADT, and collection work can depend on them.

---

# 8. Phase 2 — First-class functions and closures

## Goal

Make functions ordinary typed values and add lexical closures.

This is the next major expressive feature.

## Required language capabilities

### Function types

Choose and document one syntax consistent with Solvik.

A candidate shape is:

```solvik
Func<Int, String>
```

meaning:

```text
(Int) -> String
```

If another syntax fits the existing parser more naturally, use it only after documenting the decision.

Function types must encode:

- parameter types
- return type
- variadic status if supported
- no receiver mutability unless methods are explicitly converted to callable values

### Functions as values

Allow:

```solvik
f: Func<Int, Int> = double
```

### Functions as arguments

```solvik
func apply(value: Int, f: Func<Int, Int>) -> Int {
    return f(value)
}
```

### Functions as returns

```solvik
func makeAdder(amount: Int) -> Func<Int, Int>
```

### Anonymous functions / closures

Introduce one clear syntax.

Example design target:

```solvik
multiply: Func<Int, Int> = func(x: Int) -> Int {
    return x * factor
}
```

Do not add multiple anonymous-function syntaxes in the same phase.

### Lexical capture

Closures must capture lexical bindings predictably.

Define explicitly:

- capture of immutable values
- capture of mutable bindings
- value vs cell/reference capture behavior
- nested closures
- shadowing
- lifetime of captured environment
- closure equality behavior
- closure String/type identity behavior

Keep this much simpler than Rust ownership semantics.

### Generics

Support generic higher-order functions:

```solvik
func mapOne<T, R>(value: T, transform: Func<T, R>) -> R {
    return transform(value)
}
```

### Static analysis

Reject:

- assigning incompatible function signatures
- wrong callable arity
- wrong callable argument types
- wrong callable return type
- invalid captures
- calls to non-callable values

## Tests

Cover:

- top-level function values
- anonymous functions
- immutable capture
- mutable capture
- nested closure
- returned closure
- recursive named functions
- higher-order generics
- bad function assignment
- bad function call
- shadowed captured names

## Exit criteria

Solvik can express useful higher-order algorithms without implementation-specific escape hatches.

---

# 9. Phase 3 — Algebraic enums and pattern matching

## Goal

Upgrade enums from opaque integer-backed constants into optional algebraic/sum types while preserving simple no-payload enum cases.

## Required syntax

Preserve:

```solvik
enum Color {
    Red
    Green
    Blue
}
```

Add payload cases:

```solvik
enum Result<T, E> {
    Ok(T)
    Error(E)
}
```

Potential multi-field payloads must use one consistent model.

Prefer positional case payloads unless named payloads materially improve the language.

## Required semantics

- generic enums
- payload construction
- case identity
- structural equality where payloads are equatable
- deterministic `typeOf`
- compatibility with `Any`
- nullable interaction
- switch matching
- pattern-bound variables
- nested patterns if reasonably simple
- wildcard/default behavior

## Pattern matching

Target:

```solvik
switch result {
    case Result.Ok(value) {
        println(value)
    }
    case Result.Error(error) {
        println(error)
    }
}
```

If same-enum qualification can be safely omitted, document the exact name-resolution rule.

## Exhaustiveness

The checker should detect non-exhaustive switches over closed algebraic enums when no `default` exists.

Also detect:

- duplicate case patterns
- impossible cases
- payload arity mismatch
- payload type mismatch

## Existing enum compatibility

Existing explicit integer values may remain supported only for non-payload enums unless a coherent rule is defined.

Do not mix integer-backed semantics and payload storage ambiguously.

## Exit criteria

Solvik has a practical typed sum-type mechanism suitable for results, state machines, compiler ASTs, and self-hosting.

---

# 10. Phase 4 — Static type checker hardening

## Goal

Move Solvik from "typed when convenient" toward a trustworthy statically typed language.

Python runtime checks may remain as defensive enforcement, but invalid statically knowable programs should be rejected before execution.

## Required work

Strengthen:

### Declarations

- duplicate types
- duplicate functions
- duplicate fields
- duplicate parameters
- invalid recursive definitions
- unknown types
- generic arity

### Calls

- argument count
- argument type
- variadic rules
- generic inference
- generic constraints
- function-value calls
- return typing

### Control flow

- all required return paths
- unreachable code where straightforward
- break/continue placement
- switch type compatibility
- exhaustive algebraic enum switch

### Nullability

- null narrowing
- nullable member access
- nullable calls/indexing
- coalescing
- nested nullable generics
- nullable function values

### Mutability

- immutable bindings
- mutating receivers
- field mutability
- closure capture mutation
- collection mutation semantics

### Traits

- complete method-signature matching
- generic trait substitution
- built-in trait compatibility
- mutating method requirements
- public visibility requirements

### Assignability

Create one centralized authoritative assignability relation.

Avoid scattered type-conversion logic.

## Exit criteria

Most invalid programs fail during reference validation rather than after execution begins.

---

# 11. Phase 5 — Package and type identity model

## Goal

Make multi-package programs practical.

The current language must move away from ambiguous bare-String type identity.

## Required internal model

Introduce canonical type identity.

Conceptually:

```text
package + type name + generic arguments
```

Examples:

```text
foo.User
http.Client
collections.Box<Int>
```

Do not rely on lowercased display strings for identity.

## Required source capabilities

Allow external package types:

```solvik
client: http.Client
```

Allow constructors/literals according to the chosen syntax:

```solvik
client: http.Client = http.Client {
    ...
}
```

Define:

- package namespace lookup
- type qualification
- function qualification
- enum qualification
- trait qualification
- generic qualified types
- visibility rules
- same-package unqualified access
- conflicting dependency names

## Dependency considerations

Preserve `use` unless there is a compelling reason to redesign it.

If aliases are needed, add them deliberately in this phase and document them.

## Exit criteria

Real libraries can publish and consume types across package boundaries without type-name collisions.

---

# 12. Phase 6 — Core standard library completion

## Goal

Provide enough standard library functionality to build practical non-GUI applications and eventually implement Solvik tooling in Solvik.

Do not add everything imaginable.

Favor coherent, composable APIs.

## Required areas

### Collections

With closures available, support operations such as:

- Map
- filter
- reduce/fold
- find
- Any
- all
- contains
- first/last where appropriate

These should follow the established built-in/value/trait design rather than becoming unrelated global helpers.

### Text

- String manipulation
- Unicode-safe Char operations
- splitting/joining
- conversion
- formatting strategy if needed

### Filesystem

- file read/write/append
- directory operations
- metadata
- path operations

### Process

- arguments
- environment
- spawn/run
- exit status
- stdout/stderr capture if feasible

### Encoding/data

- Base64
- hashes
- JSON

JSON should have a clearly typed model.

Do not hide all JSON behind unsafe `Any` unless the design explicitly provides both dynamic and typed approaches.

### Networking

At minimum:

- TCP foundations OR
- practical HTTP client

Prefer a useful HTTP client if the goal is general application development.

### Time

- timestamps
- duration
- sleep
- formatting/parsing if practical

### Testing support

Provide a minimal language-native testing facility or standard test package before self-hosting.

## Exit criteria

A user can write useful CLI/server-side utilities without immediately leaving the language.

---

# 13. Phase 7 — Semantic freeze candidate

## Goal

Stop inventing major language semantics long enough to stabilize a candidate Solvik 1.0 contract.

## Required work

Audit the entire language.

Create a language-completeness matrix covering:

- lexical syntax
- types
- generics
- function types
- closures
- nullability
- structs
- traits
- enums/ADTs
- pattern matching
- collections
- iteration
- exceptions
- packages
- visibility
- standard library
- diagnostics
- command-line behavior

Identify:

- contradictions
- undocumented behavior
- obsolete syntax
- accidental semantics
- implementation-only behavior

Resolve them in Python/spec/tests.

## Required artifact

Create or update a versioned semantic document such as:

```text
Solvik Language Semantics 1.0 Candidate
```

Do not claim final 1.0 yet.

## Exit criteria

The Python implementation and specification are stable enough that Go/Rust can be ported without language semantics changing underneath them.

---

# 14. Phase 8 — Go parity

## Goal

Make the Go implementation match the frozen Python semantics.

## Rules

Python is the oracle.

Do not weaken Python behavior to simplify Go implementation.

For each parity gap:

1. reproduce with a minimal fixture
2. confirm Python/spec behavior
3. implement Go behavior
4. run differential comparison
5. add regression test

## Required parity areas

- parser
- type model
- generics
- function values
- closures
- ADTs
- pattern matching
- nullability
- traits
- package types
- stdlib
- diagnostics
- output
- exit code
- runtime errors

## Exit criteria

Go passes the full conformance corpus and differential reference suite.

No known semantic gaps remain.

---

# 15. Phase 9 — Rust parity

## Goal

Make Rust match the same frozen Python semantics.

Use the same process as Go.

Do not use Go as the semantic authority.

Python/spec remain authoritative.

## Exit criteria

Rust passes the full conformance corpus and differential reference suite.

No known semantic gaps remain.

---

# 16. Phase 10 — Tooling and developer quality

## Goal

Make Solvik pleasant enough to use as a real language.

## Candidate work

Prioritize:

- formatter
- stable diagnostic formatting
- source spans
- Stack traces
- compiler/interpreter versioning
- dependency cache behavior
- deterministic builds
- command-line ergonomics
- test runner
- LSP foundations
- syntax highlighting metadata
- package inspection tools

Do not add GUI tooling.

## Exit criteria

Core development workflows are predictable and scriptable.

---

# 17. Phase 11 — Self-hosting preparation

## Goal

Determine whether Solvik is capable of implementing its own frontend.

Do not immediately rewrite the entire compiler.

First validate language sufficiency.

## Required capabilities checklist

Confirm Solvik can comfortably express:

- lexer
- parser
- AST
- recursive structures
- enums/ADTs
- visitors or pattern matching
- maps
- lists
- strings
- Byte processing
- filesystem I/O
- diagnostics
- generics
- closures
- package loading
- tests

Any missing fundamental capability returns the project to the appropriate earlier semantic phase.

## Required first bootstrap targets

Implement in Solvik, incrementally:

1. tokenizer/lexer library
2. parser library
3. AST representation
4. basic semantic/type model

These do not replace the trusted Python reference yet.

## Exit criteria

A significant frontend subset can be implemented and tested in Solvik itself.

---

# 18. Phase 12 — Bootstrap and 1.0 readiness

## Goal

Move toward a self-hosting implementation without sacrificing the Python semantic oracle prematurely.

## Suggested bootstrap sequence

```text
Python reference
    ↓
Solvik lexer
    ↓
Solvik parser
    ↓
Solvik AST
    ↓
Solvik checker
    ↓
Solvik compiler/interpreter
```

At each stage, compare against the Python reference.

Do not discard Python merely because a self-hosted implementation exists.

Python remains valuable as the smallest readable semantic oracle.

## 1.0 release gate

Solvik 1.0 should not be declared until:

- specification is complete
- Python reference passes all tests
- Go parity is complete
- Rust parity is complete, or explicitly removed from 1.0 scope
- package/type system is stable
- stdlib baseline is usable
- diagnostics are stable enough for normal development
- conformance suite covers all major semantics
- no known critical semantic contradictions remain
- bootstrap path is demonstrated or deliberately deferred with rationale

---

# 19. Parity tracking requirements

Maintain `PARITY.md`.

Use a table similar to:

```markdown
| Feature | Python | Go | Rust | Notes |
|---|---|---|---|---|
| Generic functions | reference | pending | pending | |
| Closures | reference | pending | pending | |
| Algebraic enums | reference | pending | pending | |
```

Statuses should be concrete:

- reference
- complete
- partial
- pending
- intentionally unsupported

Do not write vague statuses like "mostly works."

---

# 20. Conformance-test philosophy

Prefer many small semantic fixtures over a few giant integration programs.

For every feature, include:

## Positive tests

Programs that must:

- parse
- validate
- execute
- produce deterministic output/exit behavior

## Negative tests

Programs that must fail with a specific diagnostic family.

Cover boundaries, not just examples.

For generic/typing features, test:

- exact match
- widening
- invalid narrowing
- nullable
- wrong arity
- nested type arguments
- trait success
- trait failure
- `Any`
- package qualification

For runtime behavior, test:

- deterministic failure
- Exception catchability
- mutation semantics
- copy/value semantics

---

# 21. Definition of "complete"

Do not interpret "complete language" to mean "contains every feature from major languages."

Solvik is complete when it can cleanly support substantial non-GUI programs with a stable semantic foundation.

The target includes:

- expressive static type system
- generics
- structural traits
- value types
- first-class functions
- closures
- algebraic data types
- pattern matching
- robust nullability
- exceptions
- useful collections
- practical packages/modules
- cross-package types
- practical standard library
- deterministic diagnostics
- testing
- multiple conforming implementations
- a path to self-hosting

The target explicitly does not require:

- classes
- inheritance
- GUI framework
- borrow checking
- lifetimes
- macro metaprogramming
- reflection-heavy runtime
- implicit dynamic typing
- complicated operator overloading
- multiple competing concurrency paradigms

Concurrency may be considered only after the language reaches semantic freeze unless it becomes necessary for a concrete standard-library design.

---

# 22. Behavior when design uncertainty appears

When a language-design decision is genuinely ambiguous:

1. inspect existing Solvik syntax and semantics
2. prefer consistency with current language shape
3. compare against the design principles above
4. choose the simplest rule that composes correctly
5. document the decision in `LANGUAGE.md`
6. encode it in tests
7. record Any important rationale in `PHASE_STATUS.md`

Do not stop merely because several reasonable designs exist.

Make a defensible decision and proceed.

Only mark a phase blocked when implementation cannot safely continue without information unavailable from the repository or when requirements are fundamentally contradictory.

---

# 23. Required end-of-phase report

At the end of every invocation, output a concise report containing:

```text
Phase:
Status:

Implemented:
- ...

Specification changes:
- ...

Tests added:
- ...

Validation:
- command -> result
- command -> result

Parity impact:
- Python:
- Go:
- Rust:

Known limitations:
- ...

Next phase:
- ...
```

Then stop.

Do not begin the next phase in the same invocation.

---

# 24. Immediate starting instruction

Begin by inspecting the current repository.

Determine whether Phase 0 and Phase 1 are already complete from the existing Python-first language-evolution work.

Do not redo completed work unnecessarily.

If Phase 1 is complete, the next implementation phase is:

```text
Phase 2 — First-class functions and closures
```

Execute exactly one phase.

Complete it fully.

Validate it.

Update `LANGUAGE.md`, tests, `PARITY.md`, and `PHASE_STATUS.md`.

Then stop at the phase boundary.
