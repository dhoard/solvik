# Solvik Implementation Status

This file is the phase handoff. Update it only after running the commands required by the active phase.

## Phase

- `NEXT`: Phase 15 — Non-Fallthrough switch
- Completed phases: Phase 0 (baseline) 2026-09-16; Phase 1 (front-end skeleton) 2026-09-16;
  Phase 2 (lexical semicolon insertion) 2026-09-16; Phase 3 (raw strings) 2026-09-16;
  Phase 4 (name resolution and static core) 2026-09-16;
  Phase 5 (typed lowering and first Solvik execution) 2026-09-16;
  Phase 6 (classes and objects) 2026-09-16;
  Phase 7 (root hierarchy and single inheritance) 2026-09-16;
  Phase 8 (interfaces and defaults) 2026-09-16;
  Phase 9 (delegation) 2026-09-17; Phase 10 (null safety) 2026-09-17;
  Phase 11 (generics) 2026-09-17; Phase 12 (enums and sealed types) 2026-09-17;
  Phase 13 (exhaustive match) 2026-09-17; Phase 14 (regex) 2026-09-17
- Last verified commit: `4451e76` plus the uncommitted Phase 14 working tree
- Last clean JVM build: `./build.sh` passed on 2026-09-17 (784 Solvik language tests, 0 failures,
  0 errors, 0 skips)
- Last clean native build: `./build-native.sh` passed on 2026-09-17 (Phase 14, `Finished generating
  'solviknative' in 1m 4s`); the `standalone/target/solviknative` launcher ran a Phase 14 regex
  program (output `true`/`false`/`count=42`/`0`/`42`/`3`/`1`/`22`/`333`/`a#b#`/`Regex`/`RegexMatch`,
  empty stderr, exit 0)

An implementation run must execute only `NEXT`. It must not start the following phase.

After Phase 16 satisfies every exit criterion, replace the phase value with `- \`NEXT\`: COMPLETE`. `workflow.sh` treats that value as the only successful terminal state.

## Phase 14 Evidence (completed 2026-09-17)

### Files changed

- New `org.solvik.regex` package: `RegexSyntax` owns the portable dialect. It recursive-descent
  validates a pattern (`unsupported(String)` reports the first violation) and compiles it
  (`compile(String)` returns a `RegexPattern`), rejecting backreferences, lookaround, embedded flags,
  non-capturing/named groups, possessive/reluctant quantifiers, character-class intersection,
  anchor/Unicode escapes, and every other engine-specific extension before the engine sees the
  pattern. `RegexPattern` is the immutable `source` + compiled-engine `Pattern` pair, created once
  per compiled pattern. `RegexSyntax.InvalidPatternException` carries the user-facing message.
- Type model: new `org.solvik.type.RegexType` and `org.solvik.type.RegexMatchType`, both non-generic
  `Object`-rooted built-ins; `TypeEnvironment` registers them (after `Unit`, before `List`);
  `SolvikRuntimeTypes` handles both in `is`/`as` tests.
- Semantic: `SolvikSemanticAnalyzer` types `Regex(pattern)` construction (exactly one `String`
  argument) and the four `Regex` methods (`matches` -> `Boolean`, `find` -> `RegexMatch?`,
  `findAll` -> `List<RegexMatch>`, `replace` -> `String`) plus the four immutable `RegexMatch`
  properties and `group(index: Int): String?`; it rejects construction of `RegexMatch`, unknown
  members, wrong arity/types, member-as-value, immutable-property writes, and nullable dereference.
  A constant `String`/raw-string argument (parentheses transparent) is validated and compiled once
  during analysis; an invalid or non-portable constant produces `SOLV-TYPE-035`. `CheckedProgram`
  records the compiled constant per construction (`regexConstants` / `regexConstantOf`); a dynamic
  pattern is absent.
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_INVALID_REGEX_PATTERN` (`SOLV-TYPE-035`).
- Lowering/runtime: `SolvikLowering` lowers a constant construction to `SolvikRegexLiteralNode`
  (holds one compiled pattern and one runtime value for every execution) and a dynamic one to
  `SolvikRegexCreateNode` (validates and compiles on first execution, caching the last pattern); it
  lowers `matches`/`find`/`findAll`/`replace`, `group`, and the four `RegexMatch` property reads to
  dedicated nodes with `?.` short-circuit support. New `org.solvik.truffle.object.SolvikRegex` and
  `SolvikRegexMatch` and nodes `SolvikRegexLiteralNode`, `SolvikRegexCreateNode`,
  `SolvikRegexMatchesNode`, `SolvikRegexFindNode`, `SolvikRegexFindAllNode`,
  `SolvikRegexReplaceNode`, `SolvikRegexMatchReadNode`, and `SolvikRegexGroupNode`.
  `SolvikDisplay` renders a `Regex`/`RegexMatch` as its type name and `SolvikException` adds
  `regexError` for an invalid dynamic pattern; `module-info.java` exports `org.solvik.regex` to the
  test module.

### Semantics and architecture implemented

- `Regex` and `RegexMatch` are non-generic built-in classes under `Object`, so they are `Any`/`Object`
  subtypes, usable as parameter/return/property types, and testable with `is`/`as`; they cannot be
  extended or implemented and `RegexMatch` cannot be constructed, both statically rejected. Equality
  is identity (they are ordinary class values, not scalars or enums) and `print` renders their type
  names; no `Any` escape hatch is used anywhere.
- The dialect is deliberately portable (docs/LANGUAGE_SPEC.md section 14): literals, `.`, `^`, `$`,
  character classes and ranges, capturing groups, alternation, `*`, `+`, `?`, `{m}`, `{m,}`,
  `{m,n}`, the ASCII classes `\d \s \w` and their negations, escaped punctuation, and `\n \r \t`.
  Everything else is a diagnostic for a constant pattern and a Solvik runtime regex error for a
  dynamic one, so the engine cannot leak engine-specific behavior into the language.
- `matches` requires the complete input; `find` returns the first non-overlapping match or `null`;
  `findAll` returns every non-overlapping match left-to-right as the built-in immutable `List` with
  elements typed `RegexMatch` (`size`/`get` reuse the Phase 11 list typing); `replace` replaces every
  non-overlapping match and treats the replacement as literal text (`Matcher.quoteReplacement`), so
  capture substitution remains deferred as specified. `RegexMatch` exposes zero-based, exclusive-end
  `start`/`end`, `groupCount`, `value`, and `group(index)` where a non-participating group is `null`
  and an out-of-range index raises a Solvik runtime bounds error.
- Constant patterns are compiled exactly once per source constant: analysis stores the `RegexPattern`
  in the `CheckedProgram` and lowering builds one literal node, so evaluating the construction inside
  a loop never recompiles. A dynamically constructed pattern is compiled on first execution and the
  last pattern is cached; an invalid one raises `regex error`. The engine stays behind `RegexSyntax`,
  `RegexPattern`, and the two `Solvik*` runtime values.
- `?.`/`??` and flow narrowing compose with the new members: `re?.matches(s)` is `Boolean?` and
  short-circuits argument evaluation, `m?.value` is `String?`, and `m?.group(1) ?? "none"` narrows.

### Tests added (784 Solvik tests total, up from 718)

- `SolvikRegexPatternTest` (13): accepted literals/wildcards/anchors, classes/ranges, groups and
  alternation, every quantifier form, ASCII classes and negations, escaped punctuation and control
  characters, compiled matching and non-matching behavior, and rejections for lookaround, all group
  extensions, embedded flags, backreferences, anchor/Unicode/`\R` escapes, possessive/reluctant
  quantifiers, class intersection, and structural errors (with a reported pattern index);
- `SolvikRegexSemanticTest` (13): the `Regex`/`RegexMatch` types and their `Object`/`Any`
  subtyping, every method and property result type, `findAll` element/size typing, one-time constant
  compilation for raw and normal strings, absence of a static constant for a dynamic pattern,
  parentheses around a constant, `is`/`as` typing, nullable safe-access result types, and values
  flowing through functions and explicit bindings;
- `SolvikRegexNegativeTest` (24): construction arity and argument-type errors including `null`,
  invalid constant patterns (syntax, reversed repetition, lookaround, backreference, embedded
  flags), `RegexMatch` construction, bare `Regex`-as-value, unknown `Regex`/`RegexMatch`
  methods/properties, wrong method argument types and arity, member-as-value, method/property
  assignment, nullable dereference, and rejecting `extends Regex`/`implements RegexMatch`;
- `SolvikRegexExecutionTest` (16): full-input `matches`, raw-string patterns, `find` value/offsets/
  `groupCount`/groups, `find` returning `null`, group zero, non-participating groups, `findAll`
  iteration and ordering, literal `replace` (including a `$1` replacement), constant patterns in a
  loop, regex values through functions, nullable safe access, `Regex`/`RegexMatch` display, `is`
  execution, invalid-syntax and unsupported dynamic patterns raising guest regex errors with no
  output, and an out-of-range group raising a bounds error;
- `SolvikTypeModelTest.allBuiltinsResolveByName` now pins `Regex` and `RegexMatch`.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 784 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 784 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 1m 4s` (run because the
  phase adds runtime representations and execution nodes). An earlier native run failed with two
  runtime-compilation blocklist violations on the bounds-error message construction in
  `SolvikList.get` and `SolvikRegexMatch.group`; moving each message build into a `@TruffleBoundary`
  helper fixed both and the final builds are clean;
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase14Demo.sol`
  printed `true`/`false`/`count=42`/`0`/`42`/`3`/`1`/`22`/`333`/`a#b#`/`Regex`/`RegexMatch` and
  exited 0; native launcher smoke test printed the same lines, wrote no stderr, and exited 0;
