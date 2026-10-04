# Statement Separation and Brace Placement — Revision Plan

Status: **in progress** on branch `feature/physical-line-separation`.
Language decision approved by the owner (option **C** plus brace/case-body rules).
The rejected alternative (ban `;` and delete the three-clause `for`) is preserved on
`wip/physical-line-semantics` (see `git log`) and must not be merged as it stands: it contradicts
`docs/LANGUAGE_SPEC.md` sections 16 and 17, which `REQ-0403`, `REQ-0501`, `REQ-1204`, `REQ-1404`,
`REQ-1900`..`REQ-1907` quote normatively.

## Rules being implemented

1. **Statement separation is a physical-line boundary token.** `PhysicalLineTokenSource` moves one
   `NEWLINE` onto the default channel per line boundary (suppressed while `(`/`[` depth is nonzero;
   no lookahead exceptions — the grammar writes `NEWLINE*` where a line may continue). The grammar
   *requires* a separator between statements, declarations, and members: `separator: NEWLINE | SEMI`.
2. **`;` stays legal as an in-line separator.** It is one of the two separator alternatives, so
   `val a = 1; val b = 2` is legal and `include "x";` keeps working. It never terminates a line: the
   boundary after a line-final `;` is what a declaration consumes, and a run of separators between two
   constructs is tolerated exactly as a blank line is.
3. **Brace placement.** After `{` only whitespace and comments may follow on that line
   (`SOLV-PARS-010`). `}` must begin its physical line and nothing but whitespace/comments may follow
   it on that line (`SOLV-PARS-007`); that single rule also forbids `} else`, `}`, `};`, `})` and
   `} ,`. A clause keyword (`else`, `catch`, `finally`) must begin its own line
   (`SOLV-PARS-008`) and a body's `{` must sit on the line of the construct introducing the scope
   (`SOLV-PARS-009`). An empty body is written as
   ```solvik
   {
   }
   ```
   — `{}` on one line is illegal. Consequence: a bracketed block closes its brackets on their own
   lines (`println(\n    if (c) {\n        1\n    }\n    else {\n        2\n    }\n)`).
4. **Case bodies are braced.** `case 1, 2: { ... }` and `default: { ... }` for both the statement and
   the expression `switch`; the value-producing form keeps its tail expression inside the braces.
5. **Unchanged:** the three-clause `for` keeps its specified spelling
   `for (mutable val i: Integer = 0; i < limit; i = i + 1)`, because `;` remains legal. Range
   `for`-in, `match` arms (`pattern => body`), and every other construct are untouched.

## Diagnostics

`SOLV-PARS-007` closing brace shares a line · `008` clause keyword not at line start ·
`009` body brace not on the introducing line · `010` content after an opening brace.
`SOLV-PARS-011` (removed three-clause `for`) is **not** implemented; the form is not removed.

## Measured blast radius

* `.sol` corpus: 664 files. 64 contain `}` followed by code (45 under `tck/corpus`): 13 `} else {`,
  3 `} else { expr }`, 8 `} finally {`, ~15 `} catch (e: T) {`, 3 `})`, 1 `{}`.
* Case bodies: 39 `.sol` files (7 in `language/tests`, 31 in `tck/corpus`).
* Java test sources with embedded Solvik text blocks: 92 files.
* Requirements: revise the section-16 group (`REQ-0403`, `REQ-1204`, `REQ-1404`, `REQ-1900`..`1907`),
  revise `REQ-0801` ("Each case body is an implicit block"), add requirements for the four brace
  rules and for braced case bodies. `REQ-0501` (three-clause `for`) survives unchanged.
* Every `solvik` code block in `docs/LANGUAGE_SPEC.md` and the quoted spec sentences must be
  re-laid-out/re-worded consistently; `./tck/requirements/sync.sh` then re-derives `requirements.md`.

## Ordered work

1. Mechanics: grammar `separator`, three-clause `for` restored, `PhysicalLineTokenSource` treats `;`
   as a line ending, `PhysicalLineRules` strict (no `{}` deviation, no bracketed-`}` deviation),
   drop the `statementCore?` tail option that existed only for the removed deviations.
2. Braced case bodies: grammar, `SolvikAstBuilder`, semantic validation, lowering.
3. Compile clean, then migrate the corpus: `language/tests`, `language/tests/regression`,
   `tck/corpus`, and the Solvik text blocks inside Java tests (scripted re-layout; goldens must not
   change).
4. Rewrite spec sections 16, 13, 21.2/21.3 and the section-27 diagnostic table; re-lay out every
   example program in the spec.
5. Update `tck/requirements/requirements.json` (+ `sync.sh`), re-record orphans, re-run
   `tck/tck-run.sh` against `standalone/target/solvik`.
6. Add `language/tests/example.sol` (+ `example.output`) exercising every language construct; it
   runs under the shipped launcher through `test-corpus.sh`.
7. Tests for each rule (positive and negative), token-stream tests for `;` plus boundary, parser tests
   for braced case bodies, three-clause `for` tests.
8. `./build-all.sh` as the final gate.
