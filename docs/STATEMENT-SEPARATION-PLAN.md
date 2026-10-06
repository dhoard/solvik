# Statement Separation and Brace Placement — Revision Record

Status: **complete** on branch `feature/physical-line-separation`, validated by
`./build-all.sh` and `./tck/tck-run.sh`.
Language decision: the final revision bans line-terminating `;` AND removes the three-clause
`for`. The intermediate design that retained both (`option C`) landed first as the mechanics
step (boundary token, braced case bodies, brace-layout diagnostics) and was then tightened;
the earlier revision that had banned `;` is preserved on `wip/physical-line-semantics`
(see `git log`). The specification (sections 16, 17, 21.3) is authoritative for the final rules
below, and `REQ-0403`, `REQ-0501`, `REQ-1204`, and `REQ-1900`..`REQ-1907` quote them verbatim.

## Rules implemented

1. **Statement separation is a physical-line boundary token.** `PhysicalLineTokenSource` moves one
   `NEWLINE` onto the default channel per line boundary (bracket depth is not consulted; no
   lookahead exceptions — the grammar writes `NEWLINE*` where a line may continue). The grammar
   *requires* a separator between statements, declarations, and members: `separator: NEWLINE | SEMI`.
2. **`;` separates two constructs on one physical line and never terminates one.**
   `var a = 1; var b = 2` is legal. A `;` followed by another physical line, end of file, or a
   stand-alone closing brace is rejected at the semicolon as `SOLV-PARS-012`, so `include "x";`,
   `foo();` at a line end, and `{ 42; }` are all errors; the grammar stays line-shape-free and the
   layout pass states the rule once.
3. **Brace placement.** After `{` only whitespace and comments may follow on that line
   (`SOLV-PARS-010`). `}` must begin its physical line and nothing but whitespace/comments may
   follow it on that line (`SOLV-PARS-007`); that single rule also forbids `} else`, `};`, `})` and
   `} ,`. A clause keyword (`else`, `catch`, `finally`) must begin its own line
   (`SOLV-PARS-008`) and a body's `{` must sit on the line of the construct introducing the scope
   (`SOLV-PARS-009`). An empty body is written as
   ```solvik
   {
   }
   ```
   — `{}` on one line is illegal. Consequence: a bracketed block closes its brackets on their own
   lines (`println(\n    if (c) {\n        1\n    }\n    else {\n        2\n    }\n)`).
4. **Case bodies are braced with no colon.** `case 1, 2 { ... }` and `default { ... }` for both the
   statement and the expression `switch`; each body is a real lexical scope and the value-producing
   form keeps its tail expression inside the braces.
5. **The three-clause `for` is removed.** Its header would require semicolons section 16 reserves
   for separating constructs on one line. The spelling at the `for` keyword is rejected with its own
   code (`SOLV-PARS-011`) that names the replacements: a range `for` or a scope block around a
   `while` loop. Range `for`-in, `match` arms (`pattern => body`), and every other construct are
   untouched.

## Diagnostics

`SOLV-PARS-007` closing brace shares a line · `008` clause keyword not at line start ·
`009` body brace not on the introducing line · `010` content after an opening brace ·
`011` removed three-clause `for` header · `012` a `;` that terminates rather than separates.

## Work completed

1. Mechanics: grammar `separator`, `PhysicalLineTokenSource` boundary placement,
   `PhysicalLineRules` strict (no `{}` deviation, no bracketed-`}` deviation), the
   `statementCore?` tail option for the removed deviations dropped.
2. Braced case bodies: grammar, `SolvikAstBuilder`, semantic validation, lowering.
3. Corpus migration: `language/tests`, `language/tests/regression`, `tck/corpus`, and the Solvik
   text blocks inside Java tests (scripted re-layout; no golden output changed).
4. Spec sections 16, 17, 13, 21.2/21.3 and the diagnostic tables rewritten; every example program
   in the spec, README, and architecture re-laid out.
5. `tck/requirements/requirements.json` revised with the section-16/17/21.3 requirements, the
   three-clause-`for` requirement re-recorded for the scope-plus-`while` idiom, new coded arms for
   `SOLV-PARS-012` (`SOL-TCK-0496`..`0498`) and a bare rejection for the removed `for`
   (`SOL-TCK-0499`); all generators re-run and `verify_regen.py` drift-free.
6. Per-rule positive and negative tests (`SolvikStatementTerminationTest`,
   `SolvikPhysicalLineLayoutTest`, `PhysicalLineTokenStreamTest`, switch/control-flow parser tests),
   token-stream tests for `;` plus boundary, and parser tests for braced case bodies.
7. `./build-all.sh` as the final gate; `./tck/tck-run.sh` conformance against both distributions.