- negative launcher checks on both launchers: a non-portable constant (`Regex(r#"(?=x)"#)`) exits 1
  with `SOLV-TYPE-035` and no program output; an invalid dynamic pattern (`Regex(make())` with `"("`)
  exits 1 with `regex error: unclosed group at pattern index 1` and no program output;
- no grammar or generated parser artifact changed in this phase (Regex adds no syntax), so no parser
  regeneration was required.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is still
only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl` samples, the
`simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI references
to `sl`). Regex supersedes no SimpleLanguage production path.

### Known limitations carried into later phases

- `case regex r#"..."#` in a `switch` and its non-fallthrough behavior are Phase 15; this phase
  supplies the compiled-pattern machinery and the `Regex`/`RegexMatch` types that switching will
  consume, but no `switch` syntax or regex case exists yet;
- regex match/capture binding in `switch` and capture substitution in `replace` are deferred by the
  specification; `replace` treats the replacement as literal text;
- offsets are the engine's zero-based character offsets (UTF-16 code units) as the specification
  describes; the portable dialect is ASCII-oriented, and `.` keeps the engine's default line
  handling because the specification does not define a multiline mode;
- `RegexSyntax` accepts escaped punctuation plus `\n`/`\r`/`\t` in addition to the named ASCII
  classes, so a control character can be written directly; this is the smallest documented extension
  of the specification's "literals" category and is not an engine-specific escape;
- a dynamic pattern is compiled at first execution and the last pattern is cached per construction
  site; only source constants receive the guaranteed once-per-constant compilation the specification
  mandates;
- the native-image fix also changed `SolvikList.get`, because `Regex.findAll` made the list bounds
  path reachable for runtime compilation for the first time; the list API itself is unchanged.

## Phase 13 Evidence (completed 2026-09-17)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the Phase 13
  surface and documents it in the header: `primary` accepts `matchExpr`; new
  `matchExpr: MATCH expression LBRACE (matchBranch | SEMI)* RBRACE`,
  `matchBranch: pattern ARROW expression`,
  `pattern: Identifier (COLON typeRef | LPAREN patternList? RPAREN)?`, and
  `patternList: pattern (COMMA pattern)*`; new `MATCH` (`match`) and `ARROW` (`=>`) tokens. The
  wildcard is the bare identifier `_`, so no new keyword is reserved;
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible, SHA-256 verified):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- AST: `AstKind` adds `MATCH_EXPR`, `MATCH_BRANCH`, `WILDCARD_PATTERN`, `ENUM_PATTERN`, and
  `BINDING_PATTERN`; new `org.solvik.ast.expression.MatchExprNode` (scrutinee plus source-ordered
  branches) and `MatchBranchNode` (pattern plus result); new `org.solvik.ast.pattern` package with
  the abstract `PatternNode` and the concrete `WildcardPatternNode`, `BindingPatternNode` (name plus
  optional written subtype), and `EnumPatternNode` (variant name plus nested argument patterns);
  `SolvikAstBuilder` builds match expressions and interprets a bare pattern name by context (a
  top-level name is a value-less variant, a name inside a variant's argument list is a binding);
- semantic: `CheckedProgram` records `enumPatterns` (pattern to resolved variant), `patternBindings`
  (pattern to binding symbol), and `patternBindingTypes` (typed binding to its resolved subtype);
  `SolvikSemanticAnalyzer` checks the scrutinee, binding and enum patterns, duplicate/unreachable
  branches, recursive exhaustiveness, and branch result unification, and exposes the new maps
  through `CheckedProgram`; `org/solvik/diagnostic/DiagnosticCode.java` adds
  `TYPE_MATCH_PATTERN` (`SOLV-TYPE-033`), `TYPE_MATCH_RESULT` (`SOLV-TYPE-034`),
  `SEM_MATCH_NOT_EXHAUSTIVE` (`SOLV-SEM-029`), and `SEM_MATCH_UNREACHABLE_PATTERN`
  (`SOLV-SEM-030`);
- lowering/runtime: `SolvikLowering` lowers a match to a scrutinee expression plus ordered
  pattern/result clauses and allocates an object-represented frame slot for every pattern binding;
  new nodes `SolvikMatchNode`, `SolvikMatchClauseNode`, `SolvikPatternNode`,
  `SolvikWildcardPatternNode`, `SolvikBindingPatternNode`, and `SolvikEnumPatternNode`;
  `language/src/main/java/module-info.java` exports `org.solvik.ast.pattern` to the test module.

### Semantics and architecture implemented

- `match` is an expression (`match <scrutinee> { <branch>* }`) and joins `primary`, so it may appear
  as a local initializer, a return value, or any nested expression. A branch is
  `pattern => expression`; semicolon insertion already terminates a result expression at the line
  boundary because a result ends in an identifier, literal, or `)`, so no insertion-table change was
  needed;
- the initial patterns are the specification's three forms: an enum variant pattern (`Ok(value)`,
  `Error(message)`, a value-less `Red`, and nested patterns such as `Wrap(Some(x))`), a
  sealed-subtype binding pattern (`circle: Circle`), and the wildcard `_`. A bare name inside an enum
  variant's argument list is a binding whose type is the variant's substituted value type; a bare
  name at the top level is a value-less variant;
- each branch introduces its own scope containing its pattern bindings, so a binding shadows an
  outer declaration for the branch result only and duplicate binding names within one branch are
  `SOLV-RESOL-002`. A binding has the variant's substituted value type or, for a written subtype, the
  written type after it is proven a non-erased, non-null subtype of the matched type
  (`SOLV-TYPE-003`, `SOLV-TYPE-025`, `SOLV-TYPE-031`, `SOLV-TYPE-033`);
- exhaustiveness is checked against the closed set the Phase 12 metadata records. An enum match must
  cover every variant of `EnumSymbol.variants()` and a sealed-class match must cover every concrete
  subtype in `ClassSymbol.allSubtypes()`, computed recursively so nested patterns such as
  `Wrap(Some(x))` plus `Wrap(None)` exhaust an outer variant; a remaining variant, sealed subtype,
  null case, or an open-typed match without a wildcard is `SOLV-SEM-029`. A nullable scrutinee
  additionally requires a wildcard or bare binding because a typed binding never matches `null`;
- a branch that repeats a variant already exhausted by an irrefutable pattern, repeats a typed
  binding that already covers every concrete sealed subtype, repeats an exact pattern signature, or
  follows a wildcard/bare binding is `SOLV-SEM-030`; the check is conservative so a nested refutable
  pattern is never mistaken for full variant coverage;
- the result type is the nearest common declared supertype of every branch result, computed over
  the declared class/interface graph with nullability folded in (`null` joined with `String` is
  `String?`). When the common supertypes have no single most specific element the match is ill-typed
  (`SOLV-TYPE-034`), which is the specification's "if none exists" case;
- lowering evaluates the scrutinee once, tries the clauses in source order, binds names by writing
  object-represented frame slots, and evaluates the first matching result. Because patterns are
  statically resolved, an enum test is a variant-identity comparison and a subtype test reuses
  `SolvikRuntimeTypes`; the non-matching fall-through is unreachable for a well-typed program and
  raises a Solvik runtime type error rather than returning a default. A program with any diagnostic
  still produces no `CheckedProgram` and no call target.

### Tests added (718 Solvik tests total, up from 666)

