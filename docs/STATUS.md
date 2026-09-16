# Solvik Implementation Status

This file is the phase handoff. Update it only after running the commands required by the active phase.

## Phase

- `NEXT`: Phase 8 — Interfaces and Defaults
- Completed phases: Phase 0 (baseline) 2026-09-16; Phase 1 (front-end skeleton) 2026-09-16;
  Phase 2 (lexical semicolon insertion) 2026-09-16; Phase 3 (raw strings) 2026-09-16;
  Phase 4 (name resolution and static core) 2026-09-16;
  Phase 5 (typed lowering and first Solvik execution) 2026-09-16;
  Phase 6 (classes and objects) 2026-09-16;
  Phase 7 (root hierarchy and single inheritance) 2026-09-16
- Last verified commit: `f3c2589` plus the uncommitted Phase 0–7 working tree
- Last clean JVM build: `./build.sh` passed on 2026-09-16 (352 Solvik language tests, 0 failures,
  0 errors, 0 skips)
- Last clean native build: `./build-native.sh` passed on 2026-09-16 (Phase 7, 48.6s); the
  `standalone/target/solviknative` launcher ran a Phase 7 inheritance/numeric program (output
  `Rex says woof`/`15`/`3`/`A`/`4.0`, empty stderr, exit 0)

An implementation run must execute only `NEXT`. It must not start the following phase.

## Phase 7 Evidence (completed 2026-09-16)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds `open`,
  `extends`, `open`/`override` method modifiers, the `super` expression, and `Long`, floating-point,
  and `Char` literal forms; regenerated parser artifacts (only via `generate_parser.sh`,
  byte-reproducible): `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`,
  `SolvikBaseVisitor.java`, `.tokens`/`.interp`;
- `org/solvik/parser/SemicolonInsertingTokenSource.java` adds the Long, floating-point, and Char
  literal tokens to the newline-terminator table;
- AST: `AstKind` adds `SUPER_EXPR`, `LONG_LITERAL`, `FLOATING_LITERAL`, `CHAR_LITERAL`; new
  `LongLiteralNode`, `FloatingLiteralNode`, `CharLiteralNode`, `SuperExprNode`; `ClassDeclNode`
  records `open` and an optional superclass; `FunctionDeclNode` records `open`/`override`;
  `SolvikAstBuilder` builds the new declarations, literals, and `super` expressions;
- Type model: new `NumberType`, `ByteType`, `ShortType`, `LongType`, `FloatType`, `DoubleType`,
  `CharType`, and the `NumericTypes` classifier; `IntType` now derives from `NumberType`;
  `ClassType` gains an analysis-time `resolveSuperType`; `TypeEnvironment` declares the complete
  Phase 7 root hierarchy;
- Semantic: `FunctionSymbol` carries `open`/`override`; `ClassSymbol` models a single superclass,
  declared versus inherited members, and a virtual method table; `ResolvedMethod` records a
  `super` call; `CheckedProgram` records explicit conversions and `super(...)` construction;
  `SolvikSemanticAnalyzer` resolves `extends`, detects cycles, validates modifiers and overrides,
  types the complete numeric hierarchy and explicit conversions, and checks `super`;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_LONG_LITERAL_OUT_OF_RANGE`,
  `TYPE_INVALID_CHAR_LITERAL`, `TYPE_CONVERSION_OUT_OF_RANGE`, `TYPE_INVALID_CONVERSION`,
  `RESOL_SUPER_OUTSIDE_CLASS`, and the `SEM_EXTEND_FINAL`/`SEM_INVALID_SUPERCLASS`/
  `SEM_INHERITANCE_CYCLE`/`SEM_ACCIDENTAL_OVERRIDE`/`SEM_OVERRIDE_*`/`SEM_SUPER_*`/
  `SEM_MISSING_SUPER_INIT*` codes;
- Truffle runtime: `SolvikTypes` adds the `long`/`float`/`double` primitive types;
  `SolvikExpressionNode` exposes `executeLong`/`executeFloat`/`executeDouble`;
  `SolvikReadLocalVariableNode`/`SolvikWriteLocalVariableNode` add Long/Float/Double frame
  specializations; `SolvikRootNode` copies those parameter kinds; `SolvikDisplay` renders every
  built-in value; `SolvikEqualNode` compares the new scalar types by value;
  `SolvikInvokeMethodNode` gained name-based virtual dispatch plus a direct `super` target;
  `SolvikClass` interns property keys per name and marks method lookup `@TruffleBoundary`;
- New nodes: `SolvikLongLiteralNode`, `SolvikFloatingLiteralNode`, `SolvikCharLiteralNode`,
  `SolvikNumericBinaryNode`, `SolvikNumericComparisonNode`, `SolvikNumericNegateNode`,
  `SolvikConvertNode`, and `SolvikSuperConstructorNode`; built-in arithmetic dispatch avoids
  `Number` virtual calls and JDK string concatenation in runtime-compiled methods so the
  native-image blocklist stays satisfied;
- `org/solvik/lowering/SolvikLowering.java` lowers inheritance (super-first layout and method
  tables), `super` calls, the new literals, explicit conversions, and non-`Int` numeric arithmetic.

### Semantics and architecture implemented

- The complete root hierarchy is present: `Any` → `Object` → `Number` →
  `{Byte, Short, Int, Long, Float, Double}`, plus `Boolean`, `Char`, `String`, and `Unit`, with
  `Nothing` the bottom type; sibling numerics are not assignment-compatible and no implicit
  widening or narrowing exists;
- `Long` (`L`/`l` suffix), decimal floating-point (optional exponent, `f`/`F` for `Float`), and
  `Char` literals type-check and execute; `Byte`/`Short` come from explicit conversions;
- explicit numeric conversions `T(value)` are the only conversions; integral targets range-check
  (literal arguments at compile time as `SOLV-TYPE-021`, otherwise at run time) and floating-point
  targets follow IEEE 754;
- arithmetic and ordering require same-type numeric operands and produce that type or `Boolean`;
  integral arithmetic is overflow-checked and float/double follow IEEE 754;
- classes are final by default; only an `open class` may be extended, and the grammar allows a
  single `extends` clause so multiple inheritance is impossible;
- members are final by default; only an `open fun` may be overridden, overrides must use
  `override`, and an override must keep the inherited parameter types with a covariant return type;
- the inheritance graph is checked for cycles; `super(...)` must be the first `init` statement
  when the superclass has no zero-argument initializer, and `super.member` resolves to the
  immediate superclass implementation;
- object layout is superclass-first; the subclass constructor runs the superclass constructor,
  then declaration initializers, then the `init` body, and inherited properties count as
  initialized on entry to a subclass constructor;
- virtual dispatch goes through the runtime class method table, so a call through a supertype-typed
  variable reaches the override, while `super.member(...)` bypasses it.

### Tests added (352 Solvik tests total, up from 286)

- `SolvikInheritanceParserTest` (6): `open class`/`extends` shape, method modifiers, `super(...)`,
  `super.member`, multiple-inheritance rejection, and top-level-function modifier rejection;
- `SolvikInheritanceSemanticTest` (7): nominal hierarchy, inherited members, override recording,
  explicit and implicit `super` initializer calls, `super` method resolution, and `extends Object`;
- `SolvikInheritanceNegativeTest` (16): extending a final class, accidental/unmatched/covariant
  override failures, inheritance cycles, non-class superclasses, `super` outside a class and
  without a superclass, bare `super`, `super(...)` placement, missing explicit/implicit super
  initialization, inherited property redeclaration, and inherited `val` writes;
- `SolvikInheritanceExecutionTest` (8): inherited property/method use, virtual dispatch through a
  supertype, dispatch from an inherited method, `super.member(...)`, explicit and implicit super
  constructors, `super.property`, and initializer ordering;
- `SolvikNumericTest` (12): literal and conversion typing, same-type arithmetic/ordering/negation
  result types, `Number`/`Object` acceptance, and execution of every built-in plus overflow and
  out-of-range runtime errors;
- `SolvikNumericNegativeTest` (15): mixed numeric operands, implicit widening/narrowing,
  non-numeric and abstract-type conversions, conversion arity and constant range errors,
  out-of-range `Long` literals, invalid character escapes, and `Char` arithmetic/ordering;
- `SolvikTypeModelTest` adds the complete numeric hierarchy and bottom-type coverage (5 tests);
  `SolvikAstStructureTest.phaseSevenNodeFamiliesAreProduced` pins `SUPER_EXPR`, `LONG_LITERAL`,
  `FLOATING_LITERAL`, and `CHAR_LITERAL` (13 tests);
  `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` pins the new literal
  terminators and the new non-terminator keywords.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 352 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 352 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 48.6s`;
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase7Demo.sol`
  printed `Rex says woof`/`15`/`3`/`A`/`4.0` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase7Demo.sol` printed the
  same, wrote no stderr, and exited 0;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in
  parser artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is
