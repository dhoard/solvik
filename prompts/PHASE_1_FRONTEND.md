# Phase 1 — Solvik Front-End Skeleton

Execute only when `docs/STATUS.md` marks Phase 1 as `NEXT`.

## Required Result

Add a Solvik lexer/parser that produces an immutable source AST with diagnostics and no dependency on executable Truffle nodes.

Use a Solvik-named grammar source and generated parser. All new front-end packages use the `org.solvik` namespace. The inherited SimpleLanguage parser may remain temporarily for baseline execution, but Solvik tests and classes must not call it.

## Supported Syntax

Phase 1 supports only:

- top-level `func` declarations with typed parameters and explicit return types;
- blocks;
- `val` and `var` local declarations;
- call-expression statements;
- integer, Boolean, and normal-string literals;
- name references, ordinary member access, calls, parentheses, and `+`, `-`, `*`, `/`;
- `if`/`else`;
- `return`.

Every statement must use an explicit `;` until Phase 2:

```solvik
func add(a: Int, b: Int): Int {
    return a + b;
}
```

Reject SimpleLanguage's `function` keyword in the Solvik parser.

## AST and Diagnostics

Provide only the node families required by the supported syntax: compilation unit, function, parameter, type reference, block, local declaration, expression statement, if, return, literal, name, member access, call, parenthesized expression, and binary expression.

Every node carries an immutable half-open source span `[startOffset, endOffset)` using zero-based character offsets into the Truffle `Source`. Line and column are derived for display, not stored as the authoritative location.

A diagnostic carries:

- stable code;
- severity;
- primary source span;
- message;
- optional expected and found values.

The parser returns an AST only when no error diagnostic exists. Invalid input must not produce a partial executable result.

## Out of Scope

- semicolon insertion and raw strings;
- symbol resolution and type checking;
- lowering or execution of Solvik syntax;
- classes, methods, nullability, inheritance, interfaces, delegation, generics, enums, match, switch, and Regex;
- Bytecode DSL changes;
- broad package, launcher, or artifact renaming.

## Validation

Add parser tests for every supported construct, exact tree shape, and source spans. Add negative tests for malformed declarations, missing explicit semicolons, and the `function` keyword.

Run focused parser tests and `./build.sh`. Update `docs/STATUS.md`, set Phase 2 as `NEXT`, and stop.