- `SolvikMatchParserTest` (14): the match expression and its scrutinee, value-less and value-carrying
  variant patterns, wildcard, sealed-subtype binding with its written type, nested variant patterns,
  wildcard inside a variant, match as a local initializer, source branch order, the match-branch node,
  and parse negatives for a missing arrow, a missing scrutinee, a missing closing brace, a missing
  pattern, and a trailing variant-pattern comma;
- `SolvikMatchSemanticTest` (12): the branch result type, a wildcard covering unlisted variants,
  generic enum binding substitution, sealed-subtype narrowing, subtype branches unifying to the
  sealed supertype, unrelated scalar branches unifying to `Object`, `null`/non-null branches unifying
  to a nullable type, nested binding substitution, a nullable sealed match with a wildcard, a typed
  binding covering the whole enum, a wildcard after a typed binding on a nullable type, and the
  recorded patterns;
- `SolvikMatchNegativeTest` (16): missing enum variant, missing sealed subtype, empty match over an
  open type, nullable enum without a wildcard, a typed binding that misses null, a duplicate variant,
  a branch after a wildcard, an unknown variant, a bare value-carrying variant, a value-less variant
  given arguments, an enum pattern on a non-enum, a binding type unrelated to the scrutinee, duplicate
  binding names, a nullable binding type, an erased generic binding type, and an ambiguous branch
  result set;
- `SolvikMatchExecutionTest` (9): value-less selection, value-carrying destructuring, wildcard
  handling, sealed-subtype member access, nested destructuring, generic binding substitution, match as
  a local initializer, a match result flowing through a sealed supertype, and non-exhaustive match
  output suppression;
- `SolvikAstStructureTest.phaseThirteenNodeFamiliesAreProduced` pins `MATCH_EXPR`, `MATCH_BRANCH`,
  `WILDCARD_PATTERN`, `ENUM_PATTERN`, and `BINDING_PATTERN` (now 20 tests);
  `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins `MATCH` and
  `ARROW` as non-terminators.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 718 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 718 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 57.1s` (run because the
  phase adds runtime execution nodes);
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase13Demo.sol`
  printed `5`/`-3`/`circle`/`square` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase13Demo.sol` printed the same
  lines, wrote no stderr, and exited 0;
- negative launcher check: a non-exhaustive enum match exits 1 with `SOLV-SEM-029` and no program
  output on both launchers;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is still
only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl` samples, the
`simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI references
to `sl`).

### Known limitations carried into later phases

- the initial pattern set is exactly the specification's enum variant, sealed-subtype binding, and
  wildcard forms; there are no literal/constant patterns, guards, or or-patterns, and a match branch
  result is a single expression rather than a block;
- exhaustiveness is recursive over nested enum patterns but a nested sealed hierarchy inside an enum
  value is only covered through the value's closed set when that value type is itself an enum or
  sealed class; duplicate detection is deliberately conservative and can miss an unreachable branch
  expressed through a nested refutable pattern;
- a nullable scrutinee always needs a wildcard or bare binding, because a typed binding matches only
  non-null values; the diagnostic reports `null` as the missing case;
- branch result unification rejects a set whose common supertypes have no unique most specific
  element (`SOLV-TYPE-034`) rather than inventing a least upper bound for multiple unrelated
  interfaces; this is the specification's "if none exists" case;
- the Phase 11/12 limitation is unchanged: a generic enum with a variant that does not determine
  every enum type parameter cannot be constructed (`SOLV-TYPE-030`), so `Option.None` is not a
  supported source value yet;
- `_` is treated as a wildcard only in pattern position; it remains an ordinary identifier
  elsewhere, matching the specification's identifier grammar.

## Phase 12 Evidence (completed 2026-09-17)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the Phase 12
  surface and documents it in the header: `compilationUnit` accepts `enumDecl`; new
  `enumDecl: ENUM Identifier typeParameterList? LBRACE (enumVariant | SEMI)* RBRACE` and
  `enumVariant: Identifier (LPAREN typeRefList? RPAREN)? SEMI`; `classDecl` accepts a leading
  `SEALED?` before `OPEN?`; new `ENUM` and `SEALED` tokens;
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible, SHA-256 verified):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- AST: `AstKind` adds `ENUM_DECL` and `ENUM_VARIANT`; new `EnumDeclNode` (name, type parameters,
  variants) and `EnumVariantNode` (name, positional value types); `ClassDeclNode` records `sealed`;
  `SolvikAstBuilder` builds enum declarations/variants and passes the sealed modifier;
- type model: new `EnumType` (nominal, `Object`-rooted, declaring type parameters, and no supertype
  edges because an enum is closed);
