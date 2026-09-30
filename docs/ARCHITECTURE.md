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

`FunctionType` is the only structural type in the model and the only one whose assignability is not
a walk of the nominal hierarchy: parameters are contravariant and the result covariant, equal
function types are canonicalized to one instance so identity-based compiler caches hold, and `Any`
is the supertype of every non-null function type. Structural comparison is confined to this class;
no other type may compare structurally, which is what keeps user classes and interfaces nominal.

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
- static storage cells and static method handles
- Truffle instance shape

SolvikAny
- runtime class reference
- shape-backed instance storage
```

Arbitrary undeclared member creation must not be exposed in normal Solvik semantics.

Static property storage lives on the declaring `SolvikClass`, not on any object: one
`SolvikStaticCell` per declared `static` property, holding a boxed value exactly as an instance
property array does. Lowering resolves each reference to its cell and embeds the cell in the read or
write node, so guest code performs no name lookup. Static members are kept in maps separate from the
virtual method table precisely because they are not inherited and must never be reachable by virtual
dispatch or delegation (docs/LANGUAGE_SPEC.md section 7).

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

A `static` method is lowered with no receiver slot and bound during lowering to a dedicated static-call
node that carries its declaring class. The class name in `Counter.reset()` is a compile-time receiver
only and contributes no executed node. Each class that has static initialization gets one `<clinit>` root
installed on its runtime class; a static property read or write, a static method call, and object
construction are active uses that run the installed `<clinit>` on the class's first use — after the
superclass chain — and afterwards guard on one boolean field, so an unused class is never initialized and
the result never depends on class declaration order.

Do not prematurely build a JVM-like vtable if Truffle call-site specialization provides a simpler implementation.

## Function Values and Indirect Calls

LANGUAGE_SPEC.md section 6 defines first-class function values. Two distinct things must stay
distinct in the implementation:

- a **direct callable symbol**: a `FunctionSymbol` resolved by static analysis and lowered to a
  fixed call target. `sum(1, 2)` keeps this path. It must never be lowered into "construct a
  function value, then invoke it", because that would trade a statically resolved call for an
  allocation and an indirect call.
- a **function value**: a guest-visible immutable reference value with its own identity, produced
  only where the language requires a value. It is a runtime object, never a compile-time symbol
  wearing a different name, and it never carries per-execution state in a compilation-final node
  field.

Compile-time facts, all recorded before lowering so lowering redoes no analysis:

| Fact | Recorded by |
|---|---|
| the selected `FunctionSymbol` of a named function reference | resolution |
| the instantiated `FunctionType` and type-parameter substitution of a generic reference | resolution |
| the resolved method and receiver mode of a bound method reference (`virtual`, `super`, safe) | member resolution |
| the ordered capture descriptors, their resolved symbols and static types | capture analysis |
| whether an anonymous function captures nothing | capture analysis |
| the function type of an indirect call site | type analysis |

**Capture analysis** runs at the anonymous-function expression, before its parameter and body scope
are entered, and preserves written order. It rejects a duplicate item, a capture/parameter name
collision, an item that is not an eligible immutable local, parameter, function value, or `this`, a
`var` item, and a self-reference to the binding being initialized. Body checking then resolves only
parameters, body locals, written captures, and top-level/module declarations; an eligible enclosing
binding reached without being listed is `SEM_UNLISTED_CAPTURE`, and after an invalid capture item is
reported a poisoned placeholder must prevent the same root cause from re-reporting as an unknown
name or an omitted capture.

**Runtime representation.** A guest function value is a sealed family over a stable call target
plus an immutable environment: a canonical named value (one instance per declared function per
context), an anonymous value (a fresh instance per evaluation, sharing one lowered root), and a
bound value (a fresh instance per evaluation, retaining one already-evaluated receiver). Equality
and hashing are reference identity through the shared equality/hash services, display is the fixed
`func`, and the value reports executable at the interop boundary. A declared callable's existing
execution handle may back these values but must not be exposed as one, and must not conflate the
one declared target with the several identities anonymous and bound values create. Named-value
canonicalization is context-local: two contexts never share a function value or captured state.

**Lowering.** A direct call lowers exactly as before. An indirect call gets a dedicated invocation
node that evaluates the callee once, evaluates arguments left to right, caches a stable target with
`DirectCallNode` while the site is monomorphic, falls back to `IndirectCallNode` when it is not, and
supplies any hidden environment or receiver argument according to the value's kind. Captures reach a
closure body through hidden runtime arguments or an equivalent immutable runtime object, never by
mutating a shared AST node. A safe bound reference uses a conditional node that allocates nothing on
the null path. Primitive parameters, captures, and results keep their frame kinds: crossing a
function-value boundary must not box a primitive.

**Instrumentation.** An anonymous root carries a source section derived from its own expression, so
a stack trace names the physical file and the anonymous site. Indirect calls carry the same call
instrumentation tags as direct calls. Solvik nodes carry no instrumentation tags at all today: no
node reports itself instrumentable, so the source-section and execution event queries of an
instrument observe nothing in a Solvik program, which makes that equality observable only as the
equality of the call stacks a direct and an indirect call of one callable report.
`SolvikCallStackTest` asserts that equality from a real multi-file program. That is a consequence of
how indirect calls are lowered rather than a tag-level oracle: there is no tag to compare, and the
quoted equality becomes testable at the tag level only when some call path acquires tags, at which
point the same equality must hold under them. A debugger-facing frame label may differ from the
guest value's display text, which is always `func`.

**Closed world.** No reflective signature discovery, dynamic class generation, JVM lambda, or
`MethodHandle` lookup from a guest type. New runtime classes must be reachable through ordinary
compiled code, so native image needs no reflective registration for them.

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

## Error Handling

Unchecked exceptions (`throw`, `try`, typed `catch`, `finally`) and the `Result` value model are two
independent mechanisms with distinct runtime representations (docs/LANGUAGE_SPEC.md section 22). The
exception mechanism is lowered entirely through the existing pipeline; it adds no second backend and
no runtime re-analysis.

- **Grammar and AST.** `throw`, `try`, `catch`, and `finally` are keywords in `Solvik.g4` and produce
  the dedicated AST statements `ThrowStmtNode` and `TryStmtNode` (with a `CatchClause` record),
  identified by `AstKind.THROW_STMT` and `AstKind.TRY_STMT`. They are ordinary syntax-tree nodes
  lowered like every other construct; no executable node is produced before semantic analysis.
- **Compile-time validation.** `SolvikSemanticAnalyzer` rejects a `throw` whose operand is not
  assignable to a guest exception type (`SEM_THROW_NON_EXCEPTION`), validates each `catch` handler
  type and binding scope, reports a handler rendered unreachable by an earlier subtype (`SEM_UNREACHABLE_CATCH`),
  requires at least one `catch` or `finally` (`SEM_TRY_NEEDS_HANDLER`), and rejects a non-exception
  handler type (`SEM_INVALID_CATCH_TYPE`). The analyzer builds the guest exception graph
  (`exceptionParents`, `exceptionClassNames`) from each class's declared superclass so both throw
  validation and handler reachability are decided before lowering.
- **Built-in bases.** `Exception`, `RuntimeException`, and `ApplicationException` live as compile-time
  `ClassType` singletons in `org.solvik.type.ExceptionBases`, registered in `TypeEnvironment`. They
  have no source declaration and no `ClassSymbol`; the semantic layer recognizes them by name
  (`isExceptionBaseType`) without importing Truffle runtime classes, preserving the layering rule that
  the semantic layer does not depend on the runtime.
- **Synthesized message.** Every guest exception type carries an optional message with Java's
  empty/message construction pattern (`docs/LANGUAGE_SPEC.md` section 22.1). The message is not a
  declared constructor parameter: the analyzer accepts one optional trailing `String?` argument on a
  non-generic exception construction, and lowering splits it from the declared arguments and stores it
  into a compiler-synthesized shape slot (`SolvikClass.MESSAGE_FIELD`) appended to every exception
  runtime class by `createRuntimeClass`. Because that slot has no `PropertySymbol`, `e.message` is not a
  member and resolves to `RESOL_UNKNOWN_MEMBER`: privacy is achieved by construction rather than by a
  visibility modifier the language does not have. The value is observable only through the synthesized
  `getMessage(): String?`, which the analyzer resolves ahead of the declared method table and lowering
  emits as a direct property read of the shared `SolvikClass.MESSAGE_KEY`, so it works on a base-type
  receiver and with `?.`. The names `message` and `getMessage` are reserved on exception classes
  (`SEM_RESERVED_MEMBER`) so no user member can collide, while remaining ordinary on other classes.
  `buildExceptionGraph` runs before class-member collection (from superclass names alone) so the
  reservation is known while members are collected.
- **Handler matching.** `CheckedProgram#exceptionsCaughtBy` computes each handler's transitive matched
  class-name set by reverse reachability over the declared-superclass graph, so a handler written on a
  base type also catches subclasses whose chain passes through a built-in base that has no runtime
  superclass link. Lowering embeds that precomputed set into each `SolvikTryNode.CatchHandler`, so
  runtime dispatch performs a set membership test over class names and never re-walks the hierarchy.
  Catch bindings get a frame slot allocated before the handler body is lowered; dispatch writes the
  caught value into that slot and the body reads it as a normal local.
