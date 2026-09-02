# Python-first semantic parity

`LANGUAGE.md` is the normative language contract and `solvik.py` is the
executable semantic reference. Go and Rust are optimized implementations.

The parity workflow is:

```text
LANGUAGE.md
    |
    v
solvik.py  (semantic oracle)
    |
    +--> test/conformance/*        (shared corpus: all implementations)
    +--> test/reference/*          (Python-reference semantics)
    |       +-- valid/             (compile-only fixtures)
    |       +-- invalid/           (expected diagnostic code, --check)
    |       +-- runtime_errors/    (expected E0xx code at runtime)
    |
    v
tools/parity.py
    |
    +--> Go observable behavior
    +--> Rust observable behavior
```

## Feature parity table

| Feature | Python | Go | Rust | Notes |
|---|---|---|---|---|
| Core language (pre-generics corpus) | reference | complete | complete | Shared corpus in `test/conformance` + differential set |
| Generic functions (inference) | reference | complete | complete | |
| Generic structs | reference | complete | complete | |
| Generic traits (user-declared) | reference | complete | complete | |
| Generic constraints (`T: A & B`) | reference | complete | complete | |
| Explicit type arguments (calls/literals) | reference | complete | complete | `f<int>(x)`, `Box<int> { ... }` |
| Nullable generic values | reference | complete | complete | Null never infers; declared-type hints + expected-type seeding |
| Generic arity diagnostics (C096/E067) | reference | complete | complete | Annotations, collections, explicit arguments, constraint arity |
| Recursive struct rejection (C097) | reference | complete | complete | Non-nullable field cycles |
| Struct literal field checking (C098) | reference | complete | complete | Against instantiated field types |
| Method type-param shadowing (C099) | reference | complete | complete | |
| Trait-argument inference from signatures | reference | complete | complete | `func total<T, C: Iterable<T>>(items: C)` |
| Core traits on built-ins | reference | complete | complete | Stringable/Equatable/Comparable/Hashable/Countable/Iterable/Collection |
| Structural trait satisfaction after substitution | reference | complete | complete | |
| Function types (`func<P..., R>`) | reference | complete | complete | Invariant; nullable; `void` return element |
| First-class functions (named refs, bound methods) | reference | complete | complete | Storable/passable/returnable values |
| Anonymous functions / closures | reference | complete | complete | Lexical capture; identity equality |
| Capture semantics (`mut` shared, immutable copy-like) | reference | complete | complete | Nested/returned closures; fresh per call |
| Higher-order generics | reference | complete | complete | `func mapOne<T, R>(value: T, transform: func<T, R>)` |
| Function-value diagnostics (C100/C101/C102, E068) | reference | complete | complete | Signature, arity, arg types, non-callable |
| Algebraic enums (payload cases) | reference | complete | complete | Positional payloads; generic enums |
| Enum construction (`Result.Ok(5)`, `Result<int,string>.Ok(5)`) | reference | complete | complete | Inference, explicit args, expected-type seeding |
| Pattern matching (bindings, wildcard, literals, nested) | reference | complete | complete | `case Result.Ok(value) { ... }` |
| Enum exhaustiveness (C105/C106/C107/C108) | reference | complete | complete | Missing/duplicate cases, arity, payload types |
| Payload equality / int() rejection / typeOf | reference | complete | complete | E066 for payload `int()` |
| Runtime type introspection (`typeOf`, `isType`) | reference | complete | complete | Top-level built-ins; canonical lowercase runtime tags |
| Static type checker hardening (C109-C119) | reference | complete | complete | Duplicate names, unknown types, returns, unreachable, break placement, null narrowing, mutability, invariance, decl/assign typing |
| Canonical package/type identity | reference | complete | complete | `package.Type<args>`; dotted identity; local-name display |
| Qualified types in annotations/literals/enums/patterns | reference | complete | complete | `http.Client { ... }`, `lib.Status.OK`, `case lib.Outcome.Good(v)` |
| Cross-package visibility (`pub` types/members, C120/E070) | reference | complete | complete | C121 builtin-name conflict |
| Closure-driven collection methods | reference | complete | complete | list map/filter/fold/reduce/find/any/all/first/last/reverse/sort |
| Text/format helpers | reference | complete | complete | string repeat/padStart/padEnd |
| Filesystem operations | reference | complete | complete | list/mkdir/isFile/isDir/size/rename/remove |
| Process args and capture | reference | complete | complete | `process.args()`, `process.capture` |
| JSON typed model | reference | complete | complete | `json.parse`/`json.stringify` |
| HTTP client | reference | complete | complete | `http.get/post/request` |
| Time formatting/parsing | reference | complete | complete | `time.iso`/`time.parse` |
| Testing facility | reference | complete | complete | `test` assertions (E071) |
| Standard-library errors (E072) | reference | complete | complete | reduce-empty, json, http, file |
| Semantic freeze (Phase 7) | reference | complete | complete | `SEMANTICS.md` 1.0-candidate contract; statement termination (P078), void (C122), main signature (C123/C124), conversion errors (E073) |
| Self-hosting preparation (Phase 11) | reference oracle | complete | complete | Solvik-native bootstrap lexer/parser/AST/basic type model; `test/bootstrap_frontend.sol` |


Statuses mean: `reference` = specified, implemented, and tested in the Python
semantic reference; `complete` = optimized implementation passes the full
reference and shared regression corpus; `pending` = not yet implemented at
that tier.

## Deliberately not claimed yet

These are still language-evolution work and must not be presented as complete
until the Python semantic reference and conformance fixtures define them:

- concurrency;
- full self-hosted compiler/bootstrap replacement (Phase 12+);
- standalone executable generation in the Rust toolchain.

Go and Rust both run the full reference differential suite against Python.
Rust's parity-complete executable uses native Rust semantic execution for the
complete frozen language surface, including closures, generics, collections,
structs, enums, exceptions, traits, package loading, static validation, and
standard namespaces. The Rust binary has no embedded Python execution path.
