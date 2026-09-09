# Conformance Testing

The conformance suite lives in `test/cases/` and is run by `test/run.sh`.

## Layout

Each case is a directory containing:

- `main.sol` — the program under test.
- `expected.out` — expected combined stdout+stderr (optional; when absent
  or empty, only the exit code is checked).
- `expected.code` — expected process exit code (optional; default `0`).
- `args.txt` — program arguments, one per line (optional).

## Running

```sh
./test/run.sh                      # uses target/debug/solvik
./test/run.sh path/to/solvik       # explicit binary
```

The runner executes every case, compares exit code and output, prints
`ok`/`FAIL` per case, and exits non-zero on any failure.

## Cases

| Case | Coverage |
| ---- | -------- |
| 01-hello | entry point, stdout |
| 02-ints | integer literals, arithmetic, overflow checks |
| 03-floats | float arithmetic, conversions |
| 04-strings | string ops, concatenation with `..`, regex basics |
| 05-lists | list literals, element access via methods |
| 06-maps | map literals, put/get/keys iteration |
| 07-stacks | stack push/pop/peek |
| 08-classes | fields, constructors, inheritance, super, public fields |
| 09-interfaces | implements, default methods, dynamic dispatch |
| 10-generics | generic classes, type arguments, erasure |
| 11-enums | variants, payloads, match |
| 12-control | if/while/for-in/range/break/continue/match |
| 13-exceptions | throw/try/catch/finally/rethrow propagation |
| 14-nullability | nullable types, coalesce, narrowing |
| 15-threads | Runnable threads, join, mutex |
| 16-conversions | `<Type>.from(...)` conversions |
| 17-builtin-libs | math/base64/hash/json/time/type introspection |
| 18-compile-error | rejects a type error with exit code 1 |
| 19-runtime-error | rejects a runtime fault with exit code 2 |
| 20-aliasing | reference aliasing; separate objects not identity-equal (§32.1/32.2) |
| 21-named-defaults | named args, defaults, mixed, out-of-order (§32.10) |
| 22-multi-interface | class implements multiple interfaces, assignable through each (§32.5) |
| 23-self | base factory, inherited factory, fluent `Self` return (§32.8) |
| 24-parent-construction | subclass factory via `super: Parent.new(...)` (§32.9) |
| 25-visibility | private/protected/public methods; public fields accessible (§32.11) |
| 26-streams | stdout/stderr and `stderr.redirect(stdout)` (§32.12) |
| 27-compile-error-parent-field | child cannot initialize a private parent field (§32.4) |
| 28-static-dot | static methods use dot-qualified type syntax (§32.13) |
| 29-super-construction | subclass-owning-state via `super: Parent.new(...)` (§37) |

The regression cases `31`–`39` cover normal completion of `try`/`catch`,
exception handlers across call frames, overflow in all six integer arithmetic
operations, and forwarding CLI arguments that resemble compiler flags.

Cases `104`–`106` cover the renamed `Long`/`Double` types and
`Random.nextLong`/`Random.nextDouble`, and verify that the removed names are
rejected.

Regression cases `50`–`54` cover cyclic JSON rejection, radix literals in
match patterns, floating-point conversion bounds, and rejection of overflowing
integer and malformed floating-point literals. Unit tests additionally exercise
shared JSON references, map/object cycles, non-finite conversions, and valid
numeric boundaries.

## Adding a case

1. Create `test/cases/NN-name/main.sol`.
2. Run the binary against it and capture output into `expected.out`.
3. For error cases, set `expected.code` to the expected exit code.
4. Re-run `./test/run.sh` until green.

Cases must be deterministic: no wall-clock output, no randomness, no
network or filesystem side effects outside a temp directory that is cleaned
up.
