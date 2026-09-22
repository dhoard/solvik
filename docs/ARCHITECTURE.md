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
  +--> recursive include resolution / AST expansion (per physical file)
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
ForStmt / ForInStmt
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

## Source Identity and Inclusion

A root compilation may use top-level `include` directives (docs/LANGUAGE_SPEC.md section 20). Each physical file is parsed independently, then include resolution recursively splices the resolved top-level items of every file into one syntax AST in depth-first, left-to-right order. Source text is never concatenated and reparsed, because that would break lexer boundaries, semicolon insertion, source spans, file names, and instrumentation.

A physical file may declare a `module` namespace and may bind file-local module prefixes with `include P alias p`. `org.solvik.parser.FileScope` records the declaring file's module name and its visible prefix-to-module bindings, and every resolved top-level item carries the `FileScope` of the file that physically declared it (`IncludeResolutionResult.itemScopes()`). Prefixes are file-local and non-transitive; unaliased inclusion of a module-declaring file also makes that module's name a visible prefix. The implicit default module has no name and keeps the flat program scope. A qualified reference uses the `::` namespace separator (`prefix::Name`), represented by `org.solvik.ast.expression.NamespaceAccessExprNode`, which keeps namespace qualification distinct from `.` member access; a qualified type uses the optional prefix on `TypeRefNode`.

Every `SourceSpan` carries a compilation-local source id and every `SourceFile` carries the matching id. `org.solvik.source.SourceCatalog` maps those ids to physical files and is the only way a diagnostic recovers the file that supplied its span; it contains no Truffle types. The language layer keeps a parallel id-to-Truffle-`Source` map. Source id `0` is always the evaluated root; included files receive increasing ids in first canonical-load order.

Each lowered executable node records its own Truffle `Source` together with its offset and length, so a cross-file implicit `main` can mix statements from several files and still expose each statement's true location to diagnostics, instrumentation, and the debugger. A lowered node never derives its source from an enclosing root. Module resolution, include resolution, and file reads finish before semantic analysis and lowering; there is no runtime module or include node and no runtime file I/O.

## Type System

Define an explicit compiler type model rather than relying on Java classes as the type system.

Conceptually:

