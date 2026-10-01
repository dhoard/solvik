# First-Class Functions — Normative Design and Implementation Plan

Status: normative design record; **implemented**. Phases 1–7 are delivered and gate-green, and the
outstanding evidence this plan owed — the section 7.7 benchmark family, the polymorphic call site and the
parser nesting cases from section 9, and the least-common-function-supertype join rule (this plan's
section 4, the specification's section 6) — is recorded in the closing sections of
`docs/FIRST-CLASS-FUNCTIONS-PLAN.md`.

This document defines the required language design and the implementation plan for adding
first-class function values to Solvik. It is intentionally complete enough to drive specification,
compiler, runtime, examples, regression tests, and TCK work without making semantic choices during
implementation.

The authority order remains:

1. `AGENTS.md`;
2. `docs/LANGUAGE_SPEC.md`;
3. `docs/ARCHITECTURE.md`;
4. this plan.

The language specification no longer says that first-class function values do not exist: the first
implementation step revised `docs/LANGUAGE_SPEC.md` and declared a new specification revision, so the
rules below are now the specification's own text rather than a proposal against it. Where this plan and
`docs/LANGUAGE_SPEC.md` differ, the specification governs, and the delivered record of what each section
became is `docs/FIRST-CLASS-FUNCTIONS-PLAN.md`. If future work exposes a conflict with `AGENTS.md`, an
unresolved rule in this plan, or another higher-authority rule, work must stop and the conflict must be
resolved in the specification before code proceeds.

`must`, `must not`, `should`, and `may` in the language-design sections below have their normative
meanings. Later sections use checklists to describe implementation work and acceptance evidence.

## 1. Goals

The feature must deliver these six vertical capabilities in order:

1. function types;
2. named top-level functions as values;
3. anonymous non-capturing functions;
4. explicit immutable closure capture;
5. generic function values;
6. bound method references.

The design must:

- preserve Solvik's strong static typing;
- keep user-defined classes and interfaces nominally typed while treating function types as a
  language-defined structural type family;
- retain explicit parameter types and existing function return rules;
- preserve direct-call optimization for calls whose target is statically known;
- avoid boxing Solvik primitives merely because they cross a function-value call boundary;
- preserve source locations, instrumentation, guest stack traces, and native-image support;
- give every accepted function-value program the same observable semantics on the JVM and native
  distributions; and
- provide positive and negative conformance coverage for every normative rule.

The feature must not introduce a second execution backend or restore any SimpleLanguage behavior.

## 2. Non-goals

The first implementation does not include:

- mutable local capture;
- polymorphic values or higher-rank function types;
- anonymous generic function declarations;
- local named function declarations;
- partial application or currying;
- default or variadic parameters;
- function overloading;
- constructors or enum variants as function values;
- static method references as values;
- an unbound instance-method form such as `Type.method`;
- trailing-lambda syntax;
- receiver function types;
- `suspend`, `async`, thread, or fiber effects;
- serialization of function values;
- user-defined operator overloading for function values;
- reflection over a function value's parameter or result types; or
- runtime `is` tests or `as` casts whose target is a function type.

Those features require separate specification work. The runtime representation must not expose an
accidental behavior that commits a later design to any of them.

## 3. Audited repository baseline

The existing repository already has useful infrastructure that must be extended rather than
replaced:

- `org.solvik.type.FunctionType` records parameter and return types but explicitly exists only to
  type statically resolved calls. It is not currently legal as a source type.
- `FunctionSymbol.functionType()` creates a function type for every declared callable.
- `SolvikSemanticAnalyzer` rejects a top-level function or method used as a value with
  `TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`).
- `CallExprNode` already permits an arbitrary expression as its callee, although semantic analysis
  currently accepts only known callable forms.
- `SolvikFunction` holds a declared callable's `RootCallTarget`, but it is explicitly an internal
  runtime object rather than a guest value.
- direct top-level, static, instance, interface, delegated, constructor, and built-in calls are
  already resolved before lowering.
- `FunctionType` is listed in `docs/ARCHITECTURE.md`'s compiler type model, so making it a real source
  type fits the required compiler architecture.
- the TCK currently contains a normative rejection asserting that a bare callable member is
  `SOLV-TYPE-014`. The new specification revision must retire or replace that requirement rather
  than silently retaining an oracle that contradicts first-class method references.

Useful infrastructure must remain intact. In particular, a direct call such as `sum(1, 2)` must not
be needlessly lowered into “construct a function object, then invoke it.” Function values add an
indirect call path; they do not replace statically resolved direct calls.

## 4. Normative language design

### 4.1 Function type syntax

A function type is written with `func`, a parenthesized comma-separated list of parameter types, and
an optional return type:

```solvik
func()
func(Integer): String
func(Integer, String): Boolean
func(): Unit
```

Omitting the return type means `Unit`, exactly as it does for a function declaration. Therefore,
`func()` and `func(): Unit` name the same type.

Parameter names do not appear in a function type. Parameter names belong to declarations and have
no role in function-type identity or assignability.

Function types may appear anywhere another non-deferred type may appear, including:

- local, parameter, return, property, and static-property types;
- generic type arguments;
- nullable types; and
- the parameter or return position of another function type.

Examples:

```solvik
val formatter: func(Integer): String = format
val optional: (func(Integer): String)? = null
val factory: func(): func(Integer): String = makeFormatter
val callbacks: List<func(String): Unit> = List()
```

Parentheses are required when nullability applies to the function value itself:

```solvik
(func(Integer): String)?  // nullable function value
func(Integer): String?    // non-null function returning String?
```

The grammar must preserve this distinction structurally; semantic analysis must not infer it from
source text.

### 4.2 Structural identity and assignability

Function types are structural language types. Two function types are identical when they have the
same number of parameters, corresponding parameter types are identical, and their return types are
identical. The declarations that produced values of those types do not affect type identity.

Function-type assignability is contravariant in parameters and covariant in the return type. Given
source type `func(S1, ..., Sn): SR` and target type `func(T1, ..., Tn): TR`, the source is assignable
to the target exactly when:

1. both types have the same arity;
2. for every parameter position `i`, `Ti` is assignable to `Si`; and
3. `SR` is assignable to `TR`.

For example, given `open class Animal` and `class Dog extends Animal`, a value of type
`func(Animal): Dog` is assignable to `func(Dog): Animal`.

Numeric widening is not a subtype relation and must not be applied recursively inside function-type
assignability. A function accepting `Long` is not assignable to a function type accepting `Integer`
merely because an `Integer` argument may widen at an ordinary conversion site. Arguments supplied
when a function value is invoked still receive the ordinary call-site numeric widening rules.

`Nothing` follows the ordinary bottom-type rule. Nullability follows the existing `T`/`T?` rules.
Function types have `Any` as their non-null top type but do not become nominal classes or interfaces.

