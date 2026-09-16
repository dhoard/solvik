# Phase 3 — Rust-Style Raw Strings

Execute only when `docs/STATUS.md` marks Phase 3 as `NEXT`.

## Required Result

Implement the exact counted-delimiter raw strings in `docs/LANGUAGE_SPEC.md`:

```solvik
r"abc"
r#"Test '"#
r##"contains "# text"##
```

The lexer must:

- recognize only a contiguous raw-string prefix and opening quote;
- match the closing quote followed by exactly the opening hash count;
- preserve backslashes, quotes, and physical newlines in the value;
- perform no escapes or interpolation;
- emit one token for the complete literal;
- report an unterminated literal at its opening delimiter and name the expected closing delimiter.

Raw-string internal newlines must be invisible to semicolon insertion.

## Validation

Add lexer, parser, AST-value, diagnostic, and semicolon-interaction tests covering every case required by `docs/TEST_PLAN.md`.

Run focused lexer/parser tests and `./build.sh`. Update `docs/STATUS.md`, set Phase 4 as `NEXT`, and stop.
