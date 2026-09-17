# Solvik Implementation Plan

The implementation must proceed in the following order. A run implements exactly the phase marked `NEXT` in `docs/STATUS.md`, satisfies all exit criteria, updates status with evidence, and stops.

SimpleLanguage compatibility must be removed. Inherited code may remain temporarily as internal migration scaffolding, but each phase removes the production paths and tests superseded by that phase. Do not add a dual-language mode.

## Phase 0 — Reproducible Baseline and Migration Inventory

Goal: establish a reproducible starting point and an explicit removal inventory before semantic changes.

- Build the untouched local SimpleLanguage copy.
- Run existing tests.
- Record required JDK/GraalVM/Maven versions.
- Record the inherited parser, AST backend, Bytecode DSL backend, runtime, launcher, registration, interop, instrumentation, tests, and generated-source workflow.
- Record required JDK, GraalVM, Maven, and ANTLR versions and exact commands.
- Keep existing Java package names only as temporary implementation details; all new front-end code uses Solvik names.
- Preserve upstream licensing notices where required.
- Add and validate the Solvik governing documents.

Exit criteria:
- clean build;
- baseline tests pass;
- inherited launcher behavior is recorded only as baseline evidence, not supported compatibility;
- `docs/STATUS.md` records Phase 1 as next;
- removal inventory identifies when every exposed SimpleLanguage path will be replaced or deleted.

## Phase 1 — Solvik Front-End Skeleton

Goal: create Solvik source AST independent of executable Truffle nodes.

Implement:
- source spans;
- AST declaration/expression/statement interfaces/classes;
- parser adapter that creates syntax AST;
- diagnostic framework.

Keep the supported syntax deliberately tiny:
- top-level functions;
- literals;
- arithmetic;
- local variables;
- `if`;
- `return`.

Until Phase 2, all statements in parser tests must use explicit `;`.

Exit criteria:
- Solvik parser produces AST;
- parser tests inspect AST;
- no static type system yet;
- Solvik syntax is not lowered or executed before static analysis exists;
- inherited execution code remains internal migration scaffolding only.

## Phase 2 — Go-Style Semicolon Insertion

Implement lexical semicolon insertion before expanding grammar.

Support:
- explicit `;`;
- newline terminators;
- multiline expressions after operators/commas/open delimiters;
- `return` newline termination;
- mandatory leading `.` / `?.` chaining continuation;
- comment, blank-line, delimiter-nesting, `else`, and end-of-file cases from the language specification.

Exit criteria:
- equivalent newline/semicolon programs parse to equivalent ASTs;
- negative edge-case tests exist;
- parser-error-driven recovery is not used for insertion.

## Phase 3 — Raw Strings

Implement:
- `r"..."`;
- `r#"..."#`;
- arbitrary matching `#` count;
- multiline raw strings;
- no escape interpretation;
- precise unterminated-literal errors.

Exit criteria:
- regex/JSON/SQL/path examples pass;
- semicolon insertion ignores raw-string internal newlines.

## Phase 4 — Name Resolution and Static Core

Goal: prove static semantics without adding execution.

Implement built-in types:
- `Any`;
- `Object`;
- `Nothing`;
- `Unit`;
- `Boolean`;
- `String`;
- `Int`.

Implement:
- assignment, `while`, three-clause `for`, `break`, and `continue` syntax and AST nodes;
- declaration collection;
- lexical scopes and name resolution;
- explicit parameter and return-type resolution;
- `val` / `var`;
- local type inference from initializer;
- assignment checks;
- operator typing;
- definite assignment;
- Boolean checking for `if`, `while`, and `for`;
- return-path validation;
- entry-point validation for the implicit `main` formed by executable top-level statements;
- compile-time type diagnostics.

Canonical proof:

```solvik
func add(a: Int, b: Int): Int {
    return a + b
}
```

must be resolved and statically checked.

Exit criteria:
- correct programs produce a fully typed semantic result;
- unknown names, duplicate names, incorrect assignments, invalid operators, invalid conditions, and return mismatches produce source-located diagnostics;
- a program with an error produces no typed result;
- no Solvik executable nodes or call targets are produced.

## Phase 5 — Typed Lowering and First Solvik Execution

Goal: make the statically checked core language executable and cut over the public language path.

Implement:
- a backend-independent typed/lowered representation;
- lowering for the Phase 4 language into the Truffle AST backend;
- primitive-specialized `Int`, `Boolean`, and `String` execution;
- functions, locals, calls, arithmetic, `if`, loops, and returns;
- Solvik `print` and `println` built-ins;
- Solvik language registration and file detection;
- Solvik launcher behavior and `solvik` launcher scripts.

Exit criteria:
- static analysis always succeeds before lowering;
- Truffle retains primitive specializations;
- the Solvik production parse path is `source AST -> semantics -> lowering -> Truffle AST`;
- no parser-to-executable-node Solvik path exists;
- the runtime registers language id `solvik`, MIME type `application/x-solvik`, and `.sol` files;
- the JVM and native launchers use Solvik names, accept Solvik programs, and expose no SimpleLanguage alias;
- exposed SimpleLanguage parser and Bytecode-backend selection paths are removed;
- representative SimpleLanguage-only syntax is rejected.

## Phase 6 — Classes and Objects