The shared type-join service must understand function types. For two same-arity function types, each
joined parameter is the more specific of the two corresponding parameter types when one is
assignable to the other, and the joined result is their existing nearest common result type. This is
the least common function supertype allowed by contravariant parameters and covariant results. If a
parameter pair is unrelated or the results have no unique join, no function-type join exists; the
ordinary join service may still select a shared nominal supertype such as `Any`. The implementation
must not introduce `Nothing`, a union, or an intersection merely to manufacture a function-type
join.

### 4.3 Function values and invocation

A function value is an immutable, non-null reference value that can be stored, passed, returned, and
invoked. A call expression may invoke any expression whose non-null static type is a function type:

```solvik
val operation: func(Integer): Integer = double
val result = operation(21)
```

The callee expression is evaluated exactly once before any argument. Arguments are then evaluated
exactly once from left to right. Arity is checked before argument-type compatibility, matching the
existing call diagnostic ordering. A nullable function value cannot be called without prior
refinement or another existing non-null mechanism.

Explicit generic call arguments are not permitted on an already-instantiated function value. A
function value is monomorphic, so `operation<Integer>(1)` is `TYPE_NOT_GENERIC`.

Invocation returns the declared result and propagates guest exceptions without wrapping or
translation. `return` inside a function body returns from that function body. It never returns from
the function that created a closure. `break` and `continue` cannot cross a function boundary.

### 4.4 Named top-level functions as values

A bare reference to a visible, non-generic top-level function produces a function value:

```solvik
func format(value: Integer): String {
    return value.toString()
}

val formatter: func(Integer): String = format
println(formatter(42))
```

Parentheses continue to distinguish invocation:

```solvik
format     // function value
format(42) // direct invocation
```

The same rule applies to module-qualified functions and predeclared functions:

```solvik
val render: func(Integer): String = text::render
val output: func(Any?): Unit = println
```

Name resolution keeps the existing lexical precedence. A visible local or parameter with the same
name shadows a top-level function. A call through such a variable invokes the variable when its type
is a function type and reports `TYPE_NOT_CALLABLE` otherwise. The absence of overloading guarantees
that a resolved top-level name identifies at most one function declaration.

Every reference evaluation to the same declared top-level function produces the same canonical
function-value identity. Module qualification does not create a second identity for the same
declaration. Contextual instantiations of one generic declaration at different function types also
share that declaration's canonical runtime identity; instantiation changes static typing, not the
underlying executable value.

### 4.5 Anonymous functions

An anonymous function is an expression with this syntax:

```solvik
func(value: Integer): Integer {
    return value * 2
}
```

Its parameters must have explicit types. Its return type follows the same rule as a named function:
omitting it declares `Unit`; a value-returning anonymous function must write its return type and must
return a compatible value on every normally completing path. Function bodies do not acquire an
implicit tail result.

Examples:

```solvik
val double: func(Integer): Integer = func(value: Integer): Integer {
    return value * 2
}

val consume: func(String) = func(value: String) {
    println(value)
}
```

An anonymous function creates a new function value every time evaluation reaches the expression.
Two evaluations are distinct even when the expression captures no values. Re-reading a local that
contains one anonymous function value preserves its identity.

An anonymous function introduces a function boundary and a lexical scope containing its parameters
and body locals. Its parameters follow the existing immutable-parameter rule. A declaration inside
its body may shadow an outer binding under the ordinary lexical-scope rules.

A bare anonymous function used as an expression statement remains illegal under
`SEM_VALUE_EXPRESSION_STATEMENT`, because creating and discarding a function value is not a call.

### 4.6 Explicit immutable closure capture

An anonymous function has no implicit access to local values from an enclosing function. Every such
dependency must appear in an explicit capture list between `func` and the parameter list:

```solvik
val factor = 3
val scale = func [factor](value: Integer): Integer {
    return value * factor
}
```

The capture list is part of the anonymous-function expression, but not part of its public function
type. The example's type is `func(Integer): Integer`: callers supply `value`, while the declaration
visibly binds `factor` into the function value.

A capture item is an identifier or `this`. It must resolve at the closure-creation site to one of:

- a `val` local declared in an enclosing function scope;
- an immutable parameter of an enclosing function;
- another function value held by an immutable binding; or
- `this` in an enclosing instance method or constructor.

The capture list uses source order as environment order. Duplicate capture items are
`RESOL_DUPLICATE_NAME`. A capture name and an anonymous-function parameter may not have the same
name; that is also `RESOL_DUPLICATE_NAME`. Capture aliases and arbitrary capture expressions are not
supported in this phase.

Each listed binding's value is captured when evaluation reaches the anonymous-function expression.
Capturing an object copies the reference, not the reachable object graph. Later mutation of that
object's `var` properties remains observable through the captured reference.

An outer local or parameter referenced by the body but omitted from the capture list is
`SEM_UNLISTED_CAPTURE`, reported on the body reference. This applies to `this` as well: a closure
body may use `this` only when `[this]` is written. Unknown names remain `RESOL_UNKNOWN_NAME`.
Top-level and module-qualified function declarations are globally resolved declarations rather than
local state and need no capture entry.

A closure must not list or otherwise capture a `var` local. A `var` named in a capture list is the
compile-time error `SEM_MUTABLE_CAPTURE`, reported on that capture item. Referencing the same outer
`var` without listing it remains `SEM_UNLISTED_CAPTURE` at the body reference; the compiler does not
silently convert it into a capture. This restriction avoids hidden heap cells and gives later
concurrency work an explicit shared-state boundary.

```solvik
var total = 0
val add = func [total](value: Integer) {
    total = total + value // declaration rejected at [total]
}
```

Mutable state may be shared explicitly through a captured immutable object reference:

```solvik
class Counter {
    var value: Integer = 0

    func increment() {
        this.value = this.value + 1
    }
}

val counter = Counter()
val increment = func [counter]() {
    counter.increment()
}
```

A non-capturing anonymous function omits the capture list. An empty capture list is invalid because
it communicates nothing; use `func(...)` instead of `func [](...)`.

Capture is transitive only through explicit values. A closure that captures another closure lists
that function-valued binding and stores the function value; it does not duplicate or flatten the
captured closure's environment. In nested closures, a name used in an inner capture list counts as a
use by the enclosing closure. Every intervening closure must therefore list and forward that value
explicitly.

Anonymous self-recursion through the binding being initialized is not supported. Listing that
binding in the capture list is an ordinary `TYPE_UNINITIALIZED_VARIABLE` because the value does not
exist when its initializer is evaluated. Recursion through named top-level functions continues to
work without a capture.

### 4.7 Generic function values

A generic function declaration does not itself produce a first-class polymorphic value. It must be
instantiated to one monomorphic function type at each value-reference site.

Instantiation is contextual:

```solvik
func identity<T>(value: T): T {
    return value
}

val integerIdentity: func(Integer): Integer = identity
val stringIdentity: func(String): String = identity
```