still only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl`
samples, the `simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI
references to `sl`).

### Known limitations carried into later phases

- `Char` is represented as one UTF-16 code unit; a supplementary (non-BMP) Unicode scalar literal
  is reported as an invalid character literal rather than being representable. String and other
  non-numeric conversions are intentionally not provided; only the six numeric types convert.
- `super` is not a value and `super.property = ...` assignment is rejected; only `super(...)` in
  `init` first position and `super.member` reads/calls are supported.
- `Byte`, `Short`, and `Char` values are boxed at frame boundaries (Truffle has no such frame slot
  kinds); `Int`, `Long`, `Float`, `Double`, and `Boolean` use primitive frame slots.
- Object properties remain object-typed Truffle shape locations, so `Int`/`Long`/etc. fields box
  at the field boundary; primitive field locations remain a later optimization.
- Virtual method lookup uses a per-class name table guarded by `@TruffleBoundary` rather than an
  inline cache; `super` calls use a fixed target.
- Interfaces, delegation, nullability, generics, enums, pattern matching, regex, and `switch`
  remain later phases (8–15).

## Phase 6 Evidence (completed 2026-09-16)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the Phase 6
  class/object grammar and documents it in the header: `classDecl`, `classMember`, `propertyDecl`
  (explicit type annotation required, matching the specification's rule that only locals infer),
  `initDecl`, the `thisExpr` primary, and the `CLASS`/`INIT`/`THIS` tokens; a class body tolerates
  stand-alone `SEMI` tokens because a member body ends in `}`, itself a semicolon terminator;
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- new syntax-AST nodes `org/solvik/ast/declaration/{ClassDeclNode,PropertyDeclNode,InitDeclNode}.java`
  and `org/solvik/ast/expression/ThisExprNode.java`; `AstKind` adds `CLASS_DECL`, `PROPERTY_DECL`,
  `INIT_DECL`, `THIS_EXPR`;
- `org/solvik/parser/SolvikAstBuilder.java` builds class declarations and now preserves top-level
  source order (functions and classes interleaved) rather than grouping by kind;
- `org/solvik/parser/SemicolonInsertingTokenSource.java` adds `THIS` to the newline-terminator
  table, since `this` is a value-producing atom;
- new `org/solvik/type/ClassType.java`; `TypeEnvironment` gains user-type registration while keeping
  the built-in list;
- semantic additions: `org/solvik/semantic/ClassSymbol.java`, `PropertySymbol.java`,
  `ResolvedMethod.java`; `FunctionSymbol` models top-level functions, instance methods (with an
  owning class), and `init` constructors; `CheckedProgram` exposes classes, resolved properties,
  construction calls, and method calls; `SolvikSemanticAnalyzer` collects class members and checks
  class bodies with constructor definite-initialization tracking;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `RESOL_UNKNOWN_MEMBER`, `RESOL_THIS_OUTSIDE_CLASS`,
  `TYPE_CLASS_AS_VALUE`, `TYPE_UNINITIALIZED_PROPERTY`, `TYPE_MISSING_PROPERTY_INITIALIZER`,
  `SEM_CLASS_REQUIRES_INITIALIZER`, and `SEM_DUPLICATE_INIT`;
- new Truffle runtime `org/solvik/truffle/object/`: `SolvikClass` (class identity, fixed property
  layout, method table, constructor) and `SolvikObject` (shape-backed instance with a runtime class
  reference and interop display);
- new nodes `org/solvik/truffle/nodes/`: `SolvikReadPropertyNode`, `SolvikWritePropertyNode`,
  `SolvikNewNode`, `SolvikInvokeMethodNode`; `SolvikDisplay` renders a user object as its class name;
- `org/solvik/lowering/SolvikLowering.java` lowers classes, methods, constructors, `this`, property
  reads/writes, construction, and method invocation, giving methods and constructors an implicit
  `this` frame slot;
- `language/src/main/java/module-info.java` exports `org.solvik.truffle.object` to the test module.

### Semantics and architecture implemented

- Classes are final and statically laid out: each class has a fixed, immutable property set;
  `val`/`var` mutability is enforced statically and undeclared member reads, writes, and calls are
  rejected as `SOLV-RESOL-004`;
- `init` is the sole constructor; calling a class name constructs an instance and runs declaration
  initializers in declaration order followed by the `init` body. A class with no `init` is valid
  only when every property has a declaration initializer (`SOLV-SEM-006`), and a class has at most
  one `init` (`SOLV-SEM-007`);
- every property without a declaration initializer must be definitely assigned on every successful
  constructor path before it is read: a path-sensitive set tracks definite assignment across
  blocks, `if`/`else`, loops, and early `return`, reporting `SOLV-TYPE-017` for a read before
  initialization, `SOLV-TYPE-018` for a missing assignment, and `SOLV-TYPE-006` for assigning a
  `val` property twice or after construction;
- instance methods and `init` receive an implicit `this` of the enclosing class type (`THIS` is
  rejected outside a class as `SOLV-RESOL-005`); `this.property`, `this.method(...)`, and
  unqualified method calls all resolve statically;
- objects use the Truffle shape/`DynamicObject` object model: every instance is allocated with the
  shared empty root shape and has exactly its declared properties added in declaration order, so
  instances of a class share one stable shape and no API can insert an undeclared member;
- construction, property access, and method dispatch execute through Truffle call targets and shape
  inline caches; `print`/`println` render an object as its class name; `==`/`!=` compare objects by
  identity.

### Tests added (286 Solvik tests total, up from 226)

- `SolvikClassParserTest` (8): full class shape, property initializer, `this` member call, implicit
  method call, semicolon-inserted class members, declaration ordering, `this` at end of line, and
  property/method source order;
- `SolvikClassSemanticTest` (11): class descriptors, construction typing and resolution, property
  read/write resolution, explicit and implicit method resolution, `this` typing, declaration
  initializers, method bodies with loops, main-less programs, `Object`/`Any` assignability, and
  method-call statements;
- `SolvikClassSemanticNegativeTest` (28): undeclared property read/write, non-class member access,
  `val` writes after construction and in `init`, double `val` assignment, read-before-init, missing
  initialization on a path, early return without initialization, class-without-init rules, wrong
  constructor/method argument type and arity, unknown/uncallable members, methods as values,
  assigning to methods, `this` outside a class, classes as values, duplicate property/method/name
  collisions, more than one `init`, duplicate class name, built-in name shadowing, property
  initializer mismatch, `init` returning a value, and missing method return paths;
- `SolvikClassExecutionTest` (8): construction with property/method access, declaration-initializer
  construction, post-construction mutation, implicit-`this` dispatch, immutable property reads,
  object display as class name, identity equality, and compile-error suppression of all output;
- `SolvikObjectModelTest` (3): stable shape sharing across instances, fixed property metadata, and
  the absence of undeclared members;
- `SolvikAstStructureTest.phaseSixNodeFamiliesAreProduced` pins `CLASS_DECL`, `PROPERTY_DECL`,
  `INIT_DECL`, and `THIS_EXPR`;
- `SolvikSemanticNegativeTest` replaces `memberAccessRequiresClasses` with
  `memberAccessOnNonClassIsRejected` (now `SOLV-RESOL-004`) and adds `classNamesAreNotValues`;
- `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins `THIS` as a
  terminator and `CLASS`/`INIT` as non-terminators.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 286 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 286 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 47.3s`;
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/ClassDemo.sol`
  printed `7`/`Douglas` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/ClassDemo.sol` printed the same
  and exited 0;
- generation reproducibility: rerunning `generate_parser.sh` regenerates the eight checked-in parser
  artifacts from the updated grammar; no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is
still only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl`
samples, the `simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI
references to `sl`).

