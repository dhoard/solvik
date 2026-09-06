# internal/reference

The Go reference implementation: a faithful port of the Python semantic
reference (`solvik.py`). Added in Phase 8 (Go parity). The Python
implementation is the oracle; observable behavior (stdout, exit codes,
diagnostic codes and messages) must match.

Layout:

- `lexer.go` — newline-sensitive lexer.
- `ast.go`, `types.go` — AST and type machinery (canonical identity,
  substitution, trait satisfaction, canonicalization pass).
- `parser.go` — recursive-descent parser incl. backtracking explicit type
  arguments.
- `validator*.go` — the static checker (C-codes).
- `interp.go`, `expr.go`, `calls.go` — the tree-walking interpreter.
- `values.go`, `builtins.go`, `stdlib_io.go` — values, methods, namespaces.
- `loader.go`, `errors.go`, `util.go` — loading, diagnostics, helpers.

`cmd/solvik` is wired to this package. The legacy bytecode compiler/VM
pipeline (internal/{checker,compiler,vm,...}) remains for its own tests;
the shipped binary uses this reference port.