The expected function type supplies constraints for every declared type parameter. The compiler
must determine one complete substitution, apply it to the function's declared parameter and result
types, and then check ordinary function-type assignability. Inference first unifies occurrences in
the declared parameter types with the expected parameter types; result positions may confirm or
complete a unique substitution but must not select arbitrarily among several valid types. All type
parameters must be resolved.

A generic function reference with no expected function type is `TYPE_CANNOT_INFER`:

```solvik
val ambiguous = identity // TYPE_CANNOT_INFER
```

An expected `Any`, an unbounded type parameter, or another type that does not expose a complete
function signature is insufficient. No source syntax for a polymorphic function type is introduced.
No new `name<Type>` expression form is introduced, because it would be ambiguous with relational
expressions. Existing explicit type arguments remain available on direct calls:

```solvik
val value = identity<Integer>(1)
```

Generic function values erase no static checks. Each reference site records its concrete
instantiated `FunctionType` in `CheckedProgram`; lowering does not repeat inference.

### 4.8 Bound method references

Reading an instance method without calling it produces a bound method value:

```solvik
class Formatter {
    func format(value: Integer): String {
        return value.toString()
    }
}

val formatter = Formatter()
val operation: func(Integer): String = formatter.format
println(operation(42))
```

The receiver expression is evaluated exactly once when the bound method value is created. The
receiver is retained strongly by that value. The method's implicit receiver does not appear in the
function type.

Ordinary virtual dispatch is preserved. A reference through a class or interface type invokes the
implementation selected by the captured receiver's runtime class. Overrides, interface defaults,
delegated implementations, universal methods, built-in instance operations, and synthesized
`Result` operations must behave the same through a bound reference as through an immediate method
call.

`this.method` is a bound reference to the current receiver. An unqualified method name remains
legal only as an immediate call under the existing implicit-`this` rule; using a method as a value
requires `this.method` so the captured receiver is explicit. A bare unqualified method name in a
value position is `RESOL_UNKNOWN_NAME`.

`super.method` creates a value bound to `this` that invokes the immediate superclass implementation
without virtual redispatch, matching an immediate `super.method(...)` call.

A generic method reference is instantiated contextually under the same monomorphic rules as a
generic top-level function reference:

```solvik
val operation: func(Integer): Integer = object.identity
```

A normal member reference on a nullable receiver is illegal. Safe member access produces a nullable
function value and evaluates the receiver once:

```solvik
val operation: (func(Integer): String)? = formatter?.format
```

If the receiver is null, the result is null and no bound function is created. If it is non-null, the
result is the corresponding bound method. When the receiver's static type is non-null, `?.` retains
the non-null function type, matching existing safe-access behavior.

Each successful evaluation of a bound method-reference expression creates a distinct function-value
identity, even for the same receiver and method. Copying that value through bindings preserves its
identity.

Properties may themselves have function types. Because Solvik already uses one class-member
namespace, a property and method cannot share a name; member resolution can therefore decide
statically whether `receiver.member` reads a stored function value or creates a bound method value.

Static methods, constructors, and enum variants are not values in this phase. The class name remains
a compile-time receiver only.

### 4.9 Equality, identity, hashing, and display

Function values are identity-bearing references. A concrete function type and its nullable form are
valid operands of `===` and `!==` when the ordinary compatibility rule also holds. `Any` remains
invalid for identity operations without refinement, as it does for every other identity-bearing
runtime value.

Semantic equality for function values is reference identity. Function `hashCode()` is the matching
reference-identity hash. These operations are fixed and cannot be overridden. The following
properties therefore hold:

```solvik
format === format // true: canonical named function value

val first = func() {}
val second = func() {}
first === second // false
first === first  // true

formatter.format === formatter.format // false: two bound-value creations
```

`toString()` for every function value returns the exact string `func`. It must not expose a Java
class name, memory address, Truffle node name, module path, captured values, or implementation
details. Consequently, `print`, `println`, and `..` render every function value as `func`.

Function values must be added to the specification's equality and hash tables and to the compiler's
identity domain. Collections continue to use the language-wide equality and hashing rules.

### 4.10 Type tests, casts, patterns, and interoperation with existing constructs

Function types are not reifiable in the first implementation. A function type used as the target of
`is` or `as` is `TYPE_INVALID_TYPE_OPERAND`. A null check may still refine a nullable function type.

A function value may be assigned to `Any`, stored in a collection, returned in an enum payload, or
passed through another generic type. Recovering a statically callable function type from `Any`
requires a future checked-cast design; this phase does not add one.

Function types participate in ordinary nullability, flow analysis, generic substitution, and
definite initialization. They are not constant expressions for `switch` labels. No function-specific
`match` pattern is added.

At the Polyglot interop boundary, a non-null function value must report itself as executable. Host
execution must enforce the function's arity as an internal runtime invariant and invoke the same
call target as guest execution. Guest source never relies on that runtime check because semantic
analysis rejects bad arity before lowering. Function parameter and return type metadata need not be
reflectively exposed to hosts in this phase.

## 5. Required diagnostics

The specification update must add this diagnostic without renumbering existing codes:

| Symbolic name | Stable code | Trigger | Primary span |
|---|---|---|---|
| `SEM_MUTABLE_CAPTURE` | `SOLV-SEM-057` | an anonymous function reads or writes a captured `var` local | captured name reference |
| `SEM_UNLISTED_CAPTURE` | `SOLV-SEM-058` | an anonymous-function body uses an eligible outer local, parameter, or `this` that its capture list omits | body reference |
| `SEM_INVALID_CAPTURE` | `SOLV-SEM-059` | a capture item resolves to something other than an eligible immutable local, parameter, or `this` | capture item |

A generic function or generic method used as a value without a complete expected function type is
the existing `TYPE_CANNOT_INFER` (`SOLV-TYPE-030`), reported on the function or method reference. No
second inference diagnostic is introduced.

An unknown identifier written in a capture list remains `RESOL_UNKNOWN_NAME`; `this` where no
instance receiver exists remains `RESOL_THIS_OUTSIDE_CLASS`; duplicate items and capture/parameter
collisions are `RESOL_DUPLICATE_NAME`; and an empty list is a parser error because `captureList`
requires at least one item. After reporting an invalid capture item, semantic analysis must retain a
poisoned placeholder for body checking so the same root cause does not cascade into an unlisted- or
unknown-name diagnostic.

Existing diagnostics retain these roles:

- `TYPE_NOT_CALLABLE` for invoking a non-function value;
- `TYPE_ARITY_MISMATCH` for a function-value call with the wrong argument count;
- `TYPE_MISMATCH` for incompatible function assignments, arguments, results, and call arguments;
- `TYPE_NULLABLE_DEREFERENCE` for calling or normally binding through a nullable receiver;
- `TYPE_NOT_GENERIC` for explicit type arguments on a monomorphic function value;
- `TYPE_INVALID_TYPE_OPERAND` for `is` or `as` targeting a function type;
- `TYPE_FUNCTION_AS_VALUE` for a callable category that remains explicitly deferred, including a
  static method reference;
