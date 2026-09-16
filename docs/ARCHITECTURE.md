# Solvik Compiler and Runtime Architecture

## Purpose

Solvik is implemented by converting a local fork of GraalVM SimpleLanguage in place. The inherited repository supplies proven Truffle integration, execution specialization, interop, tooling hooks, and build structure. Solvik replaces the guest-language syntax, static semantics, object/type model, language registration, launcher identity, examples, and tests.

This is infrastructure reuse, not language compatibility. The finished repository must not expose a SimpleLanguage parser, language id, MIME type, file extension, launcher, compatibility option, or dynamic semantics.

## Required Pipeline

Do not permanently parse directly into executable Truffle nodes.

Use:

```text
Source
  |
  v
Lexer
  |
  v
Semicolon-Inserting Token Stream
  |
  v
ANTLR Parser
  |
  v
Solvik Syntax AST
  |
  +--> symbol collection
  +--> name resolution
  +--> type resolution
  +--> static type checking
  +--> nullability / flow analysis
  +--> inheritance validation
  +--> interface conformance
  +--> delegation resolution
  +--> exhaustiveness analysis
  |
  v
Typed / Lowered Solvik Representation
  |
  v
Truffle AST
  |
  v
GraalVM execution/JIT
```

A later optimization may lower the same typed representation to Truffle Bytecode DSL. The Truffle AST backend is the only initial Solvik backend. Inherited Bytecode DSL code may remain temporarily when required to keep a phase buildable, but it must not receive a Solvik parser or lowering path and must not remain as an exposed SimpleLanguage mode.

## Package Direction

New compiler-front-end code uses the `org.solvik` namespace and the following conceptual separation:

```text
org.solvik
├── parser
│   ├── grammar
│   ├── lexer support
│   └── semicolon insertion
├── ast
│   ├── declaration
│   ├── expression
│   ├── statement
│   └── pattern
├── type
│   ├── Type
│   ├── ClassType
│   ├── InterfaceType
│   ├── NullableType
│   ├── TypeParameter
│   └── FunctionType
├── semantic
│   ├── SymbolTable
│   ├── Resolver
│   ├── TypeChecker
│   ├── FlowAnalyzer
│   ├── InheritanceValidator
│   ├── DelegationResolver
│   └── ExhaustivenessChecker
├── lowering
├── truffle
│   ├── nodes
│   ├── runtime
│   ├── object
│   └── builtin
└── launcher
```

All new compiler-front-end code must use `org.solvik` packages. Existing `com.oracle.truffle.sl` implementation classes may be renamed incrementally when doing so does not destabilize the active phase. A temporary Java package name is not permission to preserve SimpleLanguage behavior.

## AST Policy

The syntax AST represents source semantics, not runtime optimization.

Every AST node must retain source span information sufficient for high-quality diagnostics.

Examples:

```text
ClassDecl
InterfaceDecl
FunctionDecl
VariableDecl
BlockStmt
IfStmt
WhileStmt
ReturnStmt
CallExpr
BinaryExpr
MemberExpr
MatchExpr
SwitchExpr/Stmt
RegexPattern
```

Executable Truffle nodes are a separate representation.

The parser returns syntax AST plus diagnostics. Semantic passes may annotate or map syntax nodes but must not mutate them into executable nodes. Lowering runs only after all error diagnostics have been resolved; a program with compile-time errors must never produce an executable call target.

## Type System

Define an explicit compiler type model rather than relying on Java classes as the type system.

Conceptually:

```text
Type
├── AnyType
├── ObjectType
├── NothingType
├── UnitType
├── NullType
├── ClassType
├── InterfaceType
├── EnumType
├── NullableType
├── TypeParameterType
└── FunctionType
```

Built-in numeric/class hierarchy metadata belongs in the type environment.

Do not use reflection over JVM classes as the primary source of Solvik subtype relationships.

## Classes

Create explicit compile-time class symbols/descriptors.

Conceptual compile-time structure:

```text
ClassSymbol
- name
- modifiers
- type parameters
- superclass
- interfaces
- fields
- constructors
- methods
- delegates
```

Runtime class metadata is separate from compile-time symbols and contains only execution data.

## Objects and Truffle Shape

SimpleLanguage's dynamic objects permit arbitrary member insertion/removal. Solvik objects have only statically declared members.

