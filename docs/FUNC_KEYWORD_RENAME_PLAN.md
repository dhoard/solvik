# Function Keyword Rename Plan: `fun` to `func`

## Problem

Solvik spells its function declaration keyword `fun`:

```solvik
fun add(a: Int, b: Int): Int {
    return a + b
}
```

The requested language spelling is `func`. Because the keyword is defined once in the grammar and
then used by the lexer, parser, diagnostics, tests, examples, and documentation, a partial change
would leave the repository inconsistent and could leave one path accepting the old spelling.

## Goal

`func` is the only function-declaration keyword. The grammar, generated parser, diagnostics,
parser/semantic tests, execution tests, `language/tests/*.sol` examples, and documentation all use
`func`. No supported path accepts `fun`.

## Non-goals

- No compatibility mode, alias, or dual keyword. `fun` becomes an identifier, exactly like any
  other identifier that is not a reserved word (`AGENTS.md`, "Do not add a compatibility flag,
  dual parser, legacy language mode").
- No change to function semantics, types, arity, entry-point rules, or `function` legacy rejection.
- No unrelated refactoring.

## Decision

Rename the grammar token `FUN` to `FUNC` and its literal `'fun'` to `'func'`, then regenerate the
parser from the grammar so no generated file is hand-edited. Replace every remaining keyword
reference in Javadoc, diagnostic strings, tests, examples, and documentation. The SimpleLanguage
`function` legacy keyword remains rejected; only the expected-token spelling recorded in the
diagnostic changes from `'fun'` to `'func'`.

## Changes

1. `language/src/main/java/org/solvik/parser/grammar/Solvik.g4`
   - `FUN: 'fun'` becomes `FUNC: 'func'`;
   - the four uses in `functionDecl`, `signatureDecl`, `defaultMethodDecl`, and `methodDecl`;
   - header comments that name the keyword.
2. Regenerated parser artifacts (only via `generate_parser.sh`):
   `SolvikLexer.java`, `SolvikParser.java`, `SolvikVisitor.java`, `SolvikBaseVisitor.java`,
   `.tokens`, `.interp`.
3. `language/src/main/java/org/solvik/parser/SolvikErrorListener.java`
   - the legacy-syntax diagnostic's expected value becomes `'func'`;
   - the legacy classification check recognizes `'func'` in the expectation set.
4. Javadoc and diagnostic-message references:
   `FunctionDeclNode`, `InterfaceDeclNode`, `SignatureDeclNode`, `CheckedProgram`,
   `SolvikSemanticAnalyzer`, `SolvikEvalRootNode`, `ListType`.
5. Tests: replace the keyword in every Solvik source string and rename `SolvikLexer.FUN` to
   `SolvikLexer.FUNC`.
6. Examples: `language/tests/*.sol`.
7. Documentation: `docs/LANGUAGE_SPEC.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/TEST_PLAN.md`,
   `prompts/PHASE_1_FRONTEND.md`, and `docs/STATUS.md`.

## Tests

- Positive: the parser and semantic tests, execution tests, and the seven `language/tests/*.sol`
  examples compile and run with `func`.
- Negative: `SolvikParserNegativeTest` still classifies SimpleLanguage `function` as
  `SOLV-PARS-004` with expected `'func'`; `SolvikSemicolonTokenStreamTest` still pins `FUNC` as a
  non-terminator; malformed `func` inputs still produce no partial AST.
- Regression check: `grep` finds no `fun`-as-keyword reference in grammar, front end, tests, or
  examples.

## Validation

- `JAVA_HOME=/opt/graalvm ./mvnw -o -pl language test -Dtest='Solvik*Test'`
- `./build.sh`
- `./build-native.sh`
- launcher smoke test: `standalone/target/solvik language/tests/Hello.sol` and the native launcher
  print the expected output.