- semantic: new `EnumSymbol` (nominal type plus the complete variant set in source order) and
  `EnumVariantSymbol` (owner, positional value types in the owning enum's parameter space);
  `ClassSymbol` records `sealed`, `isExtendable()`, the direct `permittedSubtypes()`, and the
  transitive `allSubtypes()`; `SolvikSemanticAnalyzer` registers enum types, collects variants,
  resolves qualified variant construction and value-less variant reads, infers enum type arguments,
  rejects sealed construction and enum use as a value, and installs sealed subtype metadata;
  `CheckedProgram` records enums, enum declarations, and variant constructions;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_ENUM_AS_VALUE` (`SOLV-TYPE-032`) and
  `SEM_CANNOT_CONSTRUCT_SEALED` (`SOLV-SEM-028`);
- lowering/runtime: `SolvikLowering` creates runtime enum/variant metadata and lowers every resolved
  variant construction; new `org/solvik/truffle/object/SolvikEnumClass`, `SolvikEnumVariant`, and
  `SolvikEnumValue`; new `SolvikEnumConstructNode`; `SolvikRuntimeTypes` handles `EnumType` type
  tests; `SolvikEqualNode` compares enum values by value; `SolvikDisplay` renders an enum value as its
  enum type name; `SemicolonInsertingTokenSource`'s terminator table is documented to leave `enum`
  and `sealed` as non-terminators.

### Semantics and architecture implemented

- `enum Name<T, ...> { Variant(Type, ...) ... }` declares a closed nominal type under `Object`;
  variants are nested nominal constructors carrying positional values whose types may reference the
  enum's type parameters. A value-less variant omits the parentheses. A generic enum application is
  an invariant `ParameterizedType` exactly like a generic class or interface;
- enum construction is qualified (`Result.Ok(value)`) and a value-less variant is a bare qualified
  read (`Color.Red`). The compiler records the complete variant set (`EnumSymbol.variants()`), so an
  unknown or external variant is `SOLV-RESOL-004`, a wrong arity is `SOLV-TYPE-003`, a wrong value
  type is `SOLV-TYPE-001`, and an enum name used as a value or as a direct constructor is
  `SOLV-TYPE-032`;
- enum values compare by value for `==`/`!=`, as the specification requires: same variant and
  pairwise equal values, with scalars and nested enum values compared by value and ordinary class
  instances by identity. Runtime `is`/`as` against a non-generic enum tests nominal identity through
  erasure;
- `sealed class` is abstract and is the second way, besides `open`, that a class may be extended; a
  direct `sealed` construction is `SOLV-SEM-028`. Because semantic analysis compiles exactly one
  source file and the specification permits only same-file subtypes, every observed subclass is
  permitted; the compiler records both the direct permitted variant set and the complete transitive
  closure;
- a class may not extend or implement an enum (`SOLV-SEM-009`/`SOLV-SEM-022`), duplicate variant or
  type names are `SOLV-RESOL-002`, and an unknown variant value type keeps `SOLV-RESOL-003`;
- `match`, destructuring, and the exhaustiveness checker remain Phase 13; this phase supplies the
  closed-variant metadata (`EnumSymbol.variants()`, `ClassSymbol.permittedSubtypes()`, and
  `ClassSymbol.allSubtypes()`) it will consume. A program with any error diagnostic still produces no
  `CheckedProgram` and no call target.

### Tests added (666 Solvik tests total, up from 615)

- `SolvikEnumParserTest` (13): value-carrying and value-less variants, multiple values, generic
  type parameters, generic value-type applications, sealed/open/plain class modifiers, declaration
  order across classes and enums, and parse negatives for a function inside an enum, a missing body
  or name, a trailing value-list comma, `sealed fun`, and an enum `extends` clause;
- `SolvikEnumSemanticTest` (13): the recorded variant set, enum/Object/Any subtyping, qualified
  construction, value-less reads, generic inference and multi-value substitution, enum-typed
  assignment, equality typing, enum type tests, the sealed direct/transitive subtype sets, sealed
  non-construction with subtype construction, nominal value types, and application
  canonicality/invariance;
- `SolvikEnumNegativeTest` (16): unknown variants (call and bare), wrong arity, a bare value-carrying
  variant, a wrong value type, direct enum construction and enum-as-value, sealed construction,
  extending and implementing an enum, duplicate variants, an enum/class name collision, a raw
  generic enum type, a value-less generic variant that cannot infer, an unknown value type, and an
  unrelated-enum assignment;
- `SolvikEnumExecutionTest` (8): value-less and value-carrying value equality, generic variant
  construction, values through functions and variables, enum display, enum `is` tests, sealed
  hierarchy dispatch, and compile-error output suppression;
- `SolvikAstStructureTest.phaseTwelveNodeFamiliesAreProduced` pins `ENUM_DECL` and `ENUM_VARIANT`
  (now 19 tests); `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins
  `enum` and `sealed` as non-terminators.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 666 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 666 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 1m 5s` (run because the
  phase adds runtime enum representation and a new execution node);
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase12Demo.sol`
  printed `Color`/`true`/`false`/`circle` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase12Demo.sol` printed the same
  lines and exited 0;
- negative launcher check: a program that constructs a sealed class exits 1 with `SOLV-SEM-028` and
  no program output on both launchers;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is still
only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl` samples, the
`simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI references
to `sl`).

### Known limitations carried into later phases

- `match`, destructuring, and exhaustiveness are Phase 13. An enum value therefore has no source-level
  field access yet; `SolvikEnumValue.value(int)` exists only for the Phase 13 runtime to consume, so
  Phase 12 construction tests compare values instead of reading them;
- a generic enum with a variant that does not determine every enum type parameter cannot be
  constructed: `Result<T, E>.Ok(T)` and `Option<T>.None` report `SOLV-TYPE-030`, because the initial
  language infers call-site arguments from argument types only. This follows the Phase 11 limitation
  and is not weakened with `Any`; expected-type-directed inference is reserved for a later phase;
- `sealed` applies to classes only, matching the specification's `sealed class` wording; sealed
  interfaces are not part of the initial language. The permitted subtype set is computed within the
  single compilation unit, and the compiler has no multi-file compilation, so a cross-file subclass
  cannot arise and is structurally impossible rather than separately diagnosed;
- an enum value displays as its enum type name; the specification defines display only for ordinary
  objects, and no Phase 12 test depends on variant-name display;
- `is`/`as` against a generic enum application is rejected as erased (`SOLV-TYPE-031`), consistent
  with Phase 11; only non-generic enum type tests execute.

## Phase 11 Evidence (completed 2026-09-17)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the Phase 11
  generic surface and documents it in the header: `classDecl`, `interfaceDecl`, `functionDecl`,
  `methodDecl`, `signatureDecl`, and `defaultMethodDecl` accept a `typeParameterList?` after the
  declared name; `typeRef` accepts a `typeArguments?` list before the optional `?`; new rules
  `typeParameterList: LT Identifier (COMMA Identifier)* GT` and
  `typeArguments: LT typeRef (COMMA typeRef)* GT`. Type-argument syntax reuses `LT`/`GT` and is
  unambiguous because a `typeRef` only appears in a type position; call sites never spell type
  arguments (they are inferred);
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible, SHA-256 verified):
  `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
  `.tokens`/`.interp`;
- AST: `AstKind` adds `TYPE_PARAMETER`; new `TypeParameterNode`; `TypeRefNode` records its generic
  type arguments; `CallableDeclNode`, `ClassDeclNode`, and `InterfaceDeclNode` record their type
  parameter lists; `SolvikAstBuilder` builds type parameter lists and type arguments for every
  declaration and type reference;
- type model: new `TypeParameterType` (a nominal declared parameter whose only supertype is `Any`)
  and `ParameterizedType` (a generic application whose base's declared superclass and interface
  edges are substituted with its arguments, so generic inheritance is nominal); new `ListType` (the
  built-in generic `List<T>` under `Object` with element parameter `T`); `Type` gains
  `typeParameters()`, the canonical `parameterizedView(List)` cache, and `substitute(Map)`;
  `NullableType` substitutes its inner type; `ClassType`/`InterfaceType` carry declared type
  parameters and accept parameterized supertype and interface edges; `TypeEnvironment` predeclares
  `List`;
- semantic: `FunctionSymbol` records declared type parameters; `ClassSymbol` records per-interface
  and per-property/per-method substitutions so inherited and interface members are read through the
  receiver's type arguments; `SolvikSemanticAnalyzer` resolves type parameters and applications,
  rejects raw generic types, arity mismatches, and type arguments on non-generic types, substitutes
  member, call, construction, and return signatures, infers call-site type arguments from arguments,
  installs resolved (possibly generic) superclass and interface edges, and types the built-in
  `List<T>` members;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_RAW_GENERIC_TYPE` (`SOLV-TYPE-027`),
  `TYPE_TYPE_ARGUMENT_ARITY` (`SOLV-TYPE-028`), `TYPE_NOT_GENERIC` (`SOLV-TYPE-029`),
  `TYPE_CANNOT_INFER` (`SOLV-TYPE-030`), and `TYPE_ERASED_TYPE_TEST` (`SOLV-TYPE-031`);
- lowering/runtime: `SolvikLowering` lowers `List<T>.size` and `.get` through dedicated nodes; new
  `org/solvik/truffle/object/SolvikList` and nodes `SolvikListSizeNode`/`SolvikListGetNode`;
  `SolvikException` adds `boundsError`.

### Semantics and architecture implemented

- Generic declarations accept a type parameter list after the declared name
  (`class Box<T>`, `interface Repository<T>`, `fun identity<T>(...)`, generic methods and interface
  members). Type parameters are nominal: one instance per declaration, visible in the declaration's
  member types and body, shadowing nothing but sitting in the separate type-parameter namespace. The
  initial language defines no bounds, so a bare type parameter's only supertype is `Any`;
- a written type may apply a generic declaration (`Box<User>`, `List<String>`, nested
  `List<Box<String>>`, nullable `List<String>?` and `List<String?>`). Applications are canonical per
  base and argument list, which keeps identity comparison and the subtype walk reliable;
- type arguments are invariant: two applications are assignment-compatible only when their bases
  match and their arguments are pairwise identical. Generic inheritance is nominal and substituted:
  `class Wrapper<U> extends Box<U>` makes `Wrapper<String>` a subtype of `Box<String>`, and
  `class Holder<T> implements Container<T>` makes `Holder<String>` conform to `Container<String>`;
- call-site type arguments are inferred, never written. Construction, top-level generic functions,
  and generic methods bind their parameters from argument types by structural unification; an
  unbound parameter is `SOLV-TYPE-030`. Construction yields the inferred application, so `Box(5)` has
  type `Box<Int>`; the constructor's parameter types are substituted before checking;
- member reads, method calls, and `super` accesses on a parameterized receiver substitute the
  receiver's type arguments (composed with inherited/interface substitutions), so `box.value` and
  `box.get()` on a `Box<Int>` have type `Int`, and an inherited `get(): T` read through
  `class IntBox extends Box<Int>` also has type `Int`;
- interface conformance substitutes each interface requirement's types with the class's binding
  before comparing it with an implementation, so a non-generic class may implement
  `Container<String>` with concrete `String` signatures and a generic class may match `Container<T>`;
- a bare generic name is a raw type `SOLV-TYPE-027`; applying a non-generic type is
  `SOLV-TYPE-029`; a wrong type-argument count is `SOLV-TYPE-028`; an unknown type argument keeps
  `SOLV-RESOL-003`; duplicate type parameter names are `SOLV-RESOL-002`;
- `is` and `as` against a generic application are rejected as `SOLV-TYPE-031` because the initial
  runtime uses erasure. The runtime representation of generic values is the erased base class, so
  `Box<Int>` and `Box<String>` share one runtime class; no reified check is possible;
- `List<T>` is the built-in immutable collection type. `List<String>.size` is `Int`, `.get(index: Int)`
  is the element type, the type is invariant, and its `size` is immutable (`SOLV-TYPE-006`).
  Collection literals are deferred, so no supported source form constructs a `List` value yet; the
  runtime `SolvikList` and its size/get nodes exist so a `List`-typed parameter body lowers, and the
  out-of-range get path raises a Solvik bounds error should a construction form be defined;
- a program with any diagnostic still produces no `CheckedProgram` and no call target; the new runtime
  nodes are the only runtime `List` access, and generic erasure adds no runtime dispatch.

### Tests added (615 Solvik tests total, up from 553)

- `SolvikGenericsParserTest` (13): class, function, interface, signature, default-method, and method
  type parameter lists; multiple parameters in source order; type applications on parameters and
  returns; nested applications; nullable applications (`List<String>?`, `List<String?>`); type
  arguments in `implements`; a `<` comparison that is not a type parameter list; and parse negatives
  for empty parameter/argument lists and a trailing type-argument comma;
- `SolvikGenericsSemanticTest` (15): declared type parameters and canonical applications; construction
  inference; receiver substitution in member reads and calls; generic function and method return
  substitution; invariance; nested applications; `List` member typing, invariance, and `Object`/`Any`
  assignability; generic interface conformance and dispatch; a generic class implementing a matching
  generic interface; inherited generic member/property substitution through a concrete and a generic
  supertype; and the generic callable's recorded function type;
- `SolvikGenericsNegativeTest` (23): raw class and `List` types, wrong type-argument count, type
  arguments on non-generics and on type parameters, unknown type arguments, invariance and
  non-covariance, inferred-argument mismatch, uninferable arguments, duplicate type parameter names,
  erased `is`/`as`, `List` immutability, index typing, element typing and invariance, unknown `List`
  members, missed and mismatched generic interface implementations, `implements` arity, and wrong
  generic-function argument types;
- `SolvikGenericsExecutionTest` (8): generic construction/property/method execution, generic function,
  generic method, generic interface dispatch, a generic class through a matching generic interface,
  inherited generic members through a subclass, generic object display, and compile-error output
  suppression;
- `SolvikTypeModelTest` adds `List` builtin registration and generic-application
  canonicality/invariance/subtype coverage plus type-parameter top/typing/substitution coverage
  (now 11); `SolvikAstStructureTest.phaseElevenNodeFamiliesAreProduced` pins `TYPE_PARAMETER`
  (now 18); `SolvikTypeModelTest.allBuiltinsResolveByName` now includes `List`.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 615 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 615 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 48.5s`;
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase11Demo2.sol`
  printed `9`/`9`/`hi` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase11Demo2.sol` printed the same,
  wrote no stderr, and exited 0;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is still
only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl` samples, the
`simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI references to
`sl`).