It is acceptable and desirable to keep Truffle `Shape`/`DynamicObject` infrastructure for efficient storage and inline caches, but Solvik controls object layout.

Conceptually:

```text
SolvikClass
- class identity
- superclass metadata
- method table
- field metadata
- Truffle instance shape

SolvikObject
- runtime class reference
- shape-backed instance storage
```

Arbitrary undeclared member creation must not be exposed in normal Solvik semantics.

## Methods and Dispatch

Initial implementation:

- single class inheritance;
- explicit `override`;
- classes final by default;
- methods final by default unless explicitly `open`;
- multiple interfaces;
- interface default methods;
- delegation.

Use Truffle call targets and inline caches where possible.

Do not prematurely build a JVM-like vtable if Truffle call-site specialization provides a simpler implementation.

## Delegation

Delegation is resolved statically.

For:

```solvik
class Service implements Logger {
    delegate val logger: Logger
}
```

semantic analysis must determine whether `logger` satisfies missing `Logger` members.

Precedence:

1. explicit class method;
2. inherited valid implementation;
3. unambiguous delegated implementation;
4. interface default, according to rules defined by the type checker.

Any ambiguity must fail compilation.

Lowering may synthesize forwarding methods/nodes or dispatch directly through delegate metadata. This is a backend choice; source semantics must not depend on it.

## Nullability and Flow Analysis

Represent `T?` explicitly in the compiler type system.

Implement flow narrowing incrementally, starting with:

```solvik
if (x != null) {
    // x: T
}
```

Then add `is` narrowing.

Do not let runtime null checks substitute for compile-time enforcement.

## Semicolon Insertion

Newlines cannot simply be discarded by the lexer before semicolon insertion.

Implement a token stage that observes line boundaries and significant tokens, emitting a synthetic `SEMI` token when the lexical rule requires it.

Explicit `;` and synthetic semicolons use the same parser token type.

Raw-string internal newlines are part of a single token and must never participate in semicolon insertion.

## Raw Strings

A custom lexer helper/action is permitted for arbitrary counted `#` delimiters.

Required lexical behavior:

```text
r"..."
r#"..."#
r##"..."##
...
```

The opening delimiter determines the exact closing delimiter.

Unterminated raw strings must produce a precise lexical diagnostic containing the source location and expected delimiter shape.

## Regex

`Regex` is a built-in Solvik class/type.

Do not compile regex patterns repeatedly inside hot `switch` executions. Constant regex patterns must be compiled once per source constant and cached.

Regex engine selection is an implementation decision. Keep it behind Solvik's `Regex` abstraction so it can change later without changing language syntax.

## switch and match

`switch`:
- no implicit fallthrough;
- supports ordinary constant cases;
- supports Regex pattern cases;
- supports `default`.

`match`:
- pattern-oriented;
- expression-capable;
- supports enum/sealed destructuring;
- statically exhaustive for every known enum or sealed variant set.

Do not conflate `switch` and `match` internally merely because lowering may share code.

## Generics

Implement static generics before sophisticated runtime reification.

The initial runtime representation uses erasure while preserving complete compile-time generic checks.

Avoid adding variance syntax until a concrete need exists and semantics are specified.

## Truffle Backend

Retain and adapt:

- `TruffleLanguage` integration;
- context/environment architecture;
- instrumentation tags;
- call targets;
- inline caching patterns;
- interop where useful;
- primitive specializations;
- native-image-capable build structure if practical.

Replace or remove:

- SimpleLanguage grammar;
- dynamic typing assumptions;
- dynamic object semantics;
- parser-directly-to-runtime-node architecture;
- SimpleLanguage naming/launcher over time.

No retained component may require accepting SimpleLanguage programs. When a Solvik replacement is covered by tests, remove its superseded SimpleLanguage production path and obsolete tests in the same phase.

## AST First, Bytecode Later

Initially target Truffle AST execution only. Do not add a Solvik Bytecode DSL backend, backend-selection flag, or duplicated parser path during the initial implementation.

Once the language semantics and typed IR are stable, evaluate a second lowering:

```text
Typed Solvik IR
   ├──> Truffle AST
   └──> Truffle Bytecode DSL
```

The type checker and front end must be backend-independent.