- `TYPE_MISSING_RETURN_VALUE`, `TYPE_UNEXPECTED_RETURN_VALUE`,
  `TYPE_MISSING_RETURN_PATH`, and `TYPE_RETURN_MISMATCH` inside anonymous functions;
- `TYPE_UNINITIALIZED_VARIABLE` for an attempted recursive closure initializer; and
- `SEM_VALUE_EXPRESSION_STATEMENT` for a discarded anonymous or referenced function value.

`TYPE_FUNCTION_AS_VALUE` (`SOLV-TYPE-014`) must no longer reject legal top-level or bound method
references. Its current specification-required use for bare synthesized and built-in method reads
must be removed in the new specification revision because those reads become bound method
references. The code remains available for explicitly deferred callable categories, but no
new-revision source program may emit it for a valid callable reference.

## 6. Specification and architecture changes

Before parser or runtime implementation:

1. Revise `docs/LANGUAGE_SPEC.md`:
   - declare a new specification revision;
   - remove “no first-class function values” from section 6;
   - add the complete normative rules from section 4 of this plan;
   - extend type grammar, assignability, equality, hashing, identity, display, nullability, flow,
     generic inference, member access, and required diagnostic tables;
   - state every non-goal explicitly as deferred where ambiguity would otherwise remain; and
   - remove the new revision's `TYPE_FUNCTION_AS_VALUE` requirement for callable member reads.
2. Revise `docs/ARCHITECTURE.md`:
   - distinguish direct callable symbols from guest function values;
   - define closure capture analysis and checked facts;
   - define runtime representations for named, anonymous, capturing, and bound function values;
   - document direct versus indirect call lowering;
   - document source sections, instrumentation, interop, and primitive calling conventions; and
   - preserve the one typed-representation-to-Truffle-AST backend.
3. Update the semantic and lowering coverage inventories with every new path before claiming
   coverage completeness.

No implementation test may be treated as the semantic authority. Tests and TCK oracles must quote
the revised specification.

## 7. Compiler and runtime implementation plan

### 7.1 Grammar and syntax AST

Edit only `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`; generated ANTLR files must
never be edited.

Add grammar productions equivalent to:

```antlr
typeRef
    : nominalTypeRef
    | functionTypeRef
    | LPAREN functionTypeRef RPAREN QUESTION
    ;

functionTypeRef
    : FUNC LPAREN typeRefList? RPAREN (COLON typeRef)?
    ;

anonymousFunctionExpr
    : FUNC captureList? LPAREN parameterList? RPAREN (COLON typeRef)? block
    ;

captureList
    : LBRACKET captureItem (COMMA captureItem)* RBRACKET
    ;

captureItem
    : Identifier
    | THIS
    ;
```

The actual grammar must be factored to support nested function types and nullable grouping without
left recursion or ambiguity with function declarations. `anonymousFunctionExpr` belongs in
`primary`. A `FUNC` followed by `Identifier` remains a declaration only in a declaration context;
an anonymous function always has `(` immediately after `func`.

Add syntax nodes rather than overloading nominal nodes with sentinel names:

- a common type-reference abstraction if `TypeRefNode` must cease being final;
- `FunctionTypeRefNode` containing parameter type references and a return type reference;
- a grouped/nullable representation that preserves whether nullability applies to the function or
  its result;
- `AnonymousFunctionExprNode` containing the written capture items, parameters, declared return
  type, and body; and
- checked metadata for function-reference expressions without mutating syntax nodes.

Every new syntax node must retain its full source span and return all children in source order,
including capture items before parameters and the body.
`SolvikAstBuilder` must synthesize `Unit` for an omitted anonymous-function or function-type return
exactly as declarations do.

Parser tests must cover nested types, nullable grouping, zero/multiple parameters, absent and
non-empty capture lists, `this` capture, multiline forms, semicolon insertion after an anonymous
body, declaration/expression disambiguation, empty/malformed/duplicate-looking capture forms, and
nesting-depth protection. Duplicate validity is decided semantically, but its syntax must retain both
items and their individual spans.

### 7.2 Type model

Promote `FunctionType` from an internal call signature to a fully supported `Type`:

- canonicalize equal function types so identity-based compiler caches remain reliable;
- implement structural identity, substitution, free-type-parameter traversal, and rendering;
- integrate contravariant parameters and covariant results into `Type.isAssignableTo` without
  making other user types structural;
- add function-type handling to `TypeJoin`;
- ensure `nullableView()` works for function types;
- make `Any` a supertype of every non-null function type;
- update `IdentityDomain` to recognize concrete and nullable function types; and
- reject function types as reified type-test/cast targets.

Rendering in diagnostics must use source-style names such as `func(Integer): String`, not the
current internal `(Integer) -> String` spelling, unless the revised specification deliberately
chooses the latter everywhere. The parser, type renderer, diagnostics, and tests must agree on one
canonical spelling.

Generic substitution must recurse through parameter and result types. Function-type variance must
not affect invariant generic applications: `List<func(Dog): Animal>` remains invariant as a
`List` application even though the contained function types have their own assignability relation.

### 7.3 Resolution and semantic analysis

Extend `SolvikSemanticAnalyzer` and `CheckedProgram` so lowering consumes explicit checked facts.
Required facts include:

- the `FunctionSymbol` selected by every named function reference;
- the instantiated `FunctionType` and type-parameter substitution of every generic reference;
- the resolved method and receiver mode of every bound method reference;
- whether a reference uses normal virtual dispatch, an interface/default/delegate route, a fixed
  built-in operation, or `super` dispatch;
- the source-ordered capture descriptors and resolved symbols for every anonymous function;
- the static type of each capture;
- whether an anonymous function is non-capturing; and
- the function type of every indirect call site.

Analysis must become expected-type aware for function-reference expressions. Expected function
types flow into:

- explicitly typed local/property/static-property initializers;
- assignment targets;
- declared function and anonymous-function returns;
- callable arguments;
- collection element/key/value positions; and
- generic construction positions after their containing type is resolved.

Expected-type propagation must not weaken independent expression checking. A non-generic named
function and a fully typed anonymous function have their own type even without context. Only a
generic function or method reference requires contextual instantiation.

Calls require two paths:

1. retain existing direct resolution for declarations, constructors, static calls, methods,
   built-ins, variants, and conversions; and
2. after direct forms are ruled out, accept an expression of non-null `FunctionType`, check exact
   arity before arguments, apply normal argument conversion, and return its result type.

This ordering preserves current diagnostics for class construction, numeric conversion, and direct
calls while making local function variables callable.

Capture analysis must resolve the written capture list before entering the anonymous function's
parameter/body scope, preserving list order. It rejects duplicates, parameter collisions, mutable
bindings, invalid capture categories, an unavailable `this`, and a self-binding that is not yet
initialized. The body then resolves only its parameters, locals, written captures, and ordinary
top-level/module declarations. When lookup can identify an eligible enclosing local, parameter, or
`this` that was not listed, it reports `SEM_UNLISTED_CAPTURE` instead of silently capturing it or
degrading to `RESOL_UNKNOWN_NAME`. Globals do not exist; named top-level functions are values rather
than environment captures. Nested closures must forward outer values through an explicit capture at
every intervening function boundary.