Implement:
- class declarations;
- fields/properties;
- `val` vs `var` properties;
- a constructor named after the class;
- instance creation;
- instance methods;
- `this`;
- statically declared object layouts.

Adapt the SimpleLanguage dynamic object model to class-controlled Shapes.

Exit criteria:
- undeclared property access/write is rejected;
- property mutability enforced statically;
- object construction and method invocation execute through Truffle.

## Phase 7 — Root Hierarchy and Single Inheritance

Implement:
- built-in type hierarchy;
- usable `Byte`, `Short`, `Long`, `Float`, `Double`, and `Char` built-ins;
- explicit numeric conversions and same-type numeric operator checking;
- `open class`;
- `extends`;
- final-by-default class semantics;
- open/final method semantics;
- mandatory `override`;
- `super` only as required for single inheritance;
- cycle detection;
- method compatibility validation.

Exit criteria:
- multiple inheritance rejected;
- extending final class rejected;
- accidental override rejected;
- all declared built-in types, explicit conversions, and same-type arithmetic are tested.

## Phase 8 — Interfaces and Defaults

Implement:
- `interface`;
- multiple `implements`;
- abstract signatures;
- default methods;
- conformance checking;
- explicit resolution of conflicting defaults.

Exit criteria:
- missing interface methods produce compile-time errors;
- conflicting defaults require explicit resolution.

## Phase 9 — Delegation

Implement:

```solvik
delegate val field: InterfaceType
```

Support interface satisfaction through delegates.

Rules:
- explicit methods win;
- inherited implementation next;
- delegation must be unambiguous;
- an unambiguous interface default is used only when no earlier implementation exists;
- ambiguity is a compile-time error.

Exit criteria:
- useful composition works without boilerplate;
- tests cover two-delegate conflicts and explicit resolution.

## Phase 10 — Null Safety

Implement:
- `T?`;
- `null`;
- `?.`;
- `??`;
- `is` type tests;
- checked `as` casts;
- nullable assignment compatibility;
- null dereference rejection;
- null and `is` control-flow narrowing.

Exit criteria:
- nullable dereference is rejected statically;
- `if (x != null)` safely narrows `x`;
- stable values narrow after `is`;
- invalid casts follow the language specification.

## Phase 11 — Generics

Implement:
- generic declarations;
- generic type applications;
- generic classes, interfaces, functions, and methods;
- immutable built-in `List<T>`;
- generic substitution;
- generic assignment/conformance.

Use runtime erasure in the initial implementation. Runtime type tests against a non-reified type argument are rejected.

All generic parameters are invariant. Variance syntax is not part of the initial language.

Exit criteria:
- generic substitutions are checked at every construction, call, return, and assignment;
- raw generic types are rejected;
- runtime type tests against erased arguments are rejected.

## Phase 12 — Enums and Sealed Types

Implement:
- value-carrying enum variants;
- `sealed`;
- closed-variant metadata;
- construction and type checking for variants;
- runtime representation and execution.

Exit criteria:
- enum and sealed variant construction is statically checked;
- the compiler records the complete permitted variant set;
- invalid or external sealed variants are rejected.

## Phase 13 — Exhaustive match

Implement:
- pattern AST;
- `match` expression;
- destructuring;
- exhaustiveness checker;
- wildcard `_`.

Exit criteria:
- missing enum/sealed case fails compilation;
- all branches of a match expression are type-compatible.

## Phase 14 — Regex

Implement built-in:
- `Regex`;
- `RegexMatch`.

Implement construction, `matches`, `find`, `findAll`, and `replace`. Keep the regex engine behind Solvik runtime types.

Constant regex patterns must be compiled once per source constant and cached. Invalid constant patterns are compile-time diagnostics; invalid dynamically constructed patterns raise a Solvik runtime regex error.

Exit criteria:
- the required API is statically typed and executable;
- raw-string patterns work;
- constant compilation and invalid-pattern behavior are tested.

## Phase 15 — Non-Fallthrough switch

Implement `switch`:
- no implicit fallthrough;
- constant cases;
- grouped cases;
- `default`;
- `case regex r#"..."#`.

Exit criteria:
- no `break` needed;
- falling into the next case is impossible;
- regex cases execute correctly.

## Phase 16 — Tooling, Removal, and Release Validation

- source-quality diagnostics;
- syntax examples;
- no REPL in the initial release;
- GraalVM interop validation;
- debugger/instrumentation validation;
- native image build;
- performance benchmarks;
- final artifact, Java module, package, script, and launcher naming;
- deletion of remaining unsupported SimpleLanguage implementation paths, samples, and tests.

Exit criteria:
- `./build.sh` and `./build-native.sh` pass;
- instrumentation, interop, launcher, JVM distribution, and native distribution tests pass;
- public documentation describes only Solvik;
- no supported artifact, option, language registration, source path, sample, or positive test depends on SimpleLanguage syntax or semantics;
- remaining upstream names occur only in copyright notices and historical attribution;
- `docs/STATUS.md` records `NEXT` as `COMPLETE`.

## General Rule

Do not jump ahead, scaffold later phases, preserve a legacy mode, or implement multiple phases in one run. Complete and test each semantic layer so later features build on a stable type system and AST.

By the end of Phase 16, no supported artifact, option, language registration, source path, sample, or test may depend on SimpleLanguage syntax or semantics. Remaining upstream names are allowed only in copyright notices and historical attribution.
