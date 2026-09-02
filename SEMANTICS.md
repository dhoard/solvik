# Solvik Language Semantics — 1.0 Candidate

Version: 1.0-candidate (snapshot at Phase 11 of the completion program)
Normative sources: `LANGUAGE.md` (written specification), `solvik.py`
(executable semantic reference), conformance fixtures under `test/`.

This document is the completeness matrix and freeze record for the candidate
Solvik 1.0 contract. Every row states whether the area is **frozen** (stable
and covered by spec + reference + tests) and where its normative description
lives. Semantics change after this snapshot only through a documented phase
with a spec/reference/test update in the same change.

---

## Completeness matrix

| Area | Status | Normative reference |
|---|---|---|
| Lexical syntax | frozen | `LANGUAGE.md` "Lexical rules" |
| Types | frozen | `LANGUAGE.md` "Types" |
| Generics | frozen | `LANGUAGE.md` "Generic declarations and constraints" |
| Function types | frozen | `LANGUAGE.md` "Functions and closures" |
| Closures | frozen | `LANGUAGE.md` "Functions and closures" |
| Nullability | frozen | `LANGUAGE.md` "Types" |
| Runtime type introspection | frozen | `LANGUAGE.md` "Runtime type introspection" |
| Structs | frozen | `LANGUAGE.md` "Structs and methods" |
| Traits | frozen | `LANGUAGE.md` "Traits and enums" |
| Enums / ADTs | frozen | `LANGUAGE.md` "Algebraic enums and pattern matching" |
| Pattern matching | frozen | `LANGUAGE.md` "Algebraic enums and pattern matching" |
| Collections | frozen | `LANGUAGE.md` "Collections and iteration", "Standard library behavior" |
| Iteration | frozen | `LANGUAGE.md` "Collections and iteration" |
| Exceptions | frozen | `LANGUAGE.md` (exceptions section) + E-code reference below |
| Packages | frozen | `LANGUAGE.md` "Packages and type identity" |
| Visibility | frozen | `LANGUAGE.md` "Packages and type identity" |
| Standard library | frozen | `LANGUAGE.md` "Standard library behavior" |
| Diagnostics | frozen | this document, "Diagnostics" |
| Command-line behavior | frozen | this document, "Command-line behavior" |
| Static validation | frozen | `LANGUAGE.md` "Static validation" |

## Audit results (Phase 7)

Issues found during the audit and how they were resolved (spec/Python/tests
updated together in this phase):

- **Adjacent statements without a newline or semicolon were silently accepted**
  despite the specification claiming them invalid. The parser now enforces
  statement termination (P078) in blocks, at top level, and for struct
  members. Verified that multiline expression continuation is unaffected.
- **`void` was usable as an annotation type** (`v: void = ...`) and passed
  static validation. `void` is now rejected outside function-type return
  position (C122).
- **Conversion failures produced implementation-flavored errors** (raw Python
  messages) or no diagnostic code. Numeric/string conversion failures now
  raise a catchable `conversion failed` exception (E073) with a stable message.
  `bool` string conversion semantics are documented (`"true"`/`"false"`,
  case-insensitive).
- **`main` signatures were not validated statically.** The entry function must
  take no parameters (C123) and return `int` or nothing (C124); library files
  already reject `main`.
- **A missing source file crashed with a Python traceback.** The CLI now
  reports `error: cannot read source file: ...` and exits 1.
- **Spec contradiction on enums:** "enum values are 64-bit integer constants"
  applied only to integer-backed enums; the sentence now distinguishes
  integer-backed from payload (algebraic) values. `string()` rendering of
  payload cases (`CaseName(payload, ...)`) is now documented.
- **Undocumented behavior clarified in `LANGUAGE.md`:** newline continuation
  accepts operators at the start of the next line; `void` placement rule;
  conversion and `bool` semantics.
- Keyword set, statement termination, and all documented constructs were
  cross-checked against the lexer/parser; no other contradictions found.

## Intentional deviations / implementation choices (frozen as-is)

These are deliberate, documented choices; porting implementations must match
them rather than "fix" them:

- Null dereference (E031) and `any` downcasting (E066) are catchable runtime
  semantics, not static errors; null narrowing is the static discipline.
- Functions are cross-package accessible without `pub`; types and their
  members require `pub`.
- Generic struct/enum/function-type instantiations are invariant.
- Generic and variadic functions have no assignable function type; built-ins
  are not typed function values.
- `typeOf`/`isType`/`string()` display local (unqualified) names.
- `typeOf` and `isType` are top-level runtime-introspection built-ins; they do
  not participate in static method lookup or structural trait satisfaction.