### Known limitations carried into later phases

- Collection construction is deferred with collection literals, so no source form creates a `List`
  value; `List<T>` is fully statically typed and its size/get runtime nodes exist, but the
  specification's bounds-error behavior is not reachable from source until a construction form is
  specified. This follows the Phase 10 `String.length` precedent of not inventing an API the
  specification omits rather than changing the language;
- call sites never spell type arguments, so a type parameter that appears only in a return type
  cannot be inferred (`SOLV-TYPE-030`); explicit call-site type arguments are left to a later phase
  because `Name<T>(...)` is syntactically indistinguishable from comparison (`a < b > (c)`) without
  inventing a disambiguation rule the specification does not define;
- generic interfaces are substituted for conformance and for direct members. A member declared in a
  generic parent interface and reached through an extended-interface-typed receiver is typed with the
  receiver's own parameters rather than the declaring parent's; extension-plus-dispatch through the
  parent type is not covered by the phase's tests;
- type parameters have no bounds and no variance syntax, matching the specification's initial scope;
- a valid statement that ends in a generic type operand (`return x is Box<String>` with immediate
  semicolon insertion) needs a terminating token because `>` is not a semicolon-insertion terminator;
  an erased test is a compile error anyway, and the negative tests parenthesize the operand. No
  insertion-table change was made because treating `>` as a terminator would break multiline
  relational expressions.

## Phase 10 Evidence (completed 2026-09-17)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds the Phase 10
  null-safety surface and documents it in the header: `typeRef` accepts an optional `?`; `expression`
  starts at the new `nullCoalescing` rule (`??`, below `||`); `relational` uses a `relation`
  alternative so `is`/`as` carry a `typeRef` right operand; `memberSuffix` accepts `DOT` or
  `NULLABLE_DOT`; `literal` gains `nullLiteral`; and the `NULL`, `IS`, `AS`, `NULL_COALESCE`, and
  `QUESTION` tokens are declared (`NULLABLE_DOT` already existed for insertion lookahead);
- regenerated parser artifacts (only via `generate_parser.sh`, byte-reproducible): `SolvikLexer.java`,
  `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`, `.tokens`/`.interp`;
- AST: `AstKind` adds `NULL_LITERAL`, `TYPE_TEST_EXPR`, and `CAST_EXPR`; `TypeRefNode` records
  `isNullable()`; `MemberAccessExprNode` records `isSafe()`; `BinaryOperator` adds `COALESCE` (new
  `Kind.COALESCE`); new `NullLiteralNode`, `TypeTestExprNode`, and `CastExprNode`;
  `SolvikAstBuilder` builds nullable type references, the `null` literal, the safe member suffix, the
  `??` fold, and dedicated `is`/`as` nodes;
- type model: `Type` gains `isNullable()`, `nonNullType()`, the canonical `nullableView()`, and
  nullable-aware `isSubtypeOf`; new `NullType` and `NullableType`;