Anonymous-function return and flow analysis must reuse the ordinary callable checks. Abrupt
completion, exceptions, `Result` propagation, null narrowing, local initialization, and loop-control
boundaries must not fork into a weaker closure-only implementation.

### 7.4 Lowering

`SolvikLowering` must lower only checked facts. It must never redo name resolution, generic
inference, member selection, or capture discovery.

Add lowering paths for:

- canonical named-function value constants;
- anonymous-function roots;
- closure environment creation;
- capture reads in anonymous roots;
- bound virtual/interface/built-in/super method values;
- safe bound-reference creation; and
- indirect function-value invocation.

Every anonymous expression gets one lowered root/call target associated with its source declaration.
Evaluation allocates a function value and, for a capturing closure, an environment containing the
captured values in checked order. Non-capturing anonymous functions still allocate a distinct
function value on each evaluation, as required by identity semantics; the call target may be shared.

Closure bodies must receive their environment through hidden runtime arguments or an equivalently
explicit immutable runtime object. Captures must not be implemented by mutating a shared AST node or
storing execution-specific values in compilation-final node fields.

Direct calls continue to use their existing specialized nodes. Indirect calls need a dedicated
`SolvikInvokeFunctionValueNode` that:

- evaluates its callee once;
- evaluates arguments left to right;
- caches stable call targets with `DirectCallNode` where profitable;
- falls back to `IndirectCallNode` for megamorphic sites;
- supplies hidden environment or receiver arguments according to the function-value kind; and
- preserves primitive frame kinds and return representations.

Bound virtual method values must retain the receiver and the statically resolved method slot/name.
Invocation may cache the receiver runtime class and effective `SolvikFunction`, but it must preserve
ordinary override/default/delegation behavior. A `super` method value carries a fixed target and the
current receiver. A safe reference must use a conditional node that creates no function value on
the null path.

### 7.5 Runtime representation

Introduce a guest-visible function-value abstraction separate from compile-time symbols. One
possible sealed Java hierarchy is:

```text
SolvikFunctionValue
├── SolvikNamedFunctionValue
├── SolvikClosureValue
└── SolvikBoundMethodValue
```

The exact Java class names may differ, but the representation must provide:

- a stable call target or dispatch descriptor;
- an immutable capture environment or bound receiver where applicable;
- fixed reference-identity equality and hashing;
- exact display text `func`;
- Truffle interop executability; and
- no public mutation API.

`SolvikFunction`, which currently represents an installed declared callable, may remain execution
metadata or be refactored behind the value hierarchy. It must not conflate one declared call target
with the multiple identities created by anonymous and bound function values.

The runtime must not use Java `equals` for guest equality. Extend `SolvikValues`, `SolvikHash`,
`SolvikDisplay`, and the identity node according to the normative rules. Named function values must
be canonicalized per declared function within a Solvik program/context. Contexts must not share
guest function-value identity or captured state.

### 7.6 Instrumentation, debugging, and interop

Anonymous-function roots must carry source names and sections derived from the anonymous expression.
Guest stack traces must identify the physical source file and anonymous-function location. A stable
display label such as `<anonymous@file:line>` may be used internally for stack frames, but it must
not change guest `toString()`.

Indirect calls must carry the same call instrumentation tags as direct calls. Stepping into a
function value must enter the referenced declaration or anonymous body. Captured values should be
visible as read-only lexical values to debugger tooling where the existing frame API can expose
them without changing guest semantics.

Interop tests must establish that exported function values are executable, arity failures are
reported as interop errors rather than VM crashes, results convert through existing interop rules,
and guest exceptions remain guest exceptions.

### 7.7 Native image and performance

The implementation must remain closed-world compatible. It must not require reflective discovery of
function signatures, dynamic class generation, JVM lambdas, or `MethodHandle` lookup from guest
types. New runtime classes and interop exports must be reachable through ordinary code or registered
in native-image configuration where genuinely required.

Add focused benchmarks for:

- direct named calls as a regression baseline;
- calls through a named function value;
- non-capturing anonymous calls;
- capturing closure calls;
- monomorphic bound method calls; and
- a deliberately polymorphic function-value call site.

Benchmark results are evidence for optimization, not language semantics. The acceptance requirement
is that direct calls retain their existing lowering path and function-value calls do not force boxed
primitive storage in guest frames.

## 8. Vertical implementation sequence

Each phase must update the specification-derived tests for its own accepted and rejected behavior,
remain buildable, and pass its focused suite before the next phase begins.

### Phase 0 — normative baseline

- Revise `LANGUAGE_SPEC.md` and `ARCHITECTURE.md`.
- Declare the new spec revision.
- Update diagnostic contracts.
- Update TCK version metadata design before adding new-revision corpus entries.
- Confirm that every semantic choice in section 4 appears in the normative specification.

Exit criterion: the specification contains no “deferred” or “unspecified” statement covering one of
the six required capabilities.

### Phase 1 — function types

- Parse and build function type references.
- Promote and canonicalize `FunctionType`.
- Implement substitution, nullability, structural equality, variance, and joins.
- Allow function types in every declared-type position.
- Continue rejecting all function-producing expressions until phase 2, so the phase is coherent and
  tests can use type parsing/semantic rejection without a half-working runtime value.

Exit criterion: all type-system tests pass, including nested/nullable types and variance failures,
with no executable function values yet accepted.

### Phase 2 — named top-level functions as values

- Resolve bare and module-qualified function references.
- Create canonical runtime values.
- Add indirect invocation.
- Support built-in top-level function references.
- Extend equality, identity, hash, display, interop, and collections.
- Preserve the direct-call path.

Exit criterion: named references can be stored, passed, returned, compared by identity, printed, and
invoked on JVM and native distributions.

### Phase 3 — anonymous non-capturing functions

- Parse and build anonymous expressions.
- Reuse callable flow and return validation.
- Lower one shared body target plus one value allocation per evaluation.
- Enforce function-boundary control flow and identity rules.

Exit criterion: anonymous functions work in every expression position and every invalid return,
arity, or discarded-value case has a source-located diagnostic.

### Phase 4 — explicit immutable closure capture

- Add capture-list parsing, validation, analysis, and immutable environments.
- Support explicitly listed `val`, parameter, function-value, nested-closure, and `this` capture.
- Reject omitted dependencies, duplicate/invalid entries, empty lists, and every `var` capture with
  their normative diagnostics.
- Verify closure lifetime after the creating function returns.

Exit criterion: nested and escaping closures execute correctly without shared AST state, and mutable
or implicit capture is rejected consistently through direct and transitive cases.

### Phase 5 — generic function values

