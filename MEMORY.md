# Project Memory

This file indexes useful project knowledge retained across agent sessions. It is historical context,
not an authoritative source of requirements; verify details against `PROJECT.md` and current code.

## Entries

- [Writing conventions](memory/writing-conventions.md) — maintainer rules for USA English spelling
  across code and docs, and the requirement that documentation claims be verified against the current
  source.
- [Bundled native bindings and standard I/O](memory/io-native-bindings.md) —
  trusted `native` declarations and `BuiltinBindings`, the generated `Solvik$Io` adapter and
  `$payload`/`$of` shape, `IoError` codes, the packaged `Channel`/`Stdio`/`TextReader`/`TextWriter`
  surface, reachability filtering of unreached packaged types, the `File`/`ByteReader`/`ByteWriter`
  additions, and the removed `print`/`println` migration convention.
- [Include and file-local alias semantics](memory/include-alias-semantics.md) — `include`/`alias` are
  reserved keywords and `import` is removed (`SOL-P006`); mandatory blocks with newline/`;`
  separators; file-keyed alias tables, module-scope-over-alias precedence, canonical targets
  (`SOL-T018`), and the recorded `Ast.TypeRef` types that keep alias spellings out of generated Java.
- [Collection iteration](memory/collection-iteration.md) — the declared `iterator()` protocol
  (`TypeChecker.ITERATOR_TYPE`), the `JStmt.For` desugaring, why a generic native trait is a
  Solvik-owned interface with a `$of`/`$Adapter` conversion, the `checkedResult` support member, and
  the reachability consequence that reaching `List`/`Stack` emits `Iterator.java`.
- [Type-body name resolution](memory/type-body-name-resolution.md) — a syntax-only
  `TypeChecker.indexDeclarations` pass indexes every declared type before any type is built, so a
  type body may name packaged, module, and later-declared types; provisional `userTypes` entries,
  the one remaining order dependence (another type's field in a field initializer), and the
  workstation JDK note.
- [Language conformance invariants and naming rules](memory/language-conformance-invariants.md) —
  array element nullability invariance, reserved built-in names, `$`-suffixed generated names
  (`$function`, `$solvikError`/`$solvikExit`/`$solvikException`), the parser's delimiter/newline model,
  structural catchability of the one exception type, and recorded tensions between `LANGUAGE.md`
  examples and enforced rules.
- [`example.sol` corpus layout conventions](memory/example-corpus-layout.md) — the block-comment section
  headers, the single top-level `out` writer and the four places that must keep the fully qualified
  constructor, the 100-column `..` wrapping style, and why the `/* open` and continuation demos must
  stay line comments.
- [`..` is the loosest operator](memory/concatenation-precedence.md) — the precedence decision (render
  last), the parser seam it lives in, the grouping that is still required, the `SOL-T001` that keeps
  the change from being silent, and the generated-Java diff check for formatting sweeps.
- [`sync` blocks and monitor semantics](memory/sync-monitor-semantics.md) — `sync` lowers to one Java
  `synchronized` statement so release is the JVM's; the user-type/array monitor rule (`SOL-T019`,
  nullable `SOL-T015`), the five typed-IR seams, the `terminates` arm, the active shape tests, and the
  `GeneratedOutputOptimizationTest` `@Test` trap.
- [Map enumeration and the trait-owner `$of` conversion](memory/map-enumeration.md) — `Map.keys()` /
  `values()` over two packaged native traits (`Set<T>`, `Collection<T>`) and why they must be two,
  the live-view and unordered-iteration rules, the textual-reachability consequence, the
  `traitAdapter`/`nativeMember` result conversion now shared as `Unit.nativeResult`, the
  `checkedResult` loop guard, and why entry-wise iteration stays deferred.