- There is no `kindOf` built-in.
- `test` assertions raise E071; standard-library domain errors are E072.
- Newline continuation accepts binary operators at the start of the next line
  (documented; not treated as separate statements).
- `bool(string)` accepts only `"true"`/`"false"` (case-insensitive).
- `process.args()` returns the CLI arguments after the source file; `time`
  timestamps and durations are in milliseconds.

## Diagnostics

Stable diagnostic codes. Format: `error CODE: message` with a source span
for static errors; `uncaught exception [CODE]: message` for runtime errors.

### Lexical (L)

| Code | Meaning |
|---|---|
| L016 | Unknown escape sequence |
| L017 | Invalid hexadecimal digits in `\x`/`\u`/`\U` escape |

### Parse (P)

| Code | Meaning |
|---|---|
| P075 | Parenthesized map iteration bindings |
| P076 | Bare `func` type (missing return element) |
| P077 | Payload enum case with an integer value |
| P078 | Missing newline/semicolon/comma after statement or member |

### Compile (C)

| Code | Meaning |
|---|---|
| C017 | Invalid comparison operands (e.g. char vs numeric) |
| C028 | Left operand of `??` is not a value |
| C037 | Map value type mismatch |
| C068 | Mutating receiver / non-mutating method assignment |
| C082 | List element type mismatch |
| C090 | Duplicate function |
| C091 | Duplicate struct field or enum case |
| C092 | Duplicate parameter |
| C094 | Switch case type / pattern cannot match switch |
| C095 | Generic constraint not satisfied |
| C096 | Generic arity mismatch (annotations, arguments, constraints) |
| C097 | Non-nullable recursive struct field |
| C098 | Struct literal field type mismatch |
| C099 | Method type parameter shadows struct type parameter |
| C100 | Function signature mismatch on assignment |
| C101 | Call arity / argument type mismatch |
| C102 | Call to a non-callable value |
| C104 | `void` in a non-return function-type position |
| C105 | Non-exhaustive switch over a closed enum |
| C106 | Duplicate case coverage |
| C107 | Enum pattern shape error (arity, unknown case, invalid element, binding) |
| C108 | Enum payload type mismatch |
| C109 | Duplicate top-level name in a package |
| C110 | Unknown type |
| C111 | Non-void function missing a return path |
| C112 | Unreachable statement |
| C113 | `break`/`continue` outside a loop |
| C114 | Return value type mismatch |
| C115 | Return shape mismatch (value in void, or bare in non-void) |
| C116 | Assignment to an immutable binding |
| C117 | Assignment to an immutable struct field |
| C118 | Declaration initializer type mismatch |
| C119 | Assignment type mismatch |
| C120 | Cross-package access to a private type or member |
| C121 | Dependency package reuses a built-in namespace name |
| C122 | `void` used as a value type |
| C123 | Entry `main` takes parameters |
| C124 | Entry `main` does not return `int` or nothing |

### Runtime (E)

| Code | Meaning |
|---|---|
| E031 | Null reference (also division by zero, index/range errors) |
| E066 | Type mismatch (including `any` downcast failure) |
| E067 | Generic inference failure / constraint violation |
| E068 | Callable arity mismatch / loop-signal escape |
| E069 | Enum pattern / construction runtime error |
| E070 | Private member access (runtime defense) |
| E071 | Test assertion failure |
| E072 | Standard-library operation error |
| E073 | Conversion failed |

## Command-line behavior

`solvik.py [--check] [--version] FILE [ARGS...]`

- `FILE`: entry source file (must declare `package` and, for the entry, `main`).
- `ARGS...`: program arguments, available as `process.args()`.
- `--check`: parse and resolve dependencies without executing; prints
  diagnostics and exits 0 (clean) or 1 (diagnostics).
- `--version`: prints `solvik version <version>` and exits 0.
- Exit codes: `0` success (or `main`'s returned integer); `1` compile /
  load / I/O error; `2` uncaught runtime exception (printed to stderr as
  `uncaught exception [CODE]: message`).
- Dependencies: `use file:path` and `use url:value` (with optional
  `checksum:sha256:...` and `insecure:true|false`). Library files may not
  declare `main`.

## Freeze statement

As of this snapshot, the semantics above (specified, implemented in the Python
reference, and covered by conformance/reference fixtures) are the candidate
Solvik 1.0 contract. Go and Rust ports target this document; any future
semantic change must update `LANGUAGE.md`, `solvik.py`, tests, and this
document in the same change, and be recorded as a deviation from the
candidate.
