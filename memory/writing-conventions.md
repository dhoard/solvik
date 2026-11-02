# Writing conventions

Project-wide rules stated by the maintainer for code, comments, and documentation. They are not
language semantics; they constrain wording only.

## USA English spelling

All code and documentation use USA English spellings. Observed corrections (2026-11): `cancelled` →
`canceled`, `behaviour` → `behavior`, `travelling` → `traveling`. Note that `cancellation` keeps its
double `l` in USA English, and `-ise`/`-isation` forms (`normalise`, `serialise`, `initialisation`)
are non-USA.

A `grep -rniE` sweep over `*.java`, `*.md`, `*.sol`, `*.yml`, `*.xml` for the usual UK forms is the
practical check; identifiers were already USA-spelled, so the fixes were comments, one test display
name, and docs.

## Documentation must match the code

The maintainer requires documentation claims to be verified against the current source, with the code
as the source of truth. When documenting the CLI, check `Main`'s `USAGE` constant and dispatch, the
cache-key inputs in `CacheKey.of`, and the loaded-program scope in `ProgramLoader.load` rather than
restating older prose.