- semantic: `SolvikSemanticAnalyzer` resolves nullable type references, types `null`, `??`, `is`, and
  `as`, rejects nullable dereferences, nullable `is`/`as` type operands, non-nullable `??` left
  operands, and assignment through `?.`, and implements flow-sensitive narrowing for null checks and
  type tests with write invalidation across nested branches and loops; `CheckedProgram` records the
  tested target type of every `is`/`as` (`testedTypeOf`);
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_NULLABLE_DEREFERENCE` (`SOLV-TYPE-024`),
  `TYPE_INVALID_TYPE_OPERAND` (`SOLV-TYPE-025`), and `TYPE_NULLABLE_REQUIRED` (`SOLV-TYPE-026`);
- `org/solvik/parser/SemicolonInsertingTokenSource.java` adds `NULL` and `QUESTION` to the
  newline-terminator table; `is`, `as`, and `??` remain non-terminators and non-continuations;
- lowering `org/solvik/lowering/SolvikLowering.java` lowers the null literal, `??`, `is`, and `as`,
  passes safe access to property reads and method calls, and links each runtime class's superclass and
  transitive interface set;
- runtime: `org/solvik/truffle/object/SolvikClass.java` gains runtime superclass and interface
  metadata (with `@TruffleBoundary` on the interface lookup so the native-image runtime-compilation
  blocklist is not violated); new `org/solvik/truffle/object/SolvikRuntimeTypes.java` performs the
  runtime type check; new nodes `SolvikNullLiteralNode`, `SolvikCoalesceNode`, `SolvikTypeTestNode`,
  and `SolvikCastNode`; `SolvikReadPropertyNode` and `SolvikInvokeMethodNode` gain null-safe access;
  `SolvikException` adds `typeError(...)`.

### Semantics and architecture implemented

- `T?` is an explicit nullable type in the compiler type model: `NullableType` extends the value set
  of its non-null inner type with `null`, and `NullType` is the type of the `null` literal. Nullable
  views are canonical per type instance, so identity comparison of written and narrowed types stays
  reliable. Assignability follows the specification exactly: `S` is assignable to `T?` when `S` is
  assignable to `T`, `S?` is assignable to `T?` when `S` is assignable to `T`, `S?` is never
  assignable to a non-null `T`, and `null` is assignable only to a nullable type. `Nothing` stays the
  bottom type;
- a nullable receiver may only be dereferenced through `?.` or inside a null check; `receiver.member`
  and `receiver.member(...)` on a nullable type are `SOLV-TYPE-024`. A safe access on a nullable
  receiver yields the member type made nullable and its runtime node returns `null` without reading a
  shape, evaluating arguments, or calling a method; on a non-null receiver the member type stays
  non-null. Assignment through `?.` is rejected as an invalid target;
- `??` requires a nullable left operand (`SOLV-TYPE-026`) and yields the common type of the non-null
  left and the right operand, so `String? ?? String` is `String`, `String? ?? String?` stays
  `String?`, and a `null` right operand contributes no nullability; the right operand is evaluated
  only when the left is `null`;
- `is` and `as` take a written type operand and reject `T?` as `SOLV-TYPE-025` rather than inventing
  nullable-test or safe-cast semantics. `is` is `Boolean`; `as` has the target type and performs a
  runtime check, raising a Solvik runtime type error on failure;
- flow-sensitive narrowing is implemented for locals and parameters: `x != null` / `x == null`
  narrow the true and false branches, `x is T` narrows the true branch when `T` specializes the
  declared type, `!` swaps the refinements, and an early-returning then-branch narrows the code after
  the `if`. A write removes the refinement, the join of an `if` keeps only refinements both branches
  agree on, and a variable written inside a loop loses its refinement after the loop, so a nested or
  looped write cannot resurrect a stale non-null assumption;
- a program with any error diagnostic still produces no `CheckedProgram`, and the null-safety
  runtime nodes are the only runtime null checks. Nullable values use object frame slots, while
  non-null `Int`/`Boolean`/`Long`/`Float`/`Double` keep primitive specialization;
- runtime `is`/`as` tests use the Java representation for built-ins and the runtime `SolvikClass`
  superclass chain plus a transitive interface-name set for nominal types.

### Tests added (553 Solvik tests total, up from 486)

- `SolvikNullSafetyParserTest` (15): nullable type references on parameters and locals, the `null`
  literal, safe versus ordinary member access, safe chains, `??` precedence below `||` and left
  associativity, dedicated `is`/`as` nodes, `is` binding looser than `+`, cast chains, a nullable
  type operand, and `null` as a statement terminator;
- `SolvikNullSafetySemanticTest` (16): the `Null` type, non-null-to-nullable and nullable-to-nullable
  assignability, the nullability of `?.` on nullable and non-null receivers, `??` result types, the
  `Boolean`/target types of `is` and `as`, null-check narrowing, type-test narrowing, `var` narrowing,
  early-return narrowing after an `if`, `else`-branch narrowing, `while`-condition narrowing, and
  nullable properties;
- `SolvikNullSafetyNegativeTest` (19): `null` assigned, passed, or returned where non-null is
  required, nullable-to-non-null assignment/return, nullable property/method dereference, assignment
  through `?.`, non-nullable `??` left, incompatible `??` operands, nullable `is`/`as` operands, a
  write inside the checked block, a write in a nested branch, a write inside a loop, nullable
  arithmetic, and an unknown type in a nullable annotation;
- `SolvikNullSafetyExecutionTest` (12): safe access and safe method calls short-circuit `null` and
  skip argument evaluation, `??` chooses the right operand and does not evaluate it when unnecessary,
  `is` on classes and interfaces, successful and failing `as` (guest runtime type error, no output),
  null-check and type-test narrowing execution, built-in `is`/`as`, and inheritance type tests;
- `SolvikTypeModelTest` adds nullable canonicalization and nullable assignability (2 tests, now 9);
  `SolvikAstStructureTest.phaseTenNodeFamiliesAreProduced` pins `NULL_LITERAL`, `TYPE_TEST_EXPR`,
  `CAST_EXPR`, `BINARY_EXPR`, and `MEMBER_ACCESS_EXPR` (now 17);
  `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins `NULL` and
  `QUESTION` as terminators and `IS`/`AS`/`??` as non-terminators, and the lone-`?` test now pins the
  `QUESTION` token (2 tests, now 26); `SolvikSemicolonInsertionTest` now accepts `?.` chains;
  `SolvikParserNegativeTest.invalidCharacterProducesLexerError` uses `@` because `?` is a token.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 553 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 553 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 46.8s` (an earlier run
  failed with a runtime-compilation blocklist violation on the interface lookup; adding
  `@TruffleBoundary` to `SolvikClass.implementsInterface` fixed it and the final builds are clean);
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase10Demo.sol`
  printed `true`/`7`/`-1`/`true`/`Doug`/`42` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase10Demo.sol` printed the same,
  wrote no stderr, and exited 0;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is still
only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl` samples, the
`simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI references to
`sl`).

### Known limitations carried into later phases

- `Any` is the top type for non-null values only, so `print`/`println(value: Any)` cannot be given
  `null` or a nullable value directly; the specification defines no null display path, and nullable
  values must be coalesced or narrowed before display.
- `is` and `as` require a non-null written type operand; `T?` on the right is rejected as
  `SOLV-TYPE-025` rather than given invented semantics, because the specification does not define a
  nullable type test and explicitly defers safe-cast syntax.
- Narrowing tracks locals and parameters only, so a property read such as `this.name` is not narrowed
  by a null check, and `?.` on a `super` receiver is treated as ordinary non-null access.
- `String` has no declared members in the specification, so the illustrative `name.length` example is
  not executable; the Phase 10 member-access tests use user-declared classes instead of inventing a
  built-in member API.
- Runtime `is`/`as` for user types tests the superclass chain and a transitive interface-name set;
  there is no reified generic check yet (Phase 11 must reject tests against erased type arguments).
- The semicolon-insertion terminator table now includes `QUESTION`, a clarification required by `T?`
  just as Phase 2 added `this`; the specification's condition-2 list predates nullable types.

## Phase 9 Evidence (completed 2026-09-17)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds `delegateDecl`
  (`delegate val name: InterfaceType [= initializer];`), makes it a `classMember`, reserves the
  `DELEGATE` token, and documents the Phase 9 conventions; regenerated parser artifacts (only via
  `generate_parser.sh`, byte-reproducible): `SolvikLexer.java`, `SolvikParser.java`,
  `SolvikVisitor.java`, `SolvikBaseVisitor.java`, `.tokens`/`.interp`;
- AST: `AstKind` adds `DELEGATE_DECL`; new `DelegateDeclNode` (name, required type reference, optional
  initializer); `ClassDeclNode` now keeps one source-ordered `members()` list and derives
  `properties()`, `delegates()`, `initializers()`, and `methods()` views from it, so a property or
  delegate may appear in any position; `SolvikAstBuilder` builds delegate declarations and preserves
  source order across every member kind;
- Semantic: new `DelegateBinding` (the immutable delegate property plus the resolved
  `InterfaceSymbol` contract it can forward); `PropertySymbol` records `isDelegate()`;
  `FunctionSymbol` models a compiler-synthesized forwarding method (`isSynthesized()`,
  `forwardedDelegate()`, `delegateProperty()`, and a `delegatedMethod(...)` factory) and counts it as
  an implementation in `hasImplementation()`; `ClassSymbol` resolves delegation inside interface
  conformance using the architecture precedence (own method, inherited class method, inherited
  forwarding method, unambiguous delegate, unambiguous default), and exposes `delegates()`,
  `invalidDelegates()`, `delegatedRequirement()`, `ambiguousDelegatedRequirements()`,
  `delegateSignatureConflicts()`, and `inheritedDelegatedMethod()`;
  `SolvikSemanticAnalyzer` collects properties and delegates in one source-ordered field layout,
  records a delegate's interface contract, rejects a non-interface declared type, checks delegate
  declaration initializers, and reports ambiguous delegation and non-conforming forwarded signatures;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `SEM_INVALID_DELEGATE_TYPE` (`SOLV-SEM-025`),
  `SEM_AMBIGUOUS_DELEGATION` (`SOLV-SEM-026`), and `SEM_DELEGATE_SIGNATURE` (`SOLV-SEM-027`);
- `org/solvik/lowering/SolvikLowering.java` lowers delegate declaration initializers in source order,
  allocates a runtime handle for each resolved forwarding method, synthesizes its body
  (`this.<delegate>.<member>(...)`, returned for a value-returning member and executed for effect for
  `Unit`), installs it into the class virtual table, and reuses a superclass's forwarding handle in a
  subclass so one body is compiled per resolution.

### Semantics and architecture implemented

- `delegate val field: InterfaceType` declares an immutable, explicitly typed property that supplies
  the interface members of its declared contract to the declaring class. A delegate carries no
  `var`, never infers its type, and is initialized under the normal constructor rules (a declaration
  initializer or an assignment in `init`, with the existing definite-initialization checks);
- conformance precedence is exactly the architecture's: this class's own method, then a valid
  inherited class method, then a forwarding method a superclass already resolved, then an
  unambiguous delegate, then an unambiguous interface default. A resolved delegate is compiled to a
  forwarding method and installed in the class dispatch table, so calls through a class-typed or
  interface-typed receiver reach it and a default inherited from the delegate's contract only runs on
  the delegate object itself;
- a delegate's contract is the interface's complete member view, so it supplies inherited and
  extended requirements as well as the interface's defaults; forwarding dispatches on the delegate
  value's runtime class, which is what makes a purely abstract `Repository` requirement satisfiable;
- two distinct delegate properties that expose the same contract name with no earlier implementation
  is `SOLV-SEM-026` at the class declaration, and the member is left unresolved rather than picked
  arbitrarily; an explicit method (or inherited method) on the class resolves it and wins for every
  interface view;
- a forwarded member must keep the required parameter types and a covariant return type
  (`SOLV-SEM-027`); a delegate whose declared type is a class, a built-in, or any other non-interface
  is `SOLV-SEM-025` and supplies nothing; an unknown type name keeps its own `SOLV-RESOL-003`;
- delegation is a static analysis result consumed by the existing Truffle AST lowering. Static
  types, nominal assignability, and member resolution are unchanged: a class that implements an
  interface through a delegate is a nominal subtype of that interface exactly as before, and no new
  runtime node type was needed.

### Tests added (486 Solvik tests total, up from 430)

- `SolvikDelegateParserTest` (11): the specification delegate shape, a declaration initializer,
  source order across properties and delegates, delegate before or after `init`, and negatives for
  `delegate var`, a missing type, a missing `val`, a missing same-line terminator, a top-level
  delegate, and `delegate` used as an expression;
- `SolvikDelegateSemanticTest` (15): a delegate satisfies a requirement and its synthesized method
  records the delegate property and forwarded member, the property is immutable and typed, explicit
  and inherited methods outrank a delegate, a delegate outranks an interface default, a delegate
  overrides a default it also supplies, a subclass reuses the superclass forwarding symbol,
  initialization in `init`, two delegates resolving distinct members, a diamond contract not being
  ambiguous, an extended requirement, nominal assignability, the class dispatch table entry, a
  two-delegate conflict resolved by an explicit method, and the delegate's interface member view;
- `SolvikDelegateNegativeTest` (15): two-delegate ambiguity (distinct interfaces and the same
  interface type), a class-typed and a built-in-typed delegate, an unknown delegate type, no-`init`
  and not-assigned-in-`init` definite initialization, double assignment and post-construction write,
  a wrong-parameter and a non-covariant forwarded member, a property and a method name collision, a
  wrong-typed initializer, and that ambiguity suppresses a typed result;
- `SolvikDelegateExecutionTest` (14): a delegated requirement through an interface-typed parameter
  and through a class-typed receiver, explicit and inherited methods overriding a delegate, a
  delegate overriding a default, an interface default calling a delegated requirement, runtime
  dispatch to two delegate implementations, two independent delegates, a `Unit` member, inherited
  forwarding, a declaration initializer, reading and forwarding `this.<delegate>`, and compile-error
  suppression for ambiguity (`SOLV-SEM-026`) and a signature conflict (`SOLV-SEM-027`);
- `SolvikAstStructureTest` adds `phaseNineNodeFamiliesAreProduced` pinning `DELEGATE_DECL`;
  `SolvikSemicolonTokenStreamTest.terminatorTablePinsTheSpecificationList` now pins `delegate` as a
  non-terminator.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 486 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 486 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 47.2s` (run because the
  phase changes the runtime method-dispatch and lowering path);
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase9Demo.sol`
  printed `Good day, Rex`/`Hey Doug` and exited 0; native launcher smoke test:
  `./standalone/target/solviknative --disable-launcher-output /tmp/Phase9Demo.sol` printed the same
  lines, wrote no stderr, and exited 0;
- negative launcher check: the two-delegate ambiguity program exits 1 with `SOLV-SEM-026` and no
  program output on both launchers;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is
still only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl`
samples, the `simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI
references to `sl`).

### Known limitations carried into later phases

- A delegate must be an interface-typed property, but the declaring class is not required to
  implement the delegate's interface: a delegate whose contract is not in the class's interface
  closure supplies nothing and is not an error. The specification does not make that an error, so it
  is permitted rather than invented.
- A delegate participates in the same field layout and dispatch table as any property; there is no
  delegate-specific inline cache or vtable entry. Forwarding dispatches by name on the delegate
  value's runtime class, the same `@TruffleBoundary`-guarded lookup as class dispatch.
- Two distinct delegates supplying one contract name produce one `SOLV-SEM-026` diagnostic per
  distinct interface member of that name (typically two), matching how missing and conflicting
  requirements are already reported once per member.
- An inherited interface default is not treated as an inherited implementation ahead of a subclass's
  own delegate: the architecture places a delegated implementation before an interface default, so a
  subclass delegate replaces an inherited default. Only an inherited class method or an inherited
  forwarding method outranks a subclass delegate.
- Nullability, generics, enums, pattern matching, regex, and `switch` remain later phases (10–15).

## Phase 8 Evidence (completed 2026-09-16)

### Files changed

- Grammar source `language/src/main/java/org/solvik/parser/grammar/Solvik.g4` adds `interfaceDecl`
  (with a comma-separated `extends` list), `interfaceMember`, `signatureDecl` (a bodyless member
  terminated by a real `SEMI`), `defaultMethodDecl`, `typeRefList`, and `classDecl ... implements
  typeRefList`; new tokens `INTERFACE`/`IMPLEMENTS`; regenerated parser artifacts (only via
  `generate_parser.sh`, byte-reproducible): `SolvikLexer.java`, `SolvikParser.java`,
  `SolvikVisitor.java`, `SolvikBaseVisitor.java`, `.tokens`/`.interp`;
- AST: `AstKind` adds `INTERFACE_DECL` and `SIGNATURE_DECL`; new `InterfaceDeclNode`,
  `SignatureDeclNode`, and the shared `CallableDeclNode` base of every named callable declaration;
  `ClassDeclNode` records its `implements` list; `FunctionDeclNode` now extends `CallableDeclNode`
  and reports `hasBody()`; `SolvikAstBuilder` builds interfaces, signatures, default methods, and
  `implements` lists, and keeps source declaration order across functions, classes, and interfaces;
- Type model: new `InterfaceType` (nominal, implicitly under `Object`, with an installed
  `extends` list); `ClassType` gains `resolveInterfaceTypes`; `Type` gains `interfaceTypes()` and a
  cycle-safe subtype walk over both the superclass chain and the interface edges, so a class is a
  subtype of every interface it implements transitively and an interface of every interface it
  extends;
- Semantic: new `InterfaceSymbol` (extension list, declared members, and a name-multi-valued view
  that identity-deduplicates a diamond so one shared default is not a conflict); `FunctionSymbol`
  models interface default methods and abstract signatures (`hasImplementation()`,
  `isAbstractSignature()`, `isInterfaceMember()`, `interfaceOwner()`); `ClassSymbol` resolves
  conformance while building the virtual table using the architecture precedence (own method, then a
  valid superclass method, then an unambiguous interface default) and exposes
  `interfaceImplementations()`, `missingInterfaceRequirements()`, `conflictingInterfaceRequirements()`,
  `interfaceSignatureConflicts()`, `allInterfaces()`, `inheritedClassMethod()`, and
  `nearestDeclaredClassMethod()`; `CheckedProgram` records interfaces and interface declarations;
  `SolvikSemanticAnalyzer` collects and orders interfaces, resolves and cycle-checks extension
  graphs, checks default-method bodies with the interface as their nominal context (so `this` and an
  unqualified sibling call dispatch virtually on the conforming instance), reports non-interface
  `implements`/`extends` targets and duplicate names, rejects an interface as a value, and types both
  reads and calls through an interface-typed receiver;
- `org/solvik/diagnostic/DiagnosticCode.java` adds `TYPE_INTERFACE_AS_VALUE` (`SOLV-TYPE-023`),
  `SEM_MISSING_INTERFACE_IMPLEMENTATION` (`SOLV-SEM-020`), `SEM_CONFLICTING_DEFAULTS`
  (`SOLV-SEM-021`), `SEM_INVALID_INTERFACE` (`SOLV-SEM-022`), `SEM_IMPLEMENTATION_SIGNATURE`
  (`SOLV-SEM-023`), and `SEM_INTERFACE_CYCLE` (`SOLV-SEM-024`);
- `org/solvik/lowering/SolvikLowering.java` lowers each default method body once (receiver in frame
  slot zero) and installs the resolved implementation symbol into every conforming class's runtime
  method table; non-`super` method calls now lower to name-based virtual dispatch so a call inside a
  default body reaches the concrete implementor of a requirement;
- `org/solvik/parser/SemicolonInsertingTokenSource.java` documents that `interface` and `implements`
  are non-terminators (like `class`/`extends`), since an interface signature already ends in `;`;
- restored the untracked JVM launcher template `standalone/solvik` (see the limitation below).

### Semantics and architecture implemented

- `interface Name extends A, B { ... }` declares a nominal contract; interface extension is multiple
  while class inheritance stays single, and interfaces contain methods only, so a `val`/`var`
  property or an `init` inside an interface body is a parse error;
- a member is either an abstract signature `fun f(p: T): R;` (a requirement) or a `fun` with a body
  (a default). Both are callable through the interface type; a signature can never be used as a value
  and an interface name can never be constructed (`SOLV-TYPE-023`);
- `class C implements A, B` joins every requirement transitively. Conformance is checked while the
  class's virtual table is built: the effective implementation is the class's own method, then a valid
  superclass method, then an unambiguous interface default. A resolved default is installed into the
  class table, so calls through a class-typed or interface-typed receiver reach it and one default
  body is compiled for all implementors;
- a required member with no implementation is `SOLV-SEM-020` at the class declaration; two distinct
  defaults for one unresolved name is `SOLV-SEM-021` and must be settled by an explicit method (or an
  inherited class method) on the class, which then wins for every interface view;
- an implementation must keep the required parameter types and return a subtype of the required type
  (`SOLV-SEM-023`), including conformance against an inherited default it replaces; `override` is not
  used for interface implementation (`SOLV-SEM-012` stays class-inheritance only);
- `implements` naming a class, built-in, or `Object` is `SOLV-SEM-022`, `class C extends SomeInterface`
  is `SOLV-SEM-009`, an interface may extend only interfaces, an extension cycle is `SOLV-SEM-024`
  (and the subtype walk terminates even on a malformed graph), interfaces share the type-name
  namespace with classes and built-ins, and a member restated as a requirement after an extended
  default is rejected because it would leave both a requirement and a default unresolved;
- a diamond extension reaches one shared default and is not a conflict; a member redeclared by an
  extending interface hides the inherited one rather than conflicting;
- interface-typed parameters, locals, properties, and return values accept any implementing class
  value; interface-to-class and unrelated-interface assignments stay rejected; equality between two
  interface-typed values compares the objects by identity, and conformance failures produce no typed
  result and no executable call target, so no output is produced;
- interface conformance is a static analysis result consumed by the existing Truffle AST lowering;
  no new runtime node type was needed and no SimpleLanguage path was added or revived.

### Tests added (430 Solvik tests total, up from 352)

- `SolvikInterfaceParserTest` (11): specification interface shape, multi-`implements` source order,
  interface `extends` lists, `extends` + `implements` together, signature `;` and default-body `}`
  termination, declaration order across functions/classes/interfaces, and negatives for a property or
  `init` in an interface body, an `override` interface member, `implements` on an interface, and a
  member without a return type;
- `SolvikInterfaceSemanticTest` (19): interface descriptors, single and multiple conformance, a
  default satisfying its own requirement, default installation into the class dispatch table, sibling
  requirement calls through implicit `this` and through `this.member`, extension inheritance,
  inherited conformance through a superclass, class and superclass method precedence over a default,
  covariant implementations, interface-typed assignability, recorded resolution through an
  interface-typed receiver, argument/return typing, member hiding, diamond sharing, an interface-typed
  class property, and nominal interface typing;
- `SolvikInterfaceNegativeTest` (32): missing implementation, missing inherited requirement, one
  requirement reported per class, unresolved conflicting defaults (direct, through extension, and with
  a default elsewhere), conflicts resolved by an explicit or inherited method, wrong parameter types,
  non-covariant return, arity mismatch, non-conforming replacement of an inherited default,
  `implements` on a non-interface and on a built-in, interface extending a class, a class extending an
  interface, unknown interface name, two-interface and self-extension cycles, `super` inside a default
  body, duplicate interface and member names, shared class/interface namespace,
  interface-as-value, interface member read as a value, unknown member / wrong argument type / wrong
  arity through an interface receiver, no interface member leakage into a later top-level function,
  interface value not assignable to an implementing class type, `this` outside any member, restating an
  extended default as a requirement, and `override` on an implementing method;
- `SolvikInterfaceExecutionTest` (12): implementing method through an interface-typed parameter, a
  default running for a class that implements only the requirement, virtual dispatch from a default to
  the concrete requirement, an explicit method overriding a default, independent dispatch through two
  interfaces, explicitly resolved conflicting defaults, an extended-interface default calling sibling
  requirements, inherited conformance dispatched through a superclass method, a default called on a
  class-typed receiver, a default inside a loop over an interface-typed local, a default calling
  another default, and compile-error suppression of all output;
- `SolvikTypeModelTest` adds interface subtype/`Object`-rooted/nominal and class-interface edge
  coverage (7 tests); `SolvikAstStructureTest` pins `INTERFACE_DECL` and `SIGNATURE_DECL` and that a
  signature node has no body child (15 tests); `SolvikSemicolonTokenStreamTest
  .terminatorTablePinsTheSpecificationList` now pins `interface`/`implements` as non-terminators.

### Commands and results

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`: 430 tests,
  0 failures/errors/skips;