### Known limitations carried into later phases

- Object properties are stored in object-typed Truffle shape locations, so `Int`/`Boolean` fields
  are boxed at the field boundary; primitive field locations are a possible later optimization.
- There is no inheritance, `open`/`override`, `super`, interfaces, or delegation yet (Phases 7–9);
  `class` is effectively final and all members are final.
- A method invoked from a constructor is not checked for reading a property the constructor has not
  yet initialized; definite-initialization analysis covers the constructor body only.
- Property initialization is recognized only for a `this.property` target; an aliasing write inside
  `init` (for example through a local `val` holding `this`) is checked for mutability but does not
  satisfy definite initialization.
- The specification's semicolon-insertion terminator list does not name `this`; phase 6 adds
  `THIS` to the table because `this` is a value-producing atom and otherwise could not terminate a
  statement. This is recorded as a required clarification rather than a language change.

## Phase 5 Evidence (completed 2026-09-16)

### Files changed

- Removed the inherited SimpleLanguage production path entirely: `language/src/main/java/com/oracle/truffle/sl/**`
  (grammar and generated parser, AST/runtime nodes, bytecode backend, builtins, function registry,
  `SLLanguage`/`SLContext`/launcher-facing registration, file detector), the inherited language test
  tree `language/src/test/java/com/oracle/truffle/sl/test/**`, the SimpleLanguage-only `tck/**`
  module (and its `<module>` entry in `pom.xml`), the root `sl` launcher wrapper, and
  `standalone/sl`;
- New Truffle runtime `language/src/main/java/org/solvik/truffle/`: `SolvikLanguage`,
  `SolvikContext`, `SolvikFileDetector`, `SolvikFunction`, `SolvikRootNode`, `SolvikEvalRootNode`,
  `SolvikTypes` (Truffle DSL type system), `SolvikUnit`, `SolvikDisplay`, `SolvikException`,
  `SolvikParseException` (plus generated `SolvikTypesGen` and `SolvikLanguageProvider`);
- New nodes `language/src/main/java/org/solvik/truffle/nodes/`: `SolvikStatementNode`,
  `SolvikExpressionNode`, literal nodes, `SolvikReadLocalVariableNode`/`SolvikWriteLocalVariableNode`,
  `SolvikBlockNode`, `SolvikIfNode`, `SolvikWhileNode`, `SolvikForNode`, `SolvikReturnNode`,
  `SolvikBreakNode`, `SolvikContinueNode` and their control-flow exceptions, `SolvikInvokeNode`,
  `SolvikPrintNode`/`SolvikPrintlnNode`, the Truffle-DSL arithmetic/comparison/equality/logical/unary
  nodes, and `SolvikAddNodeGen`-style generated factories;
- New backend-independent lowering `language/src/main/java/org/solvik/lowering/`: `LoweredProgram`
  and `SolvikLowering`;
- Semantic/type additions: `CheckedProgram` and `SolvikSemanticAnalyzer` now expose resolved name
  symbols and declare the predeclared `print`/`println` functions; `FunctionSymbol` models built-ins;
  `DiagnosticCode` adds `SOLV-LEX-003`; new `org/solvik/source/StringEscapes` validates and decodes
  normal-string escapes;
