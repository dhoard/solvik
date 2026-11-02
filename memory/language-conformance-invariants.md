# Language conformance invariants and naming rules

Verified against the current source; see `LANGUAGE.md` and `JAVA_BACKEND.md` for authority.

## Array nullability is invariant across assignment

`SolvikType.arrayAssignable` requires the element type to match exactly at every level; only the
array's own `?` (`T[N]` → `T[N]?`) and length erasure (`T[N]` → `T[]`) widen. Allowing
`String[2]` → `String?[2]` was unsound because the carrier is a mutable `java.util.List`: a `Null`
written through the wider binding made a `String`-typed element null. Element widening is still
allowed where the array owns the slot (`String?[2] {"a", "b"}` literals and `a[i] = value`).

## Array length is the immutable member `size`

Every array type has exactly one member, `size` (`TypeChecker.ARRAY_SIZE_MEMBER`), typed `Integer`
and read without parentheses. It lowers to the carrier's `List.size()` call
(`JavaLowerer.member`), so a fixed `T[N]` and a size-erased `T[]` read the same way and the emitted
Java matches the former call form. The removed call form is `SOL-T001` with a hint
(`checkMemberCall`), a write is `SOL-T010` (`checkAssign`), and a nullable receiver is `SOL-T015`.
`LANGUAGE.md` §5/§13.1 records the change. `String.size()` and the packaged collections keep the
function form, so `text.size` gets a hint pointing at `text.size()`. Removing the old checker branch
also un-reserved `size` for user types: a type may declare and call its own `size()` again.

## Static initializer blocks run through a generated void method

`static { ... }` is an `Ast.StaticInitializer` carrying the number of fields declared before it;
`Ast.TypeDecl.staticInitializers` holds at most one (a second is `SOL-P004`, also rejected by the
checker for hand-built ASTs). The source convention places it after all fields and before the first
function; `JavaLowerer.classUnit` emits it after those fields. Statements live in a private
static void `$solvikStaticInitN` method called from the block: javac rejects a static initializer
that cannot complete normally (for example one containing only `raise`), while a void method may
throw freely. The checker runs a block in static context with no `self`, no type parameters, and no
`return` (`SOL-T005`); it may construct the enclosing type through its private constructor. Traits
and `native "class"` declarations reject blocks at parse time (`SOL-P004`). Compiler-generated regex
pattern fields are emitted before user fields/blocks so a block may use a regex switch. The CLI
unwraps `ExceptionInInitializerError`/caused `NoClassDefFoundError` before classifying a failed
class initialization, so a `raise` reports `SOL-R013`, `exit` carries its status, and a `SOL-Rxxx`
abort keeps its code; none is catchable by a Solvik `try`. See `plans/static-initializers.design.md`
and `LANGUAGE.md` §3.4.

## Built-in names are reserved

`TypeChecker.checkNamespace` rejects (`SOL-T001`):

- any declared `type`/`trait` named `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`,
  `BigInteger`, `BigDecimal`, `String`, `Boolean`, `Any`, or `Number` (top level and in modules);
- a *top-level* function named after one of those types or `print`/`println`/`exit`.

Module functions and type members may keep those names because their calls are qualified or
member-selected (`m::Integer(1)`, `self.exit()`). Before this rule, a type named `String`/`Integer`
crashed lowering with `NoSuchElementException` or emitted Java that did not compile.

## Generated Java names

- `$` never appears in a Solvik identifier, so `$`-suffixed names are collision-free.
- `GeneratedNames.functionName` appends `$function` to static functions named `equals`,
  `hashCode`, `toString`, or any support member in `SUPPORT_MEMBER_NAMES` (kept equal to the
  `GeneratedIntrinsics` registry by `GeneratedIntrinsicsTest`).
- The program class's nested support types are `$solvikError` and `$solvikExit`
  (`GeneratedNames.ERROR_CLASS`/`EXIT_CLASS`); `GeneratedNames.errorBinaryName()`/
  `exitBinaryName()` are what `Main` compares against. A user `type Error` therefore stays
  `solvik.Error` and does not bind to the support class.

## Reference casts of signed operands are parenthesized