- `./build.sh`: BUILD SUCCESS; 430 Solvik language tests, 0 failures/errors/skips;
- `./build-native.sh`: BUILD SUCCESS; `Finished generating 'solviknative' in 46.9s` (run because the
  phase changed the runtime method-dispatch and lowering path);
- JVM launcher smoke test: `./standalone/target/solvik --disable-launcher-output /tmp/Phase8Demo.sol`
  printed `Hello Rex the dog`/`woof`/`Hello Rex the dog`/`Hello Rex the dog` and exited 0; native
  launcher smoke test: `./standalone/target/solviknative --disable-launcher-output /tmp/Phase8Demo.sol`
  printed the same four lines, wrote no stderr, and exited 0;
- negative launcher checks: an interface program with a missing implementation exits 1 with
  `SOLV-SEM-020` and no output, and `function main() {}` is still `SOLV-PARS-004`;
- generation reproducibility: rerunning `generate_parser.sh` regenerates all eight checked-in parser
  artifacts byte-for-byte (SHA-256 verified); no generated file was edited by hand.

### Remaining transitional SimpleLanguage code

No execution path changed and nothing new was removed. The remaining SimpleLanguage material is
still only non-executable naming and samples reserved for Phase 16 (the `language/tests/*.sl`
samples, the `simplelanguage`/`org.graalvm.sl` artifact and Java module names, and documentation/CI
references to `sl`).