- Registration and build: `language/src/main/java/module-info.java` exports `org.solvik.truffle`,
  `org.solvik.truffle.nodes`, and `org.solvik.lowering` and provides `SolvikLanguageProvider`;
  `language/src/main/resources/META-INF/native-image/.../native-image.properties` initializes the
  Solvik classes at build time; `pom.xml` drops the `tck` module and points `launcherClass` at
  `org.solvik.launcher.SolvikMain`; the launcher module is now `org.solvik.launcher` with
  `SolvikMain`; `standalone/pom.xml` publishes `solvik` and builds `solviknative`; new `standalone/solvik`
  and root `solvik` scripts; `generate_parser.sh` no longer regenerates the SimpleLanguage grammar.

### Semantics and architecture implemented

- The Solvik production path is exactly `source -> semicolon-inserting lexer -> ANTLR parser -> syntax
  AST -> symbol/name/type analysis -> typed/lowered representation -> Truffle AST execution`.
  `SolvikLanguage.parse` parses and statically checks first and throws `SolvikParseException` on any
  error diagnostic, so an ill-typed program is never lowered and never produces a call target.
- Typed lowering consumes only the backend-independent `CheckedProgram`; it assigns typed frame
  slots (`Int`, `Boolean`, or `Object`) to parameters and locals, so primitive values are stored and
  operated on without boxing, and function calls go through `RootCallTarget`s resolved statically.
- Execution implements functions and recursion, locals, assignment, `if`/`else`, `while`, three-clause
  `for` (with `continue` running the update clause), `break`/`continue`, `return`, `Int` checked
  arithmetic with division-by-zero and overflow errors, `String` concatenation, ordering and equality,
  short-circuit `&&`/`||`, unary `!`/`-`, and the predeclared `print`/`println`.
- The runtime registers language id `solvik`, name `Solvik`, default MIME type and character MIME type
  `application/x-solvik`, and a `.sol` file detector. No `sl` language id, MIME type, parser, bytecode
  backend, or compatibility mode remains; representative SimpleLanguage syntax is rejected as
  `SOLV-PARS-004`.

### Tests added (226 Solvik tests total)

- `SolvikExecutionTest` (17): scalar `print`/`println`, precedence, division, overflow, string
  concatenation, comparisons, short-circuiting, locals and mutation, `if`/`else if`/`else`, `while`
  with `break`/`continue`, `for` update-after-`continue` and `break`, recursion, forward/mutually
  recursive calls, raw and escaped strings, no-`main` programs, and bare `return`;
- `SolvikExecutionNegativeTest` (9): compile errors are reported before any output, non-Boolean
  conditions, invalid entry points, SimpleLanguage `function` rejection, division-by-zero and overflow
  runtime errors, invalid escapes, and assignment-as-expression rejection;
- `SolvikLanguageRegistrationTest` (4): `solvik` id/name/MIME registration, absence of the `sl` alias,
  `.sol` files, MIME-typed sources, and no compatibility mode;
- `SolvikRuntimeStructureTest` (3): generated DSL primitive type helpers, primitive `executeInt`/
  `executeBoolean`, and generated factories for every specialized node;