```text
Type
├── AnyType
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

`AnyType` is the compiler representation of the sole non-null root type `Any`; there is no
`Object` type in the model. Built-in numeric/class hierarchy metadata belongs in the type
environment.

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

SolvikAny
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

## Equality and Reference Identity

Semantic equality (`==`/`!=`) and reference identity (`===`/`!==`) are separate in syntax, typing,
and lowering.

Compiler:

- semantic equality keeps the existing one-direction assignment-compatibility rule;
- identity first applies that rule, then validates the exact identity-bearing domain from a single
  `IdentityDomain` built from the program's declared class and interface types plus the mutable
  built-in collections; the domain must not be inferred from Java implementation class names in
  several visitors;
- `Any`, scalars, `Unit`, enums, `Regex`, `RegexMatch`, and unbounded type parameters are
  rejected for identity even when assignment-compatible, using `SOLV-TYPE-039`;
- null refinement treats `x === null`/`x !== null` exactly like `x == null`/`x != null`, and only
  when identity typing succeeded.

The universal `equals(other: Any?): Boolean` member is resolved like `Any.toString`: a call resolves
before any per-type member table, and the declaration is validated against the fixed root signature
(`override`, one `Any?` parameter, `Boolean` return). The analyzed facts are recorded in
`CheckedProgram` (a set of built-in-equality call sites) so lowering stays a pure translation of
checked facts.

Runtime:

- one shared semantic-equality service (`SolvikValues.equal`) serves operator equality, explicit
  `equals` calls, recursive enum payloads, `Set` construction/`add`/`contains`/`remove`, `Map`
  construction/`put`/`get`/`containsKey`/`remove`, and constant `switch` matching;
- the left operand is the dynamic receiver; a user class dispatches its effective `equals` override
  exactly once and only after the null precheck, and an absent override falls back to reference
  identity;
- built-in scalars and `Unit` use fixed IEEE/value rules, collections use reference identity, and
  `Regex`/`RegexMatch` use source text and an immutable snapshot, so engine caching and interning
  never become observable;
- specialization must not change semantics when a call site later receives another runtime kind
  through `Any`;
- guest exceptions propagate normally and no guest comparison delegates to arbitrary Java `equals`.

Lowering uses a dedicated reference-identity node (`SolvikIdentityNode`) that compares only guest
references, never `equals`; `!=` and `!==` are logical negations of one evaluation of their positive
operation. An explicit `equals` call lowers through the same semantic-equality service, with a safe
call on a null receiver returning `null` without evaluating the argument.

Solvik now specifies a `hashCode` contract (docs/LANGUAGE_SPEC.md section 3): `Any.hashCode()` is a
universal member, paired with `Any.equals` by a compile-time rule, and `SolvikHash` mirrors the
structure of `SolvikValues` so a hash can never drift from equality for any value whose hash the
runtime computes itself.

Whether a hash index is safe depends on who is trusted to keep the invariant
`equal(a, b) implies hash(a) == hash(b)`, and confirming a hit with `equals` is **not** sufficient
protection. Skipping a candidate bucket is itself a membership decision, and an element that is
skipped never reaches the equality scan that would have accepted it. This was measured, not assumed:
indexing a `Set` by hash bucket while still confirming every surviving candidate with
`SolvikValues.equal` returns `false` for `Set.contains` on a key whose `hashCode` violates the
invariant, where the unindexed scan returns `true`. Requiring `override hashCode` alongside
`override equals` makes the pair exist; it does not make the hash agree with equality, because a
compiler cannot prove anything about a method body.

So the index may be applied only where the invariant is guaranteed by construction rather than by
user discipline: the fixed rules in `SolvikHash`, which cover `null`, the scalars, `Unit`, enum
values, `Regex`, and `RegexMatch`, and read exactly the fields the matching equality rule reads.
Keys whose effective `hashCode` is a user override cannot be indexed without accepting that a
user-visible wrong answer is reachable from code the compiler must accept. If indexed keys and
user-keyed collections ever have to coexist in one collection, the collection must either keep a
full scan for its un-indexable keys or the spec must make a violating `hashCode` a runtime error.

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

## Expression-Oriented Constructs

Block, `if`, and `switch` expressions are additive value-producing forms; assignments remain
statements and functions still require an explicit `return` (docs/LANGUAGE_SPEC.md section 21).
Their analysis and lowering responsibilities are located as follows.

- **Tail-result identification.** The parser/AST builder identifies the terminal expression of a
  value-required block or case body structurally, from the trailing expression-form item, in
  `org.solvik.parser.SolvikAstBuilder` (`valueBlock`, `valueTail`, `valueCaseBody`). It never inspects
  whether a terminal semicolon was explicit or synthesized, so both share one AST shape. The optional
  tail lives on `org.solvik.ast.statement.BlockNode#tail`; expression forms are
  `BlockExprNode`, `IfExprNode`, and `SwitchExprNode`.
- **Control-flow completion analysis.** `SolvikSemanticAnalyzer` computes an explicit compile-time
  `Flow` summary that distinguishes a normal completion with a result from one without a result and
  records `return`/`break`/`continue` paths. `flowOfValueBlock`, `flowOfExpression`,
  `flowOfStatementSequence`, and `completionOf` combine it across a sequence, an `if`, and `switch`
  cases. Abrupt paths are represented by control flow and never by a fabricated value.
- **Shared result-type joining.** `org.solvik.type.TypeJoin` is the single declared-hierarchy join
  used by `match`, block, `if`, and `switch` result typing, so their results cannot drift. It returns
  no join for branches whose only shared supertypes are incomparable.
- **Expression-`switch` totality checking.** `checkSwitchExpr` requires exactly one last `default`
  and rejects a case body that can complete normally without a tail result; regex and constant label
  checking is shared with the statement form through `checkSwitchCases`. Exhaustiveness for a closed
  variant set remains `match`'s responsibility.
- **Typed result metadata.** The result type of every value-producing construct is recorded in the
  identity map exposed by `org.solvik.semantic.CheckedProgram#typeOf`, exactly like any other
  expression, and lowering reads it without re-checking.
- **Lowering and runtime boundaries.** Lowering runs only after all diagnostics succeed. It emits
  `SolvikBlockExprNode` and `SolvikIfExprNode`; an expression `switch` lowers to a stored scrutinee
  followed by an ordered `if`/`else` expression chain so the scrutinee is evaluated exactly once and
  only the selected body executes. No runtime node recomputes branch typing, repairs a missing value,
  or evaluates a condition twice.

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