- **Runtime representation.** A guest throw is signalled by `SolvikGuestException` (a
  `ControlFlowException`) carrying the thrown value, so it unwinds across call targets and is
  distinguishable from an internal failure. `SolvikTryNode` catches `SolvikGuestException` plus every
  abrupt exit a body can take — `SolvikReturnException`, `SolvikBreakException`,
  `SolvikContinueException`, and `SolvikPropagationException` — so `finally` runs on every exit path;
  it leaves `AbstractTruffleException` (internal faults) untouched and consults handlers in source
  order. The in-flight transition is captured as a `PendingExit` value and carried across the finally
  clause, which resolves the single escaping transition with Java's last-abrupt-completion-wins rule:
  a finally clause that completes normally leaves the pending transition untouched, while a finally
  clause that completes abruptly (throw, `return`, `break`, `continue`, or `?`) replaces it and the
  replaced value is discarded outright. There is deliberately no suppressed-exception chain on
  `SolvikGuestException`: suppression is a try-with-resources / checked-exception feature that Solvik
  does not have, so a discarded throw leaves no trace. `exit` raises a host exit signal that is not a
  `ControlFlowException` and so is never captured here. The handler set lookup sits behind a
  `@TruffleBoundary` so its JDK collection call is never runtime-compiled (native-image blocklists the
  collection's `containsKey` for compiled code).
- **Return-path reachability.** `SolvikSemanticAnalyzer.alwaysReturns` models Java's reachability for
  `try` statements (docs/LANGUAGE_SPEC.md section 22.3), because the replace-on-abort rule makes the
  `finally` clause decisive for whether the statement can still fall through. A `finally` block that
  always transfers control guarantees it for the whole statement; otherwise the statement guarantees it
  only when the `try` block and every `catch` body each always transfer control. This is what lets a
  `return` inside a `try` satisfy the value-returning-function rule (`TYPE_MISSING_RETURN_PATH`) exactly
  as `javac` does, while a handler that can complete normally still forces a follow-up `return`.
- **Program boundary.** `SolvikEvalRootNode.execute` is the single outermost Solvik boundary. It calls
  the implicit `main` and converts a `SolvikGuestException` that reaches it into a guest-visible
  `SolvikException` (`AbstractTruffleException`) naming the thrown class and, when one is present, its
  message, so the host reports an ordinary guest failure and a non-zero exit rather than an internal
  error. The message is read at this boundary through an uncached `GetNode` behind a
  `@TruffleBoundary`, keeping the object-store access out of the hot throw/unwind path. Ordinary function root
  nodes deliberately do not perform this conversion, so an enclosing handler above a throw site still
  receives the value during unwinding.

## Result Operations and Propagation

The `Result` value model is the second, independent error-handling mechanism (docs/LANGUAGE_SPEC.md
section 23). `Result` is not a built-in type: a two-parameter enum named `Result` is recognized
structurally (`isResultType` — a `ParameterizedType` whose base is named `Result` with two arguments),
so no prelude or built-in type table is required and the mechanism composes with ordinary
user-declared enums and `match`. Its operations and the postfix `?` propagation operator are lowered
through the existing pipeline with no second backend and no runtime re-analysis.

- **Variant identity is positional.** The success and error payloads are the first and second
  variants, so `Ok`/`Err` naming is not load-bearing. The compiler resolves the two variants once
  (`program.enumSymbol("Result")`) and lowering captures their runtime identities, so a Result
  operation performs a reference comparison against the receiver's variant with no name lookup or
  table dispatch at run time.
- **Operation typing.** `SolvikSemanticAnalyzer.resolveMethodReturnType` routes a `Result`-typed
  receiver to `checkResultMethodCall`, which types `isOk`/`isErr` as `Boolean`, `unwrap` as the
  success argument `T`, `unwrapErr` as the error argument `E`, `expect(String)` as `T`, and `ignore`
  as `Unit`, validating arity and argument types through the shared `checkArguments` helper. Unknown
  members are `RESOL_UNKNOWN_MEMBER`. `resolveMemberRead` rejects a bare member read of an operation
  name as `TYPE_FUNCTION_AS_VALUE` and any other name as `RESOL_UNKNOWN_MEMBER`, mirroring how the
  built-in `Regex`/`RegexMatch`/collection members are handled.
- **Lowering and runtime.** `SolvikLowering` detects a `Result` receiver before the built-in collection
  dispatch and emits a single `SolvikResultOperationNode` carrying the operation, the lowered receiver,
  the lowered `expect` message (only for `expect`), the safe-call flag, and the two captured runtime
  variant identities. A safe call on a null receiver short-circuits to null before its message
  argument is evaluated.
- **Wrong-variant faults.** `unwrap` on the error variant, `unwrapErr` on the success variant, and
  `expect` on the error variant raise `SolvikException.unwrapFailed`/`expectFailed`, the same
  `AbstractTruffleException` fault class as an arithmetic or cast error, so the host reports an
  ordinary guest failure rather than an internal error. Both factories are `@TruffleBoundary` methods
  that build their message (the latter rendering the carried error through `SolvikDisplay`), so the
  runtime-compiled operation node performs no string formatting on the hot path.
- **Propagation.** The postfix `expression?` is lowered to `SolvikPropagationNode`, which on the
  success variant yields the payload and otherwise throws `SolvikPropagationException` carrying the
  error. `SolvikRootNode.execute` catches that signal only for functions and returns the carried value
  as the function's own `Result`, so propagation composes across nested calls without terminating the
  program. The operand must be a `Result` (`SEM_RESULT_PROPAGATION_INVALID_OPERAND`), an enclosing
  `Result`-returning function must exist (`SEM_RESULT_PROPAGATION_NO_BOUNDARY`), and the unwrapped
  success and propagated error types must be assignable to that boundary (`SEM_RESULT_PROPAGATION_TYPE_MISMATCH`).
- **Must-consume rule.** A `Result` value used as a standalone statement is `SEM_UNUSED_RESULT`, so an
  error cannot be silently discarded. `?`, `match`, and any non-`Result`-yielding operation consume a
  value; `ignore()` yields `Unit` and is the explicit discard. This is enforced in
  `SolvikSemanticAnalyzer.checkExprStmt` against the recorded expression type, before lowering.

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