- Add expected-type propagation and contextual instantiation.
- Record substitutions in `CheckedProgram`.
- Support generic named top-level functions in value positions.
- Reject unconstrained, partially constrained, and ambiguously constrained references.
- Reject explicit generic arguments on monomorphic values.

Exit criterion: all generic inference cases are decided before lowering, with no runtime type-based
inference.

### Phase 6 — bound method references

- Support class, interface, inherited, overridden, default, delegated, universal, built-in,
  synthesized `Result`, safe, generic, `this`, and `super` references.
- Retire new-revision `TYPE_FUNCTION_AS_VALUE` rejection tests.
- Preserve one-time receiver evaluation and dynamic dispatch.
- Add bound-value interop and identity behavior.

Exit criterion: every immediately callable instance method category has behaviorally equivalent
bound-reference coverage.

### Phase 7 — integration, examples, TCK, and final validation

- Complete examples and corpus entries.
- Complete the new-revision TCK requirement inventory and manifests.
- Run coverage inventories and close every recorded gap.
- Run `./build-all.sh` as the final quality gate.

## 9. Test plan

Tests must verify parser structure, semantic decisions, lowered execution, runtime boundaries,
instrumentation/interop, launcher behavior, native behavior, and portable conformance. A parser test
does not substitute for a semantic test, and an in-process execution test does not substitute for a
distribution or TCK test.

### 9.1 Parser and AST tests

Add focused tests for:

- `func()`, explicit `Unit`, one/multiple parameters, nested parameter/result function types;
- `(func(...): R)?` versus `func(...): R?`;
- function types in local, property, static property, parameter, result, and generic arguments;
- anonymous functions with and without return types;
- anonymous functions with absent, single, multiple, nested-forwarding, and `this` capture lists;
- anonymous functions in argument, return, collection, block-expression, `if`, and `switch`
  expression positions;
- newline and semicolon insertion around anonymous bodies;
- `func name(...)` declaration versus `func(...)` expression disambiguation;
- malformed parameter types, missing delimiters, illegal anonymous type parameters, and trailing
  tokens; and
- parser nesting-limit behavior for deeply nested function types and anonymous expressions.

AST assertions must check node kinds, child order, synthesized `Unit`, nullability ownership, and
source spans.

### 9.2 Type and semantic tests

Create dedicated test classes, rather than scattering the whole feature across unrelated suites:

- `SolvikFunctionTypeTest`;
- `SolvikFunctionReferenceSemanticTest`;
- `SolvikAnonymousFunctionSemanticTest`;
- `SolvikClosureSemanticTest`;
- `SolvikGenericFunctionValueTest`; and
- `SolvikBoundMethodReferenceSemanticTest`.

Positive cases must include:

- exact function assignment;
- parameter contravariance and result covariance;
- `Any`, nullability, refinement, and nested types;
- passing and returning function values;
- collections and enum payloads containing function values;
- shadowing a function name with a function-valued local;
- module-qualified references;
- generic contextual instantiation from parameters, results, and nested positions;
- bound references through class and interface static types; and
- safe nullable receiver binding.

Negative cases must include:

- arity mismatch between function types;
- wrong variance direction;
- numeric widening incorrectly attempted inside a function type;
- calling a non-function or nullable function;
- wrong invocation arity and argument types;
- explicit type arguments on a function value;
- `is`/`as` with a function target;
- a generic reference without a complete target;
- inconsistent generic constraints;
- listed mutable capture, omitted immutable/mutable dependencies, duplicate captures, invalid
  capture categories, parameter/capture collisions, empty lists, and omitted `this`;
- attempted recursive closure initialization;
- discarded anonymous/reference expressions;
- missing, unexpected, incompatible, and not-on-all-paths anonymous returns;
- illegal loop control crossing a closure boundary;
- normal method binding through a nullable receiver; and
- static method, constructor, and enum-variant value attempts.

Every negative test must assert the stable diagnostic code and the primary source span.

### 9.3 Execution tests

Add execution suites for each value kind. They must cover:

- canonical identity of repeated named references;
- named calls through locals, parameters, returns, properties, collections, and `Any`-independent
  generic containers;
- anonymous identity per evaluation;
- zero-, one-, and multiple-argument indirect calls;
- `Unit`, primitive, nullable, object, enum, collection, and function-valued results;
- checked numeric widening at an indirect call site;
- guest exception propagation through indirect calls;
- closure lifetime after its creator returns;
- explicit nested capture forwarding and closure-of-closure behavior;
- capture of primitives, objects, function values, parameters, and `this`;
- observation of later object-property mutation through a captured reference;
- generic references instantiated to at least two unrelated concrete signatures;
- receiver evaluation exactly once;
- virtual override selection through a captured base/interface receiver;
- interface defaults and delegation;
- `super` binding without redispatch;
- safe binding on null and non-null receivers;
- universal `toString`, `equals`, and `hashCode` binding;
- built-in collection/regex/result operation binding;
- function equality, identity, hash consistency, and exact `func` display; and
- left-to-right callee/argument evaluation.

At least one test must drive a single indirect call site with several function-value kinds to
exercise the polymorphic fallback. Tests must not depend on Java identity hashes or internal class
names.

### 9.4 Lowering and runtime unit tests

Where a behavior can be isolated without bypassing Truffle context requirements, verify:

- closure descriptor/capture ordering;
- hidden argument layout;
- primitive frame kinds for function parameters, captures, and results;
- direct-call nodes remain in direct-call lowering;
- indirect nodes appear only for function-valued callees;
- named-value canonicalization is context-local;
- anonymous and bound allocations follow the specified identity rules; and
- safe references do not allocate on the null branch.

Prefer end-to-end language tests when a private-node assertion would duplicate implementation
instead of verifying behavior.

### 9.5 Instrumentation and interop tests

Extend the existing instrumentation/interop suites to verify:

- function values report executable interop capability;
- host invocation returns values and propagates guest exceptions;
- host wrong-arity invocation is controlled and source-independent;
- debugger/source sections point to the referenced declaration or anonymous body;
- call tags are emitted for indirect calls; and
- included/module source identity survives anonymous and bound invocation.

### 9.6 Regression and distribution corpus

Add checked-in `.sol`/`.output` examples under `language/tests` and regression cases under
`language/tests/regression`. At minimum include:

- `FirstClassFunctions.sol` / `.output`, demonstrating all six capabilities together;
- a module/include example that exports a named function reference;
- positive regression programs for each phase;
- negative regression programs for every new diagnostic and every deferred form that could
  otherwise parse accidentally; and
- diagnostic fixtures with expected codes and spans for mutable capture, omitted capture, invalid
  capture entries, and generic-reference inference failure.

The corpus must continue to require empty stdout for rejected programs. JVM and native launchers
must produce byte-identical success output.

## 10. Example program requirements

`language/tests/FirstClassFunctions.sol` should be readable documentation, not an exhaustive test.
It should demonstrate:

