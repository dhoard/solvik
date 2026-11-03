# Include and file-local alias semantics

Implemented from `plans/include-alias-semantics.md`; `LANGUAGE.md` §10.1/§10.2 is the contract.

## Surface

- `include` and `alias` are reserved keywords (`TokenKind.INCLUDE`/`ALIAS`, inside the
  `VAR..RAISE` keyword range). Any binding, field, parameter, or type spelled `include` or `alias`
  is now a parse error, which is why the corpus and tests were migrated.
- `import` is retired but still lexed as `TokenKind.IMPORT`; the parser reports `SOL-P006` once and
  skips the line (or the `{ ... }` block) naming both replacements.
- Both blocks are mandatory, including for one entry: `include "a.sol"` and `alias m::A A` are
  rejected with `SOL-P002`. Entries are separated by a newline or `;`, mixed and repeated; commas
  are rejected with `SOL-P002`; an empty block is `SOL-P007`; both blocks are file-level only.

## Resolution

- `Ast.Program` carries `includes` and `aliases`; the loader drops includes after following them and
  keeps aliases because each `Ast.AliasDecl` keeps the `SourcePos` of the file that wrote it.
- `TypeChecker.buildAliasTables` builds `Map<file, Map<local, canonical>>` after declarations are
  collected and before any body is resolved. `SourcePos.file()` is the file key, so a merged
  program keeps file-local scope.
- Unqualified order: local/parameter (elsewhere), enclosing module declaration, file alias,
  top-level declaration. Qualified: expand the leading segment once (`fb::Parser` →
  `foo::bar::Parser`), never recursively; an unaliased canonical path is unchanged.
- Alias targets are canonical and must be a module, type, or trait visible at file level (a `pub`
  module member). Strict policy: an alias never widens visibility, and point-of-use visibility is
  still checked. All alias errors are `SOL-T018`.
- A module-local declaration shadows a same-spelled alias inside that module; the alias still
  applies outside it. Top-level collisions, built-in type names, `exit`, and the reserved `solvik`
  root are rejected at declaration instead of being shadowed.

## Backend

`TypeChecker.Result.typeReferenceTypes` records the canonical `SolvikType` of every written
`Ast.TypeRef`, and `JavaLowerer.Unit.resolve` reads it before falling back to its own textual
lookup. This is what keeps an alias spelling out of generated Java (class names,
`implements` lists, parameter and return types). The fallback still covers written types the
checker never resolved.

## Tests

`IncludeAliasSyntaxTest` (syntax/separators/removed forms), `AliasTest` (semantics, per-file
isolation, generated Java, `SOL-T018` rejections), `IncludeTest` (renamed from `ImportTest`), and
`MainTest`'s cache tests. `testsupport/GeneratedProgram` compiles with `javac --release 25` against
an empty classpath and runs the entry point for output comparisons.
