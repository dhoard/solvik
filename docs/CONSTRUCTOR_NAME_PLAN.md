# Constructor Named After the Class: `init(...)` to `ClassName(...)`

## Problem

Solvik's constructor declaration uses a keyword in the name position
(`docs/LANGUAGE_SPEC.md` section 7):

```solvik
class User {
    val id: Long
    var name: String

    init(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

`init` is the only declaration in the language whose *name* is supplied by a keyword. Every other
declaration (`func`, `val`, `var`, `class`, `interface`, enum variants, parameters, delegates) uses
a keyword as an introducer and leaves the name to the programmer. Because `init` occupies the name
slot, it must be reserved as an identifier and cannot be used for anything else. Section 6 of the
specification reserves `init` for exactly one declaration form that never appears at a call site:
construction is written `User(...)`, never `User.init(...)`.

The result is a reserved word spent on a name the programmer cannot choose, reuse, or see at a use
site.

## Goal

A class declares its constructor as a member named after the class, with a parameter list and a
body, and without the `func` keyword or a return type:

```solvik
class User {
    val id: Long
    var name: String

    User(id: Long, name: String) {
        this.id = id
        this.name = name
    }
}
```

The constructor name is the class name the programmer already wrote, so no additional vocabulary is
reserved. `init` is removed from the grammar's keyword set and becomes an ordinary identifier.

Construction syntax is unchanged: calling the class name invokes the constructor.

## Non-goals

- **No call-site change.** `User(...)` remains the construction syntax. This plan does not introduce
  `new`, `User.new(...)`, or any other use-site marker.
- **No new keyword.** `init` is removed and nothing replaces it as a reserved word.
- **No underscore namespace.** No leading-`_` identifier rule is introduced.
- **No inheritance change.** Single inheritance, `open`/`override`, `super(...)`, and the
  constructor-lookup rules are unchanged.
- **No newly invented semantics for sealed classes.** A sealed class that declares a constructor
  keeps its current behavior: construction is rejected at the call site because the class is
  abstract (`SOLV-SEM-0xx`, `sealed class ... is abstract and cannot be constructed`). This plan
  does not add a rule forbidding the declaration.
- **No enum, interface, delegation, generics, null-safety, or regex change.**
- **No compatibility path.** There is no option, alias, or legacy mode that accepts `init` as a
  constructor. After this change `init` is an identifier everywhere, exactly like any other
  non-reserved word.

## Decision

1. Replace the grammar rule `initDecl: INIT LPAREN parameterList? RPAREN block` with
   `constructorDecl: Identifier LPAREN parameterList? RPAREN block`, and remove the `INIT: 'init'`
   lexer token. The first-token sets of the `classMember` alternatives stay disjoint
   (`val`/`var`, `delegate`, `Identifier`, `open`/`override`/`func`), so the rule is LL(1) and no
   other parser rule changes.
2. The constructor name must equal the enclosing class name. A generic class `Box<T>` declares
   `Box(...)`, not `Box<T>(...)`.
3. A class has at most one constructor declaration.
4. The constructor is not a method. It is not inherited, may not carry `open` or `override`, is not
   declared by an interface, is not forwarded by a `delegate`, and is not callable as
   `this.User(...)`.
5. A class member declaration other than the constructor may not have the same name as its class.
   This matches C# (CS0542) and Dart, and avoids the Java quirk where `p.Point(2)` calls a method
   while `Point(2)` constructs.
6. Construction, definite property initialization, the implicit zero-argument initializer, and
   `super(...)`/`super.member` rules are unchanged apart from the word "constructor" replacing
   "init" where the specification refers to the declaration.
7. Internally the constructor's `FunctionSymbol` keeps the sentinel name `"<init>"`. The sentinel is
   not user-visible, and keeping it avoids any interaction with a (forbidden) class-named method.

## Proposed Specification (draft)

This is the drop-in text for `docs/LANGUAGE_SPEC.md`. It is deliberately **not** applied to
`LANGUAGE_SPEC.md` by this plan: the specification is normative and must continue to describe the
implementation until the implementation lands, exactly as `FUNC_KEYWORD_RENAME_PLAN.md` was written
while the specification still said `fun`. Applying it is step 1 of the implementation run.

### Section 2, `User` mutability example

```solvik
class User {
    var name: String