```solvik
func twice(value: Integer): Integer {
    return value * 2
}

func identity<T>(value: T): T {
    return value
}

class Prefixer {
    val prefix: String

    Prefixer(prefix: String) {
        this.prefix = prefix
    }

    func apply(value: String): String {
        return this.prefix .. value
    }
}

val named: func(Integer): Integer = twice

val offset = 2
val closure: func(Integer): Integer = func [named, offset](value: Integer): Integer {
    return named(value) + offset
}

val generic: func(String): String = identity

val prefixer = Prefixer("value=")
val bound: func(String): String = prefixer.apply

println(closure(20))
println(bound(generic("42")))
```

Its golden output must be authored from the revised specification. Additional focused examples may
be added when they teach a distinct rule, but the example directory must not become a substitute for
JUnit or TCK coverage.

## 11. TCK plan

First-class functions change normative semantics and therefore require a new specification revision
and a matching active TCK corpus. The project and TCK have not shipped, so the current development
TCK may be migrated in place; no compatibility runner or archived runnable TCK release is required.
Tests for the new semantics must not remain labeled `2026.09-draft` after that revision changes.

### 11.1 Versioning and inventory

The current TCK runner, schema, inventory, and profile represent one active specification revision;
its default corpus discovery does not support mixing manifests from two revisions. Migrate that
active development surface atomically:

1. replace the version registry's active supported draft revision;
2. update schema version enums, the complete requirements inventory, profiles, adapter capability
   declarations, reports, and every corpus manifest to the new revision;
3. rename or regenerate the corpus revision directory so the checkout contains only the active
   revision beneath the default corpus root;
4. carry forward unchanged requirements only after reviewing their normative quotes against the new
   specification;
5. create the new function-value requirements and corpus cases;
6. withdraw or supersede `REQ-2309`, whose `SOLV-TYPE-014` rejection contradicts bound method
   references, and replace `SOL-TCK-0352` with a valid new-revision oracle;
7. update `REQ-0508`, whose current quote explicitly says first-class values do not exist;
8. update `tck/VERSION` if the development TCK versioning policy requires a new identifier; and
9. update the TCK self-tests and prose guards for the resulting inventory and corpus counts.

Because no release exists, the migration may replace and regenerate current TCK inputs directly.
Source-control history is sufficient development history; the working tree must not retain a second
revision that the default loader would discover and reject.

Do not guess permanent `REQ-*` or `SOL-TCK-*` numbers in advance. Allocate the next available IDs at
implementation time and record the mapping from the design keys below.

### 11.2 Required normative inventory

Create at least these atomic requirement entries:

| Design key | Requirement |
|---|---|
| `FCF-TYPE-SYNTAX` | function types parse in every declared-type position; omitted result means `Unit` |
| `FCF-TYPE-NULLABLE` | nullable function versus nullable result grouping |
| `FCF-TYPE-VARIANCE` | parameter contravariance and result covariance |
| `FCF-TYPE-NO-NUMERIC-SUBTYPE` | numeric widening does not create function-type subtyping |
| `FCF-NAMED-REFERENCE` | a bare top-level function is a canonical function value |
| `FCF-QUALIFIED-REFERENCE` | module-qualified function references preserve declaration identity |
| `FCF-INDIRECT-CALL` | function-valued callees obey arity, typing, evaluation order, and result rules |
| `FCF-ANONYMOUS` | anonymous evaluation creates a new identity and uses explicit callable return rules |
| `FCF-CAPTURE-LIST` | every local closure dependency is declared in a non-empty explicit capture list |
| `FCF-CAPTURE-VAL` | listed immutable bindings are captured at closure creation in source-list order |
| `FCF-CAPTURE-OBJECT` | captured object references observe later object mutation |
| `FCF-CAPTURE-VAR-REJECT` | mutable local capture is the specified compile error |
| `FCF-CAPTURE-OMISSION-REJECT` | an eligible outer binding or `this` used without listing is the specified compile error |
| `FCF-CAPTURE-INVALID-REJECT` | duplicate, colliding, empty, unknown, and ineligible capture forms are rejected as specified |
| `FCF-CAPTURE-LIFETIME` | a closure remains valid after its creator returns |
| `FCF-GENERIC-INSTANTIATION` | expected function type instantiates a generic function reference |
| `FCF-GENERIC-NO-TARGET` | an unconstrained generic reference is rejected with the specified code |
| `FCF-BOUND-ONCE` | a bound receiver is evaluated once and retained |
| `FCF-BOUND-DISPATCH` | bound class/interface references preserve virtual dispatch |
| `FCF-BOUND-DEFAULT-DELEGATE` | interface defaults and delegated methods bind correctly |
| `FCF-BOUND-SAFE` | safe binding yields null or a nullable bound function as specified |
| `FCF-BOUND-SUPER` | a `super` reference invokes the immediate superclass implementation |
| `FCF-FUNCTION-IDENTITY` | named, anonymous, and bound identity rules |
| `FCF-FUNCTION-EQUALITY-HASH` | semantic equality is identity and hash is consistent |
| `FCF-FUNCTION-DISPLAY` | exact display text is `func` |
| `FCF-FUNCTION-EXCEPTION` | guest exceptions propagate through indirect invocation |
| `FCF-FUNCTION-NONREIFIABLE` | function targets of `is`/`as` are rejected |

Each inventory entry must quote the final normative specification verbatim and use one of the TCK
schema's supported oracle kinds, normally compile-time or runtime for this feature. Diagnostic codes
are normative only when the revised specification names them. Polyglot interop remains an
implementation integration suite because the current launcher-based portable TCK protocol does not
expose guest function values to a host caller; it must not be mislabeled as a portable TCK result.

### 11.3 Portable corpus cases

For each requirement, add the smallest independent program that distinguishes a conforming
implementation from a plausible incorrect one. Important paired tests include:

- accepted covariance/contravariance and rejected reverse variance;
- direct call versus function-value call with the same output;
- named-reference identity true versus anonymous/bound identity false;
- explicit capture before creator return versus invocation after creator return;
- object-reference capture with mutation after closure creation;
- listed dependency versus the same dependency omitted from the capture list;
- explicit forwarding through two nested closure capture lists;
- generic reference with a complete target versus no target;
- virtual bound reference through a base type versus a fixed `super` reference;
- null safe-bound reference versus non-null safe-bound invocation;
- indirect exception propagation caught by guest code; and
- rejection phase/code tests for mutable, omitted, invalid, duplicate, empty, and colliding captures
  and for non-reifiable type operands.

Success manifests must declare exact stdout and exit status. Compile-error manifests must declare
the normative diagnostic code and UTF-8 source span where specified. Programs must not rely on
host-specific line endings, timing, Java exception text, Java class names, identity hash values, or
unspecified collection iteration.

### 11.4 TCK generator and self-tests

Commit a generator for the new batch under `tck/tools/` at the same time as its corpus. The generator
must reproduce manifests, oracle comments, and source files byte for byte in an empty temporary
corpus. Extend `verify_regen.py` coverage so the batch cannot become an ungenerated provenance gap.