Java's reference cast takes a `UnaryExpressionNotPlusMinus` (JLS 15.16), so `(Object) -1` is a
parenthesized expression followed by a subtraction, not a cast — javac rejects it. The boxed form
of a negated literal therefore renders as `(Object) (-1)`
(`JavaSourceRenderer.castOperandNeedsParentheses`). The bug showed up as "generated Java did not
compile" for `println(-1 .. "x")`, `String(-1)`, `println(-1, "x")`, and `-1 == Null`; the rule is
pinned by `JavaSourceRendererTest.parenthesizes_a_signed_cast_operand` and the execution tests
`ValueSemanticsExecutionTest.negative_literals_render_through_the_object_carrier` and
`NullValueExecutionTest.negative_literals_compare_with_null`.

## Newlines and delimiters

`Parser.delimiterDepth` > 0 makes `peek`/`lookahead` skip `NEWLINE`, so `(...)`, `[...]`, and
type-argument `<...>` may span lines; `parseArguments`, parameter lists, array literals, blocks,
and type bodies are handled as before. Outside a delimiter a newline still ends a statement. A `\`
as the first non-blank character of a continuation line cancels the newline since the statement's
last line (lexer; `SOL-L006` for a misplaced marker, and a semicolon still terminates). This is
documented in `LANGUAGE.md` §1, pinned by `LanguageConsistencyExecutionTest` and `LexerTest`.

## Known tensions in LANGUAGE.md (do not "fix" silently)

- §3.3 shows `pub type Counter` at file scope, but top-level `pub` is rejected (`SOL-P004`).
- §3.4 calls `visitor.visit()` where `visit() -> Integer`; the compiler rejects an unused call
  result (`SOL-T001`), and §11.1's statement-only convention depends on that rule.
- `exit(300)` with a literal is a compile-time `SOL-T001`; computed or negative codes are runtime
  `SOL-R007`.

## Exception handling is one type, and catchability is structural

Solvik has one unchecked exception type (`LANGUAGE.md` §7.4). It carries an `Integer` code and a
`String` message, exposes only `getCode()`/`getMessage()`, and lowers to the nested support class
`Solvik$Program.$solvikException`. `raise` is a Java `throw`, and `catch (e: Exception)` is a Java
`catch (Solvik$Program.$solvikException e)` — so a `SOL-Rxxx` abort (`$solvikError`), an `exit(n)`
(`$solvikExit`), and any Java exception from a native call pass a `catch` by *type*, with no emitted
check and no runtime cost. Do not make the abort or exit class a subtype of the exception class:
that would silently change every documented abort into a catchable event, and the abort line is
printed at throw time by `fail(...)`.

Decisions worth keeping: codes are non-negative, checked once in the generated constructor
(`SOL-R012`, mirroring `exit`'s computed-code `SOL-R007`); an uncaught exception is reported by
`Main` as `SOL-R013` with the program's own code and message; a `try` takes **one** `catch` at most
and needs a `catch` or a `finally` (`SOL-P002`); `return` inside `finally` is rejected (`SOL-T017`)
because Java would silently discard the pending exception or value, while `break`/`continue`/`raise`
keep Java's behavior; a caught value is non-null, and `raise Null`/`raise maybe` are `SOL-T001`/
`SOL-T015`.

Implementation details that are easy to break: `else`-style layout applies, so `} catch (...) {`
must share a line (`Parser.requireClauseOnSameLine`); the checker's `terminates` is now
compositional (`raise`, and a `try` whose every exit clause leaves), which drives both the
unreachable-statement rule and the null-narrowing facts after an `if`; and `GeneratedIntrinsics.type()`
only names `Exception` (instead of the raw class name) when the program already emits the exception
class, so `.type()` does not pull the class into a program that never uses exceptions. The support
class's constructor is `public` (unlike `$solvikError`'s), because a `raise` lowered into a module's
function holder or a generated type class lives in a different Java package.

Corpus, tests, and docs that pin this: `example.sol` §"exception handling", `ExceptionTest`,
`ExceptionExecutionTest`, `MainTest`, the `ExampleCorpusTest` stdout assertions,
`JAVA_BACKEND.md` "Exception handling".