    User(name: String) {
        this.name = name
    }
}
```

### Section 7, replace the two `init` paragraphs

Replace:

> A class has at most one `init` declaration. Calling the class name invokes it. Every property
> without a declaration initializer must be assigned exactly once on every successful constructor
> path before it is read; a `val` property cannot be assigned afterward.
>
> A class with no explicit `init` has an implicit zero-argument initializer only when all properties
> have declaration initializers. A subclass initializer must invoke `super(arguments)` as its first
> statement when the superclass has no zero-argument initializer; otherwise `super()` is implicit.
> `super.member` accesses the immediate superclass implementation.

with:

> A class declares its constructor as a class member whose name is the class name, with a parameter
> list and a body, and without the `func` keyword or a return type:
>
> ```solvik
> class User {
>     val id: Long
>     var name: String
>
>     User(id: Long, name: String) {
>         this.id = id
>         this.name = name
>     }
> }
> ```
>
> Calling the class name invokes its constructor. A class has at most one constructor declaration.
> Every property without a declaration initializer must be assigned exactly once on every successful
> constructor path before it is read; a `val` property cannot be assigned afterward.
>
> A constructor is not a method. It is not inherited, cannot carry `open` or `override`, is not
> declared by an interface, is not forwarded by a `delegate`, and cannot be invoked as
> `this.User(...)`. For a generic class `Box<T>`, the constructor is named `Box`, not `Box<T>`. A
> class member declaration other than the constructor cannot have the same name as its class.
>
> A class with no explicit constructor has an implicit zero-argument initializer only when all
> properties have declaration initializers. A subclass constructor must invoke `super(arguments)` as
> its first statement when the superclass has no zero-argument initializer; otherwise `super()` is
> implicit. `super.member` accesses the immediate superclass implementation.

### Section 7, first class example

`init(id: Long, name: String)` becomes `User(id: Long, name: String)`.

### Section 9, delegation example

`class UserService implements Repository<User>` declares
`UserService(repository: Repository<User>)` instead of `init(repository: Repository<User>)`.

## Changes

1. `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`
   - remove `INIT: 'init'`;
   - replace `initDecl` with `constructorDecl: Identifier LPAREN parameterList? RPAREN block`;
   - update `classMember` to name `constructorDecl`;
   - update the Phase 6 header comment that says "at most one `init`".
2. Regenerated parser artifacts, only via `./generate_parser.sh`:
   `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
   `.tokens`, `.interp`.
3. AST
   - rename `InitDeclNode` to `ConstructorDeclNode` and add a `name` field;
   - rename `AstKind.INIT_DECL` to `AstKind.CONSTRUCTOR_DECL`;
   - `ClassDeclNode`: rename `initializers()`/`initializer()` to `constructors()`/`constructor()`
     and update its Javadoc; update `FunctionDeclNode`'s Javadoc cross-reference.
4. `SolvikAstBuilder`
   - rename `buildInit` to `buildConstructor` and pass `ctx.Identifier().getText()`;
   - update the class-member dispatch.
5. Semantics
   - `FunctionSymbol`: rename the `initDeclaration` field and `initDeclaration()` accessor to
     `constructorDeclaration()`, and change `declaredConstructor`'s parameter type;
   - `ClassDeclNode.constructors()` drives the at-most-one check;
   - add the class-name check: a constructor-shaped declaration whose name differs from the
     enclosing class is an error, and a non-constructor member whose name equals the class is an
     error;
   - `SolvikSemanticAnalyzer`: update the duplicate-constructor check, the missing-initializer
     message, the implicit-`super` message, and the constructor-construction sites;
   - `DiagnosticCode`: rename `SEM_DUPLICATE_INIT` to `SEM_DUPLICATE_CONSTRUCTOR` (keep
     `SOLV-SEM-007`), rename `SEM_CLASS_REQUIRES_INITIALIZER` to
     `SEM_CLASS_REQUIRES_CONSTRUCTOR`, update the `SEM_MISSING_SUPER_INIT*` Javadocs, add
     `SEM_CONSTRUCTOR_NAME` (`SOLV-SEM-035`) and `SEM_MEMBER_NAMED_AFTER_CLASS` (`SOLV-SEM-036`),
     and replace `init` with `constructor` in the affected Javadoc.
6. Lowering: `SolvikLowering` uses `constructor.constructorDeclaration().body()`.
7. Examples: `language/tests/Objects.sol` (`Named`, `Service`) and `language/tests/Shapes.sol`
   (`Circle`, `Square`).
8. Tests: every Solvik source string that declares `init`, every `InitDeclNode`/`INIT_DECL`/`initDecl`
   reference, and the diagnostic-message assertions that quote "at most one init".
9. Documentation: `docs/LANGUAGE_SPEC.md`, `docs/IMPLEMENTATION_PLAN.md` (the Phase 6 member list),
   and a completed section in `docs/STATUS.md`.

## Tests

- **Positive:** a constructor named after its class with parameters, a generic `Box<T>` constructor
  named `Box`, a class with only declaration initializers and no constructor, a subclass calling
  `super(arguments)`, the `language/tests/*.sol` examples, and a class member declared on the line
  after a property with no explicit semicolon (pinning that the named constructor needs no
  semicolon-insertion table change).
- **Negative:**
  - a constructor-shaped declaration whose name differs from its class is `SOLV-SEM-035`;
  - a `func`/`val`/`var`/`delegate` member named after its class is `SOLV-SEM-036`;
  - two constructors are `SOLV-SEM-007`;
  - a constructor in an interface is a parse error (interfaces have no constructor member);
    `SolvikInterfaceParserTest#initDeclarationInsideAnInterfaceIsRejected` is rewritten for the
    constructor spelling, and its Javadoc no longer says `init`;
  - `open`/`override` on a constructor is a parse error;
  - a class with no constructor and a property without an initializer is `SOLV-SEM-006`.
- **Freed identifier:** `init` is usable as a method, property, parameter, and local name
  (`func init()` now parses and type-checks).
- **Removal regression:** `grep` finds no `INIT` token, `initDecl` rule, or `init`-as-constructor
  reference in the grammar, front end, tests, examples, or documentation.

## Validation

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`
- `./build.sh`
- `./build-native.sh`
- launcher smoke test: `standalone/target/solvik language/tests/Objects.sol` and the native launcher
  print the expected output.

## Status

Implemented. `docs/LANGUAGE_SPEC.md` now specifies the class-named constructor, the grammar,
regenerated parser, AST, semantics, diagnostics, lowering, examples, and tests are updated, and
`docs/STATUS.md` records the change and the build/test evidence. The specification text drafted in
the "Proposed Specification" section above was applied verbatim.