Run and, where necessary, extend:

- schema and strict-JSON tests;
- manifest/inventory linkage tests;
- normative-quote verification;
- profile coverage validation;
- reference-adapter refusal accounting if the independent subset does not implement function
  values; and
- JVM/native differential runs.

The reference subset adapter must refuse new function-value programs honestly until it implements
them. A refusal is not agreement and cannot satisfy full-language conformance.

## 12. Documentation deliverables

Implementation is incomplete until all user-facing and maintainer documentation agrees:

- `docs/LANGUAGE_SPEC.md`: normative syntax and semantics;
- `docs/ARCHITECTURE.md`: compiler/runtime boundaries and representation;
- `README.md`: feature summary and examples if it enumerates language capabilities;
- `language/tests/FirstClassFunctions.sol`: runnable example;
- `docs/SEMANTIC-TEST-COVERAGE.md`: resolution/type/capture coverage;
- `docs/LOWERING-TEST-COVERAGE.md`: named/closure/bound/indirect lowering coverage;
- `tck/README.md` and `tck/IMPLEMENTATION_PLAN.md`: new revision, inventory, and corpus status; and
- diagnostic reference prose wherever stable codes are listed.

Documentation must not describe Java lambdas, JVM functional interfaces, or Truffle classes as
Solvik semantics.

## 13. Validation commands

Use GraalVM for JDK 25 and repository wrappers. Focused commands may be added as tests are created,
but the validation sequence must include:

```bash
# Focused parser/type/semantic/execution suites using GraalVM JDK 25.
./mvnw -pl language -Dtest='*FunctionType*,*FunctionReference*,*AnonymousFunction*,*Closure*,*BoundMethod*' test

# Portable TCK validation and self-tests.
./tck/tck-check.sh

# Required final JVM + native builds, corpus, and TCK runs.
./build-all.sh
```

The focused Maven command must use the GraalVM `JAVA_HOME` selected by the repository wrappers; it
must not fall back to the host JDK. `./build-all.sh` is the final gate and cannot be skipped.

After the gate passes:

- review `git diff --check`;
- review the complete diff for accidental generated-parser edits, stale SimpleLanguage language
  paths, weakened diagnostics, or tests that reproduce implementation output instead of normative
  oracles;
- confirm JVM/native differential agreement for all new TCK cases; and
- confirm every row in the semantic and lowering coverage inventories is resolved.

## 14. Acceptance criteria

The feature is complete only when all of the following are true:

1. A new `LANGUAGE_SPEC.md` revision normatively defines all six required capabilities and every
   edge case listed in section 4.
2. The parser accepts exactly the new syntax and generated ANTLR output was never hand-edited.
3. Function types work in every declared-type position with structural identity, variance,
   substitution, joins, nullability, and `Any` integration.
4. Named references, anonymous functions, explicit immutable closures, generic instantiation, and
   bound methods work through the typed/lowered pipeline.
5. Direct calls retain their existing lowering path.
6. Implicit, mutable, invalid, duplicate, empty, and colliding capture forms and every deferred
   construct are rejected precisely.
7. Function equality, identity, hashing, display, nullability, exceptions, and interop match the
   specification.
8. Source sections, instrumentation, guest stack traces, and include/module source identity are
   preserved.
9. Primitive parameters, captures, and results are not boxed merely to support function values.
10. Positive and negative JUnit tests cover parser, semantic, lowering, runtime, instrumentation,
    and interop behavior.
11. Checked-in examples and regression corpus cases cover the feature and pass under both shipped
    launchers.
12. The new-revision TCK inventory, generated corpus, manifests, profiles, and quote provenance pass
    all TCK self-tests.
13. JVM and native TCK runs agree on every portable observable.
14. `./build-all.sh` passes from clean outputs.
15. The final diff and coverage inventories contain no unresolved gap or known semantic ambiguity.

No subset of these criteria is sufficient for completion under the repository's 100% confidence
rule.

## 15. Risk register

| Risk | Consequence | Required mitigation |
|---|---|---|
| Function types accidentally make user types structural | Breaks Solvik's nominal type model | Confine structural logic to `FunctionType`; add unrelated-class regression tests |
| Variance is reversed | Unsound calls at runtime | Test both valid and invalid directions with base/derived parameter and result types |
| Expected-type inference leaks into direct calls | Changed diagnostics or inference | Preserve separate direct-call and function-reference resolution paths |
| Generic references become implicitly polymorphic | Runtime or erased type unsoundness | Require complete contextual monomorphic substitution and record it in `CheckedProgram` |
| A closure body acquires an undeclared dependency | Hidden inputs and difficult review | Require every eligible outer value and `this` in an explicit capture list |
| Closures capture mutable frame storage | Hidden aliasing, races, invalid lifetime | Reject `var` capture; copy listed immutable values into immutable environments |
| AST nodes retain per-execution captures | Cross-call/context corruption | Store captures only in runtime values/arguments, never mutable node fields |
| Bound reference evaluates receiver more than once | Duplicated side effects | Dedicated creation node and side-effect-count execution tests |
| Bound dispatch freezes the wrong implementation | Overrides/defaults/delegates behave incorrectly | Record dispatch mode and test every method source through base/interface types |
| Function identity depends on Java wrapper allocation | JVM/native divergence | Specify identity per value kind and explicitly canonicalize named values |
| Equality/hash use Java behavior | Violates guest invariant | Extend shared `SolvikValues`/`SolvikHash` services and test collections |
| Primitive calls become boxed | Performance regression against repository principles | Preserve frame kinds and add lowering tests/benchmarks |
| Indirect calls replace direct calls | Broad performance regression | Keep direct lowering; inspect node paths and benchmark baseline |
| Nullable grammar binds `?` to the wrong type | Source ambiguity | Require grouped nullable-function syntax and assert AST shape |
| Existing `SOLV-TYPE-014` TCK oracle remains active | Contradictory conformance results | Version the spec/TCK and retire the prohibition only in the new revision |
| Native image misses runtime/interop classes | JVM-only success | Exercise complete corpus and TCK under `build-native.sh` |
| TCK expected output is copied from the IUT | Circular oracle | Derive each manifest from quoted revised specification and verify quotes |

## 16. Final design summary

The delivered language surface is:

```solvik
// Function type and named function value
val named: func(Integer): String = format

// Anonymous function
val anonymous = func(value: Integer): String {
    return value.toString()
}

// Explicit immutable capture
val prefix = "value="
val closure = func [prefix](value: Integer): String {
    return prefix .. value
}

// Contextually instantiated generic function value
val identityOfInteger: func(Integer): Integer = identity

// Bound method reference
val bound: func(Integer): String = formatter.format

// Function-value invocation
println(bound(identityOfInteger(42)))
```

This feature remains independent of threading. It provides the typed callable foundation that later
virtual-thread APIs may consume without giving first-class functions concurrency-specific semantics.
