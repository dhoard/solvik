# `..` is the loosest operator

Recorded 2026-11 while changing the precedence ladder at the maintainer's direction ("minimal `()`
requirements"). `LANGUAGE.md` §4.3 is the contract; this note records the reasoning and the traps.

## The rule

Lowest to highest: `..`, `||`, `&&`, `==`/`!=`/`===`, `<`/`<=`/`>`/`>=`, `+`/`-`, `*`/`/`/`%`,
unary `-`/`!`, postfix. `..` sits below every other binary operator because concatenation *renders*
values that are already fully evaluated: a comparison, condition, or arithmetic expression is an
operand without grouping.

```solvik
out.writeLine("total: " .. 1 + 2 * 3 .. " ok: " .. count > 0)   // renders "total: 7 ok: true"
```

Grouping is required only when a concatenation is itself an operand, e.g. comparing rendered text:
`if ("id:" .. id) == "id:7" { … }`. Without those inner parentheses the `==` would compare `id` with
the literal text, which is `SOL-T001` when the carriers differ.

## Implementation

- `Parser.parseExpression` → `parseConcatenation` → `parseDisjunction` → … → `parsePostfix`;
  `parseComparison` parses `parseAdditive` operands. Precedence is parser-only: the AST, checker,
  lowering, typed IR, Java renderer, cache key, and diagnostics are untouched, and parentheses never
  produced AST nodes.
- `ParserTest.follows_the_settled_precedence_table`, `concatenation_is_the_loosest_operator`, and
  `parentheses_still_override_concatenation` pin the ladder. Keep the `&&`/`||` cases: they are what
  distinguishes "below equality" from "below everything".

## Migration and verification

- An unparenthesized `a .. b == c` changes from `(a .. b) == c` (Boolean) to `a .. (b == c)` (String).
  A Boolean context now rejects it with `SOL-T001`, so nothing changes silently; code that compares a
  concatenation adds parentheses.
- Safe check for any formatting sweep: capture `--keep-sources` output before and after and `diff -r`
  the generated Java. Removing redundant parentheses leaves it byte-identical unless the sweep also
  flattens nested `..` groups, which is allowed because `..` is left-associative.
- `example.sol` carries one live "required grouping" line, `out.writeLine("grouped concatenation: " ..
  ("id:" .. 1) == "id:1")`, pinned by `ExampleCorpusTest`. Do not "simplify" it away.