- `SolvikAstStructureTest` scopes its engine-independence checks to the front end so the new Truffle
  backend is allowed to depend on Truffle.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 226 tests, 0
  failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 226 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 48.0s`;
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output language/tests/Fibonacci.sol`
  printed `55` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output language/tests/Fibonacci.sol` printed
  `55` and exited 0; both launchers reject `function main() {}` with `SOLV-PARS-004`;
- the Solvik grammar and generated parser artifacts were unchanged in this phase; no generated file
  was edited by hand.

### Remaining transitional SimpleLanguage code

The SimpleLanguage production language, parser, runtime nodes, bytecode backend, builtins, language
registration, tests, and TCK module are gone. What remains is non-executable naming/sample material
reserved for Phase 16: the `language/tests/*.sl` samples and `.output` files, the Maven artifact and Java
module names (`simplelanguage`, `org.graalvm.sl`), and documentation/CI references to `sl`
(`README.md`, `ci.jsonnet`, `standalone/README.md`, `versions-rules.xml`). No supported path executes
SimpleLanguage source.

### Known limitations carried into later phases

- Instrumentation wrappers and standard tag events are not wired; source sections are attached lazily
  so diagnostics and stack traces have locations, but `@GenerateWrapper`/instrumentation validation is
  deferred.
- The native-image Truffle feature required `@TruffleBoundary` on `print`/`println` and on `String`
  concatenation to avoid blocklisted JDK methods; Phase 6 must give user objects an interop display
  path instead of a generic `toString`.
- `SolvikUnit` is visible to interop as a null-like value, so evaluating a `Unit` program yields no
  result while `print`/`println` still render `Unit`.
- Only `Int`, `Boolean`, `String`, and `Unit` exist; `Long`, floating types, `Char`, nullability,
  classes, and collections are later phases. Parameters are immutable; the complete root hierarchy is
  still Phase 7.

## Phase 4 Evidence (completed 2026-09-16)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the static-core
  statement and operator grammar and documents the Phase 4 conventions in its header:
  assignment statements (assignment is a statement, never an expression), `while`, three-clause
  `for` with allowed omitted clauses, `break`, `continue`, and the static-core operator tiers
  unary `!`/`-`, `*`/`/`, `+`/`-`, ordering, equality, and short-circuit `&&`/`||`;
- regenerated parser artifacts (only via the `generate_parser.sh` Solvik section, byte-reproducible):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- new syntax-AST nodes `org/solvik/ast/statement/{AssignStmtNode,WhileStmtNode,ForStmtNode,BreakStmtNode,ContinueStmtNode}.java`
  and `org/solvik/ast/expression/{UnaryExprNode,UnaryOperator}.java`;
- `org/solvik/ast/AstKind.java` adds `ASSIGN_STMT`, `WHILE_STMT`, `FOR_STMT`, `BREAK_STMT`,
  `CONTINUE_STMT`, and `UNARY_EXPR`; `org/solvik/ast/expression/BinaryOperator.java` extends the
  operator set and classifies each operator by family;
- `org/solvik/parser/SolvikAstBuilder.java` builds the new statement nodes and the full operator
  precedence tower, with `for`-clause nodes excluding their separating semicolons;
- `org/solvik/parser/SemicolonInsertingTokenSource.java` adds `break` and `continue` to the
  newline-terminator table (specification condition 2);
- `org/solvik/diagnostic/DiagnosticCode.java` adds the `SOLV-RESOL-*`, `SOLV-TYPE-*`, and
  `SOLV-SEM-*` diagnostic codes for the new layers;
- new `org/solvik/type/` package: `Type`, `AnyType`, `ObjectType`, `NothingType`, `UnitType`,
  `BooleanType`, `StringType`, `IntType`, `FunctionType`, `TypeEnvironment`;
- new `org/solvik/semantic/` package: `Symbol`, `VariableSymbol`, `FunctionSymbol`, `Scope`,
  `SymbolTable`, `CheckedProgram`, `SemanticResult`, `SolvikSemanticAnalyzer`;
- `language/src/main/java/module-info.java` exports `org.solvik.type` and `org.solvik.semantic`
  to the test module.

### Semantics and architecture implemented

- An explicit, backend-independent compile-time type model with nominal identity: `Any` is the top
  type, `Object` the root of class values, `Nothing` the bottom type, and `Int`, `Boolean`,
  `String`, and `Unit` its built-in subtypes. `FunctionType` exists only to type a call's callee;
  functions remain non-first-class and a bare function name is an error outside callee position.
- Two-pass static analysis: declaration collection over source-file functions, then per-function
  body checking. Top-level functions live in the outermost scope, so a body may call any declared
  function, including forward declarations; parameters and blocks push nested scopes, so a nested
  declaration may shadow an outer one but same-scope duplicates are errors.
- Name and type resolution: parameter and return-type references resolve against the built-in type
  environment; unknown types and unknown names produce source-located diagnostics.
- Local declarations infer their type from the initializer when no annotation is present and check
  the initializer against an explicit annotation; `val`/parameter bindings are immutable, `var`
  locals mutable; assignment requires a mutable local target and a value assignable to its type.
- Operator typing for the Phase 4 core: `Int` arithmetic and ordering (`Boolean` result), `String +
  String`, `==`/`!=` on assignment-compatible operands (`Boolean` result), short-circuit
  `&&`/`||` and unary `!` on `Boolean`, and unary `-` on `Int`; mixed or unsupported operands are
  rejected.
- Boolean checking for `if`, `while`, and `for`; loop-control statements are rejected outside a
  loop; the `for` initializer must be a declaration or assignment and the update clause an
  assignment.
- Return validation: a bare `return` requires a `Unit` function, a value must be assignable to the
  declared return type, and a non-`Unit` function must return on every path (conservative: a loop
  never counts as a guaranteed return).
- Entry-point validation: a declared `main` must have no parameters and return `Unit`; a program
  without `main` remains valid, which the canonical `add` proof requires.
- Definite-assignment tracking is implemented on variable symbols and reads of a not-yet-initialized
  binding are rejected; because every Phase 4 local declaration requires an initializer, the
  reachable Phase 4 cases are use-before-declaration, which the scope ordering already rejects.
- A successful analysis produces a `CheckedProgram` of compiler facts (function symbols, per-
  expression types, local symbols, validated entry point) and never executable nodes; any error
  diagnostic yields `SemanticResult.failure` with no program, so an ill-typed program cannot be
  lowered or executed. Member access and method calls remain parseable but are rejected with
  `SOLV-TYPE-015` until class declarations exist.

### Tests added (192 Solvik tests total; three obsolete Phase-1 negatives removed)

- `SolvikControlFlowParserTest` (10): assignment statement shape/span, `while`, three-clause `for`
  with exact clause spans, all-clauses-omitted `for`, assignment initializer, `break`/`continue`,
  structural unary/ordering/equality/logical precedence, and statement ordering;
- `SolvikSemanticTest` (13): the canonical `add` proof, every value expression typed in a rich
  program, entry-point acceptance, inference/mutability, nested shadowing, `for` scoping, `Any`
  assignability, operator result types, string concatenation, raw-string `String` type, call typing,
  forward references, and loop control inside loops;
- `SolvikSemanticNegativeTest` (39): every `SOLV-RESOL-*`, `SOLV-TYPE-*`, and `SOLV-SEM-*` failure
  the phase can produce, each asserting the first diagnostic code and that no program is produced;
- `SolvikTypeModelTest` (4): built-in name resolution, the specified subtype hierarchy, the bottom
  type, and `FunctionType` shape/identity;
- `SolvikAstStructureTest.phaseFourNodeFamiliesAreProduced` pins the new node kinds;
- `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins `break` and
  `continue` as terminators and the new operator tokens as non-terminators;
- removed `SolvikParserNegativeTest.whileLoopIsOutOfPhaseOneScope`,
  `unaryMinusIsOutOfPhaseOneScope`, and `comparisonOperatorsAreOutOfPhaseOneScope`, and replaced
  `assignmentAsExpressionIsRejected` with `assignmentIsNotAnExpression`, because Phase 4 makes those
  constructs valid and assignment must still be rejected inside an expression.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 192 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 630 language tests (1 inherited environment-dependent skip),
  15,180 TCK tests, 0 failures/errors;
- generation reproducibility: rerunning the Solvik section of `generate_parser.sh` regenerates all
  eight checked-in parser artifacts byte-for-byte (verified by SHA-256 before and after);
- `./build-native.sh`: BUILD SUCCESS (01:04 min); the native launcher
  `standalone/target/slnative` regenerated after the `module-info.java` export additions,
  confirming the new front-end classes do not break native-image analysis.

### Remaining transitional SimpleLanguage code

Nothing new was removed and no execution path changed: the inherited SimpleLanguage grammar,
parser, launcher, registration, samples, and tests remain scaffolding. Semantic analysis runs only
inside Solvik tests; `./standalone/target/sl` still parses only SimpleLanguage, confirming Solvik is
neither lowered nor executed.

### Known limitations carried into later phases

- Parameters are treated as immutable bindings, and assignment to a parameter produces
  `SOLV-TYPE-006`. The specification calls the assignment target a "mutable local or `var`
  property" and never declares parameters `var`; the safe reading is chosen and must be revisited
  if a later phase specifies parameter mutability.
- Every local declaration requires an initializer, so the definite-assignment flag is structurally
  present but no uninitialized read is reachable until class properties without initializers arrive
  in Phase 6.
- Member access, method calls, `print`/`println`, `??`/`?.`/`is`/`as`, numeric types beyond `Int`,
  classes, and `List` are not analyzed yet; member access is explicitly rejected as unsupported.
- Return-path validation is conservative: a loop is never assumed to guarantee a return, so
  `fun f(): Int { while (true) {} }` is reported as missing a return path.

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`:
  `RAW_STRING_LITERAL` is a lexer rule matching the contiguous opening delimiter `'r' '#'* '"'`
  whose action scans the counted body in a new `@lexer::members` helper, because ANTLR cannot express
  an arbitrary counted delimiter directly; document the Phase 3 conventions in the file header;
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- new `org/solvik/parser/UnterminatedRawStringException.java` carries the expected closing delimiter
  through the ordinary ANTLR error dispatch so the diagnostic layer can classify the failure;
- new `org/solvik/ast/expression/RawStringLiteralNode.java` exposes the lexeme, the hash count, and
  the unescaped content between the delimiters;
- `org/solvik/ast/AstKind.java` adds `RAW_STRING_LITERAL`;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `LEXER_UNTERMINATED_RAW_STRING`
  (`SOLV-LEX-002`);
- `org/solvik/parser/SolvikAstBuilder.java` builds a raw-string literal node when the token is
  present;
- `org/solvik/parser/SolvikErrorListener.java` maps `UnterminatedRawStringException` to
  `SOLV-LEX-002`, with the primary span covering the opening delimiter and the expected closing
  delimiter recorded as the expected value and named in the message;
- `org/solvik/parser/SemicolonInsertingTokenSource.java` adds `RAW_STRING_LITERAL` to the
  newline-terminator table, since a raw string is a literal for specification condition 2;
- `generate_parser.sh` documents the Phase 3 lexer-action convention.

### Semantics and architecture implemented

- The lexer recognizes only a contiguous `r` + zero or more `#` + `"` prefix; `r` followed by
  anything else (including no quote, e.g. `r#foo`) stays an identifier. The action consumes the body
  through the ATN simulator, so ANTLR's line/column tracking stays correct across embedded newlines;
- the opening delimiter fixes `N`; the literal closes on the first `"` followed by exactly `N` `#`
  characters and no further `#`. A quote with a different hash count is content, so both
  `r##"abc"#` and `r#"abc"##` are unterminated rather than silently closed;
- one token is emitted for the complete literal. Backslashes, quotes, and physical newlines are
  preserved verbatim with no escape or interpolation processing; `RawStringLiteralNode.value()`
  is exactly the content between the delimiters;
- an unterminated raw string consumes to end of input and reports `SOLV-LEX-002` at the opening
  delimiter, naming the exact expected closing delimiter (e.g. `"##`);
- raw-string internal newlines are part of the single token, so they never become `NEWLINE` tokens
  and never participate in semicolon insertion; a real newline after the literal terminates the
  statement like any other literal.

### Tests added (22 new tests; 128 Solvik tests total; one obsolete Phase-1 test removed)

- `SolvikRawStringTest` (22): spec examples are single tokens with exact lexemes/spans; empty raw
  strings for every hash count; backslashes preserved; embedded quotes/hashes as content; multiline
  raw string is one token with no internal `NEWLINE` tokens; `r`/`raw`/`rust`/`r2` stay identifiers;
  `r#` without a quote stays an identifier; unterminated zero-hash and counted literals report
  `SOLV-LEX-002` at the opening delimiter and name `"` / `"###`; too-few and too-many closing hashes
  are mismatches; failed literals expose no AST; AST lexeme/hash-count/value/span; zero-hash node;
  raw strings in local initializers and call arguments; regex/JSON/path/SQL spec examples; token
  stream shows internal newlines invisible to insertion; newline after a literal terminates; comment
  after a literal; EOF terminator; multiline literal stays one statement; normal strings still reject
  unescaped newlines; non-contiguous `r #"..."#` is rejected;
- `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins
  `RAW_STRING_LITERAL` as a terminator;
- `SolvikParserNegativeTest.rawStringSyntaxIsRejectedBeforePhaseThree` was deleted because raw
  strings are a supported Solvik construct from this phase on.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o test -pl language -Dtest='Solvik*Test'`: 128 tests,
  0 failures/errors/skips;
- `JAVA_HOME=/opt/graalvm ./mvnw -o test -pl language`: 566 tests (1 inherited environment-dependent
  skip), 0 failures/errors;
- `./build.sh`: BUILD SUCCESS; 566 language tests (1 inherited environment-dependent skip), 15,180
  TCK tests, 0 failures/errors;
- generation reproducibility: rerunning the Solvik section of `generate_parser.sh` regenerates all
  eight checked-in parser artifacts byte-for-byte (verified by hashing before and after);
- `./build-native.sh` was not required: this phase changed only the front end, not runtime,
  registration, launcher, or native-image configuration.

### Remaining transitional SimpleLanguage code

Nothing new was removed and no execution path changed: the inherited SimpleLanguage grammar, parser,
launcher, registration, samples, and tests remain scaffolding. Solvik raw strings are parsed to the
syntax AST but are neither type-checked nor executed yet, and `./standalone/target/sl` still parses
only SimpleLanguage.

### Known limitations carried into later phases

- Raw string tokens are now part of the semicolon-insertion terminator table; when more literal
  forms arrive they must join it the same way;
- `[`/`]` and `?.` are still lexed only for insertion decisions and remain parse errors until their
  grammar phases;
- raw strings are unescaped values in the AST but no static typing, lowering, or execution exists
  yet, so their `String` type is not recorded until Phase 4/5.

## Phase 2 Evidence (completed 2026-09-16)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`:
  `WS` now skips only horizontal whitespace; physical newlines became hidden `NEWLINE` tokens
  (`\r\n`, `\r`, `\n`) and comments moved from `skip` to hidden tokens so insertion can see
  newlines inside block comments; added `NULLABLE_DOT`, `LBRACKET`, `RBRACKET` lexer tokens
  (no parser rule consumes them yet); statement lists (`compilationUnit`, `block`) tolerate
  redundant stand-alone `SEMI` tokens so explicit and synthesized semicolons can coexist;
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- new token-stream stage `org/solvik/parser/SemicolonInsertingTokenSource.java` implementing
  LANGUAGE_SPEC section 16 conditions 1–3, boundary coalescing and the end-of-file rule;
- `org/solvik/parser/SolvikParser.java` now feeds the lexer through that stage into a
  `CommonTokenStream`;
- `language/src/main/java/module-info.java` additionally exports `org.solvik.parser.generated`
  to the test module so token-stream tests can use the generated lexicon;
- `generate_parser.sh` documents the Phase 2 lexer conventions.

### Semantics and architecture implemented

- Semicolon insertion happens in a token-source layer between lexer and parser, as required by
  `docs/ARCHITECTURE.md`. Synthetic `SEMI` tokens share the grammar's single `SEMI` token type and
  sit on the default channel, so the parser cannot distinguish them from explicit `;`.
- Synthetic tokens are placed zero-width immediately after the terminated token
  (`startIndex = terminated.stop + 1`, `stopIndex = startIndex - 1`); AST statement spans therefore
  end at the last real token of a statement, never at the following newline. Their line/column
  locate the physical newline that triggered insertion.
- Conditions implemented exactly: unmatched `(` and `[` depths must both be zero; the preceding
  significant token must be an identifier, literal, `return`, `)`, `]`, or `}`; the next
  significant token must not be `.`, `?.`, or `else` (the sole lookahead exception).
- Boundary handling: runs of newlines, blank lines and comment newlines collapse to one insertion;
  a preceding explicit `;` suppresses the next boundary; newlines inside block comments count as
  physical newlines; comments are transparent to the continuation lookahead; CRLF, CR, and LF all
  terminate; end of file applies the rule without the lookahead exception exactly once whether or
  not a trailing newline exists.
- Insertion is decided solely from the raw lexer token sequence: the stage references no parser,
  error strategy, or diagnostic, so it is deterministic and independent of parser errors and
  recovery, and explicit and synthesized forms parse to equivalent ASTs.
- Phase boundaries respected: raw strings, loops, bracket syntax, and nullable member access are not
  implemented. `?.` and `[`/`]` are lexed only to drive insertion decisions; the parser still
  rejects programs that use them.

### Tests added (41 new tests; 107 Solvik tests total)

- `SolvikSemicolonTokenStreamTest` (24): explicit-versus-newline token-sequence equivalence;
  synthetic token type/channel/zero-width placement and coordinates; blank-line coalescing;
  multiline expressions after operators and commas; unmatched and nested `(` and `[` suppression;
  EOF with/without newline and after an explicit `;`; `return`-newline termination; `}` followed by
  `else`; leading-dot chains; `?.` suppression at the token-stream layer (Phase 10 pending); lone `?`
  still a lexical error; newline inside block comments; line comments; `.`/`else` lookahead across
  comments; CRLF/CR; parser-error independence; terminator and continuation tables; run-to-run
  determinism.
- `SolvikSemicolonInsertionTest` (17): newline form versus explicit-semicolon twin shape equality
  with exact span slices for locals, returns, member chains, dangling `else`, multiline expressions,
  blank/comment lines, CRLF, and no-explicit-semicolon programs; standalone `;` produces no AST
  statements; negatives for concatenated statements, operator at end of line, unclosed `(`, bracket
  syntax, and `?.` chains.
- `SolvikParserNegativeTest`: the three obsolete “missing explicit `;`” tests were replaced by
  ASI-aware negatives (unterminated statement inside an unclosed `(`; same-line statement
  concatenation; line ending in an operator).
- `SolvikTestSupport.parseFails` helper; refreshed javadoc in `SolvikParserTest`.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -q -o test -pl language -Dtest='Solvik*Test'`: 107 tests,
  0 failures/errors;
- `JAVA_HOME=/opt/graalvm ./mvnw -q -o test -pl language`: all language tests pass;
- `./build.sh`: BUILD SUCCESS; 545 language tests (438 inherited + 107 Solvik, 1 inherited
  environment-dependent skip), 15,180 TCK tests, 0 failures/errors;
- `./build-native.sh`: BUILD SUCCESS; native launcher `standalone/target/slnative` still runs
  `language/tests/Fibonacci.sl` (exit 0, matching output);
- generation reproducibility: rerunning the Solvik section of `generate_parser.sh` regenerates all
  eight checked-in parser artifacts byte-for-byte.

### Remaining transitional SimpleLanguage code

Nothing new was removed. The launcher, registration, samples, and inherited tests still belong to
SimpleLanguage, and Solvik code is parsed to an AST but not lowered or executed:
`./standalone/target/sl /tmp/solvik_smoke.sol` still rejects `fun` with the inherited parser
(`missing 'function' at 'fun'`), confirming Solvik syntax is not yet an execution path.

### Known limitations carried into later phases

- `break` and `continue` keyword tokens do not exist yet (Phase 7); when they are introduced they
  must join the newline-terminator table and their bracket/loop peers in
  `SemicolonInsertingTokenSource`.
- `[`/`]` and `?.` are lexed only for insertion; they remain parse errors until their grammar
  phases.
- The inherited SL grammar and parser are unchanged and still discard newlines; only the Solvik
  front end implements semicolon insertion.

## Phase 1 Evidence (completed 2026-09-16)

### Files changed

- New grammar source: `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`;
- New generated parser (produced only by `generate_parser.sh`, package `org.solvik.parser.generated`):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`, `.interp`/`.tokens`;
- New hand-written front end: `org/solvik/source/{SourceSpan,LineColumn,SourceFile}.java`,
  `org/solvik/diagnostic/{Diagnostic,DiagnosticBag,DiagnosticCode,DiagnosticSeverity}.java`,
  `org/solvik/ast/{AstKind,AstNode,CompilationUnitNode}.java`,
  `org/solvik/ast/declaration/{DeclarationNode,FunctionDeclNode,ParameterNode,TypeRefNode}.java`,
  `org/solvik/ast/statement/{StatementNode,BlockNode,BindingKind,LocalDeclNode,IfStmtNode,ElseBranchNode,ReturnStmtNode,ExprStmtNode}.java`,
  `org/solvik/ast/expression/{ExpressionNode,BinaryOperator,BinaryExprNode,CallExprNode,MemberAccessExprNode,ParenExprNode,NameRefExprNode,LiteralNode,IntLiteralNode,BoolLiteralNode,StringLiteralNode}.java`,
  `org/solvik/parser/{SolvikParser,SolvikParseResult,SolvikAstBuilder,SolvikErrorListener}.java`;
- New tests in `language/src/test/java/org/solvik/test/`: `SolvikTestSupport`, `SolvikParserTest`,
  `SolvikParserNegativeTest`, `SolvikAstStructureTest`, `SolvikDiagnosticFrameworkTest`;
- Modified: `language/src/main/java/module-info.java` (test-only exports of `org.solvik.*`),
  `generate_parser.sh` (documented Solvik generation section with UPL-header postprocessing).

### Semantics and architecture implemented

- Immutable, Truffle-free syntax AST with authoritative half-open `[startOffset, endOffset)` spans
  (line/column derived for display only via `SourceFile`).
- Diagnostic framework: stable codes (`SOLV-LEX-*`, `SOLV-PARS-*`), severity, primary span, optional
  expected/found; error results expose no AST at all.
- Supported syntax exactly as scoped: top-level `fun` with typed parameters and explicit return type,
  blocks, `val`/`var` locals with required explicit `;`, call-expression statements, Int/Boolean/
  normal-string literals, name references, ordinary member access, calls (left-folded chains),
  parentheses, `+ - * /` with precedence encoded structurally and left associativity, `if`/`else` /
  chained `else if`, `return [expr];`.
- SimpleLanguage `function` is rejected as `SOLV-PARS-004` legacy syntax; raw strings, `while`, unary
  operators, comparisons, and assignment-as-expression are all rejected before their phases.
- No lowering, no execution, and no static typing of Solvik code exists yet; the front end never
  references the inherited parser or any Truffle node type (asserted by structure tests).

### Tests added (all positive/negative pairs)

66 new JUnit tests: `SolvikParserTest` (20; shapes, exact span text, precedence/associativity, call
folding, paren retention, if/else nesting, immutability, determinism, span-nesting and sibling-order
invariants), `SolvikParserNegativeTest` (23; `function` keyword rejection, missing explicit `;` for
return/local/expr statements, malformed declarations/parameters, top-level statements, assignment
expressions, EOF truncation, lexical errors, out-of-scope syntax, and "no partial AST" checks),
`SolvikAstStructureTest` (10; final fields, final classes, no mutators, no Truffle/Graal imports or
execution API names anywhere under `org/solvik`, required node families),
`SolvikDiagnosticFrameworkTest` (13; span algebra, line-break styles including `\r\n`, offset
round-trip, diagnostic fields/order/builder rules, unique stable codes).

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw test -pl language -Dtest='Solvik*Test'`: 66 tests, 0 failures/errors;
- `./build.sh`: BUILD SUCCESS; 504 language tests (438 inherited + 66 new) with the single inherited
  environment-dependent skip, 15,180 TCK tests, 0 failures/errors;
- `./build-native.sh`: BUILD SUCCESS (01:03) after the `module-info.java` change;
- Generation reproducibility: rerunning the Solvik section of `generate_parser.sh` regenerates all
  eight checked-in parser artifacts byte-for-byte.

### Remaining transitional SimpleLanguage code

Everything listed in the removal inventory remains in place as scaffolding: the inherited
SimpleLanguage grammar/parser, direct Truffle/Bytecode lowering, `sl` registration, launcher, samples,
and tests. The Solvik front end is wired to nothing but its own tests; `./standalone/target/sl`
still parses only SimpleLanguage (verified: a `.sol` file containing `fun` is rejected by the
inherited parser with `missing 'function'`), which confirms Solvik syntax is not yet lowered or
executed.

## Phase 0 Evidence (completed 2026-09-16)

See `docs/BASELINE.md` for the full inventory. Recorded evidence:

- `/opt/graalvm/bin/java -version`: Oracle GraalVM 25.3.4.1+1.1, `java version "25.0.4.1" 2026-08-18 LTS` (JVMCI 25.3-b22);
- `JAVA_HOME=/opt/graalvm ./mvnw -version`: Apache Maven 3.9.16 running on Java 25.0.4.1 from `/opt/graalvm-25.3.4.1+1.1`;
- `./build.sh`: BUILD SUCCESS; 438 language tests (0 failures, 0 errors, 1 inherited environment-dependent skip in `SLSeparatedClassLoadersTest`), 15,180 TCK tests (0 skipped);
- `./build-native.sh`: BUILD SUCCESS (01:05 min), same test totals, native launcher produced at `standalone/target/slnative` (`native-image 25.0.4.1 2026-08-18`, Substrate VM 25.3.4.1+1.1);
- JVM smoke test: `JAVA_HOME=/opt/graalvm ./standalone/target/sl language/tests/Fibonacci.sl` exited 0; program output matches `language/tests/Fibonacci.output` after removing the standard engine banner line;
- Native smoke test: `JAVA_HOME=/opt/graalvm ./standalone/target/slnative language/tests/Fibonacci.sl` exited 0 with empty stderr and the same matching output;
- Parser generation: ANTLR 4.13.2 via `generate_parser.sh` with `JAVA_HOME=/opt/graalvm`; regeneration into a scratch directory reproduced the grammar outputs modulo the checked-in UPL header, narrowed `@SuppressWarnings`, and stale `_localctx.field` vs `((XxxContext)_localctx).field` context-field syntax (documented in `docs/BASELINE.md`; no files changed in Phase 0).

No code changes were required to make the baseline reproducible; no pre-existing failures were found.

## Current Repository State

Solvik is the only supported language and executes end to end:

- `SolvikLanguage` is registered as `solvik` / `application/x-solvik` with a `.sol` file detector, and
  the inherited SimpleLanguage language, parser, nodes, bytecode backend, builtins, tests, and TCK
  module are removed;
- the production path is the required one: semicolon-inserting lexer, ANTLR parser, syntax AST,
  name/type analysis, typed lowering, and Truffle AST execution; static analysis always runs before
  lowering and a program with any compile-time error produces no call target;
- the executable core is the Phase 4 static core plus `print`/`println`, the Phase 6 object
  system, and the Phase 7 root hierarchy and single inheritance: functions and recursion, typed
  locals and assignment, `if`/`while`/`for`, `break`/`continue`, `return`, checked arithmetic on
  `Byte`/`Short`/`Int`/`Long` and IEEE 754 `Float`/`Double`, explicit numeric conversions,
  `String` concatenation, `Char` values, ordering/equality, short-circuit logical operators, unary
  operators, final-by-default class declarations with `val`/`var` properties, `init`, instance
  methods, `this`, construction, `open class`/`extends`, `override` with virtual dispatch, and
  `super`;
- the JVM (`standalone/target/solvik`) and native (`standalone/target/solviknative`) launchers run
  Solvik programs and expose no SimpleLanguage alias;
- remaining SimpleLanguage material is non-executable naming and samples reserved for Phase 16: the
  `language/tests/*.sl` samples, the `simplelanguage`/`org.graalvm.sl` artifact and module names, and
  documentation/CI references to `sl`.

## SimpleLanguage Removal Inventory

| Inherited surface | Required disposition |
|---|---|
| Grammar and generated parser | Replace with Solvik grammar and parser during Phases 1–3; remove the old production parser path in Phase 5 |
| Direct parser-to-Truffle lowering | Replace with AST, semantic analysis, typed lowering, and Truffle AST lowering by Phase 5 |
| Bytecode DSL selection and parser | Do not port to Solvik; remove exposed selection and execution paths by Phase 5 |
| Dynamic typing and implicit local creation | Remove as Solvik static typing lands in Phase 4 |
| Dynamic object member insertion/removal | Remove from supported semantics in Phase 6 |
| `sl` language id, MIME type, launcher behavior, and launcher filenames | Replace with `solvik`, `application/x-solvik`, and Solvik-only launchers in Phase 5 |
| SimpleLanguage samples and semantic tests | Replace phase by phase; remove remaining examples and tests by Phase 16 |
| Java packages, Maven artifact names, scripts, and filenames | Rename incrementally; finish by Phase 16 |
| Copyright and historical attribution | Preserve |

Do not add a compatibility mode to ease this removal.

## Phase Completion Template

When a phase passes:

1. list files changed;
2. list semantics and architecture implemented;
3. list positive and negative tests;
4. record targeted-test and required build-wrapper results;
5. list remaining transitional SimpleLanguage code;
6. set `NEXT` to the immediately following phase;
7. stop.
