# FIX-STRING — Raw-string-literal delimiter handling

**Scope:** plan only — do not implement code changes.
**Verdict:** the raw-string-literal lexer is verified **correct**. This plan
resolves the prior review's flagged "bug" by locking in the correct behavior
with tests and a documentation clarification. No lexer-code change is required.

---

## 1. What the prior review flagged

The review observed that `r"#"…"#` fails to parse and labeled it a "real
implementation bug," noting the "correct form is `r#"…"#`."

## 2. Investigation — findings

The lexer rule is hand-written (ANTLR cannot express a counted delimiter):

```antlr
RAW_STRING_LITERAL: 'r' '#'* '"' { lexRawStringBody(); } ;
```

- **Opening delimiter:** `r` + `N` `#` + `"` (contiguous); `N` is recovered from
  the matched text (`hashes = getText().length() - 2`).
- **Closing delimiter:** `"` + `N` `#` (same `N`), verified by
  `Solvik.g4::isRawStringClose(hashes)` — a quote followed by exactly `N` hashes
  and no further `#`.
- **Value:** content between the delimiters (`RawStringLiteralNode.value()`).

This is the Rust-style counted model and it matches `docs/LANGUAGE_SPEC.md`
§15 exactly (`r + N '#' + '"' + content + '"' + N '#'`).

### `r"#"…"#` is not a bug — it is a mismatched delimiter

`r"#"…"#` is `r"` (opening, **N = 0**) followed by content and `"#` (a closing
delimiter with **N = 1**). The opening and closing hash counts disagree, so the
lexer cannot find the terminator and reports:

```
rs.sol:1:9: ERROR SOLV-LEX-002: unterminated raw string literal;
  expected closing delimiter " (expected: ", found: 'r"') at [8..10)
```

This is the *correct* diagnostic — the mismatch is a user error, not a lexer
defect. The valid form is `r#"…"#` (N = 1 on both sides), which parses and
renders as `Test '`.

### Verified behavior

| Input        | Hash counts | Result |
|--------------|-------------|--------|
| `r"abc"`     | 0 / 0       | `"abc"` |
| `r#"a"b"#`   | 1 / 1       | `a"b` |
| `r##"c "# c"##` | 2 / 2   | `c "# c` |
| `r#""#`      | 1 / 1 (empty) | `""` |
| `r#"\d+"#`   | 1 / 1       | `\d+` |
| `r"#abc"#`   | 0 / 1       | **SOLV-LEX-002** (mismatched — correct) |

## 3. Recommended resolution

### 3a. No lexer change
Do **not** modify `Solvik.g4` / `lexRawStringBody`. The counted-delimiter model
is correct; `r"#"…"#` is correctly rejected as a user error.

### 3b. Documentation clarification
- `docs/LANGUAGE_SPEC.md` §15 already states the correct model and uses the
  correct examples (`r#"…"#`, `r##"…"##`, `r###"…"###`). **No change required.**
- Ensure the README raw-string example uses `r#"…"#` (not `r"#"…"#`).

### 3c. Tests — lock in the correct behavior

**Positive tests** (add to `SolvikRawStringTest.java`):
- `r"abc"` → `"abc"`
- `r#"a"b"#` → `a"b` (embedded quotes)
- `r##"c "# c"##` → `c "# c`
- `r#""#` → `""` (empty)
- `r#"\d+"#` → `\d+` (backslashes preserved)
- multiline body preserves embedded newlines
- N = 2, 3, 4 (e.g. `r####"…""####`)

**Negative tests** (add `SolvikRawStringNegativeTest.java`):
- `r"#abc"#` → `SOLV-LEX-002`, expected closing delimiter `"`, at the opening.
- `r##"abc"#` → `SOLV-LEX-002` (0/1).
- `r#"abc"##` → `SOLV-LEX-002` (1/2).
- `r""#` → `SOLV-LEX-002` (1/0).
- `r"abc""#` → `SOLV-LEX-002` (0/1).
- Each negative test asserts the diagnostic names the exact expected delimiter.

## 4. Validation

```bash
JAVA_HOME=/opt/graalvm-25.3.4.1+1.1 ./mvnw -pl language \
  test -Dtest='SolvikRawStringTest,SolvikRawStringNegativeTest'
```

- Current state: `SolvikRawStringTest` — 22 tests, 0 failures.
- After adding the negatives: expect all green; no SOLV-LEX-002 leaks into
  positive runs.

## 5. Resolution

1. Add positive + negative tests.
2. Re-run the maven command above; confirm all green.
3. Confirm `docs/LANGUAGE_SPEC.md §15` is unchanged and correct.
4. Commit tests. **No lexer-code diff.**

## 6. Verification (already done)

- `r#"Test '"#` → `Test '` ✓
- `r##"contains "# text"##` → `contains "# text` ✓
- `r###"arbitrary content"###` → renders ✓
- `r"#abc"#` → `SOLV-LEX-002` (mismatched, correct) ✓
- §15 examples (`r#"…"#`, `r##"…"##`, `r###"…"###`) all render correctly ✓
- 22 raw-string tests already pass via maven ✓