### Known limitations carried into later phases

- Delegation is not implemented: `delegate val field: InterfaceType` is still a parse error, so an
  interface member can only be satisfied by a declared class method, an inherited class method, or an
  interface default (Phase 9).
- Interface members are methods only, matching the specification; there are no interface properties,
  constants, or visibility modifiers, and interface members carry no `open`/`override`.
- An interface declares no members of its own beyond its written ones: an interface-typed receiver
  exposes only the interface's own member set, and a conforming object still displays as its class
  name.
- An interface-typed receiver is dispatched through the runtime class table by name (same
  `@TruffleBoundary`-guarded name lookup as class dispatch); there is no interface-specific inline
  cache or vtable yet.
- Interface conformance is reported once per class at its declaration span for a missing requirement
  or an unresolved conflict; the conflicting-default diagnostic names the interface whose member set
  carries the name, which for a diamond is the extending interface rather than both origins.
- Nullability, generics (so `interface Repository<T>`), enums, pattern matching, regex, and `switch`
  remain later phases (10–15).
- The JVM launcher template `standalone/solvik` (referenced by `standalone/pom.xml`, copied to
  `standalone/target/solvik`, and never tracked in git) was absent from the working tree at the start
  of this phase, so `./build.sh` produced no JVM launcher. It was restored from the upstream
  `standalone/sl` template with only the copyright year updated, so the `@@launcherClass@@` filtering
  now yields `org.solvik.launcher/org.solvik.launcher.SolvikMain`. Phase 16 should commit or otherwise
  stop depending on an untracked launcher template.

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
  system, the Phase 7 root hierarchy and single inheritance, the Phase 8 interfaces and defaults, the
  Phase 9 delegation, and the Phase 10 null safety: functions and recursion, typed
  locals and assignment, `if`/`while`/`for`, `break`/`continue`, `return`, checked arithmetic on
  `Byte`/`Short`/`Int`/`Long` and IEEE 754 `Float`/`Double`, explicit numeric conversions,
  `String` concatenation, `Char` values, ordering/equality, short-circuit logical operators, unary
  operators, final-by-default class declarations with `val`/`var` properties, `init`, instance
  methods, `this`, construction, `open class`/`extends`, `override` with virtual dispatch,
  `super`, `interface`/`implements`/defaults, `delegate`, nullable `T?` types, `null`, `?.`, `??`,
  `is`, checked `as`, and flow-sensitive narrowing;
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
6. set `NEXT` to the immediately following phase, or to `COMPLETE` after Phase 16;
7. stop.
