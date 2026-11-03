# `example.sol` corpus layout conventions

Recorded 2026-11 after reformatting the corpus for readability. The file is the executable
inventory of the language, so every construct must stay live; these rules are about presentation
only.

## Shape

- One block-comment header per section: `/*`, ` * Title (§x.y)`, ` */`, then exactly one blank
  line. A `§` reference appears only where one already did; the numbers are not invented.
- One writer for the whole transcript: `out := solvik::io::TextWriter(solvik::io::Stdio.stdout())`
  near the top, then `out.writeLine(...)` / `out.write(...)`. The fully qualified constructor stays
  spelled out in four places on purpose: the binding itself, the `` `captured to memory` `` line
  after `Stdio.setStdout` (it must observe the *current* stdout), and two function bodies. A
  top-level binding is not visible inside a `func` or type body (`SOL-T001 unknown name`), which is
  why those bodies construct their own writer.
- Maximum 100 columns. Long `writeLine` calls break *inside* the parentheses at top-level `..`
  operands, four spaces deeper than the statement, with the operator leading the continuation line.
  No `\` marker is needed there: a newline inside a delimited construct is layout (§1).
- No operand parentheses. `..` is the loosest operator (see
  [concatenation-precedence.md](concatenation-precedence.md)), so `"label: " .. x + 1 .. " " .. y`
  needs no grouping. The only parentheses kept around a `..` chain are the required grouping for a
  concatenation that is itself an operand, e.g. `("id:" .. 1) == "id:1"`.
- Trailing comments use exactly two spaces and are never column-aligned; alignment rots on the next
  edit and made the pre-cleanup file look ragged.
- `.. ""` appends are noise: `writeLine(text)` accepts a plain `String`. The 31 that existed were
  removed.

## Traps

- **Block comments nest** (depth counting in `Lexer.skipBlockCommentQuietly`). Anything containing
  `/*` or `*/` must stay a `//` comment — this is why the "what this file cannot show" appendix,
  which quotes `/* open` as the `SOL-L005` example, is line comments. Converting that list to a
  block comment makes the compiler report `SOL-L005` at `1:1` and swallow the rest of the file.
- Both comment forms must stay in live use: the top-of-file paragraph is the only place that
  demonstrates nesting, and line comments remain throughout (trailing notes, rejected-code lists,
  the "comment-only line" continuation demo).
- The statement-continuation demo (`banner := "continuation"` then `\ .. " joined across lines"`)
  must stay exactly one `\` line: it is the live `SOL-L006`-adjacent example.
- `LANGUAGE.md` does not specify comment syntax at all — only a passing mention in §1 and a lumped
  `SOL-L001`–`SOL-L007` diagnostics row. The corpus is currently the de facto description.

## Verification after formatting edits

`./solvik run example.sol` must reproduce the previous transcript byte for byte except the random
`uuid:` line (`sed 's/^uuid: .*/uuid: <random>/'` makes the diff exact); `./solvik check
example.sol` plus `ExampleCorpusTest` cover compile, run, and warning-free generated Java.
