# Phase 2 — Lexical Semicolon Insertion

Execute only when `docs/STATUS.md` marks Phase 2 as `NEXT`.

## Required Result

Implement the exact algorithm in `docs/LANGUAGE_SPEC.md`, between the Solvik lexer and parser.

- Preserve physical newline information in the lexer.
- Emit synthetic `SEMI` tokens in a token-source or token-stream layer.
- Give explicit semicolons and synthesized semicolons the same parser token type.
- Handle delimiter nesting, comments, blank lines, `return`, `else`, end of file, and leading `.`/`?.` continuation exactly as specified. Test `?.` at the token-stream layer until nullable member access is added in Phase 10.
- Keep insertion deterministic and independent of parser errors or recovery.

Do not implement raw strings in this phase.

## Validation

Add token-stream tests and parser/AST equivalence tests for explicit-semicolon and newline forms. Add negative tests for incorrectly continued statements and regression tests proving no insertion after operators, commas, or unmatched opening parentheses and brackets.

Run focused lexer/parser tests and `./build.sh`. Update `docs/STATUS.md`, set Phase 3 as `NEXT`, and stop.
