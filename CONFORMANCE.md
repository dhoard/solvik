# Conformance Testing

The conformance suite lives in `test/cases/` and is run by `test/run.sh`.

## Layout

Each case is a directory containing:

- `main.sol` — the program under test.
- `expected.out` — expected combined stdout+stderr (optional; when absent
  or empty, only the exit code is checked). Output comparison is an exact
  whole-string match, so warning-bearing cases must include the full
  rendered warning line (including its `file:line:col:` prefix). Warnings
  never change the exit code, so a warning-only case keeps `expected.code`
  at 0.
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
| 08-classes | private fields, methods, interface default dispatch |
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
| 23-self | factory and fluent `Self` return (§32.8) |
| 24-composition | interface delegation to a composed field |
| 25-visibility | private fields and methods; method-based external access (§32.11) |
| 26-streams | stdout/stderr and `stderr.redirect(stdout)` (§32.12) |
| 27-compile-error-private-field | external read of a private field is rejected (§32.4) |
| 28-static-dot | static methods use dot-qualified type syntax (§32.13) |
| 29-composed-login | composition with delegation and multiple interfaces (§37) |

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

Cases `116`–`118` cover the mandatory `let` keyword: bare declarations are a
parse error, `let` is reserved (not usable as an identifier), and
`mutable let` is rejected. Cases `119`–`124` cover Rust-style shadowing:
same-block shadow, type-changing shadow, nested restore, parameter shadow,
mutable/immutable interplay, and null-narrowing invalidation across a shadow.
Cases `125`–`128` cover full block scoping: loop-local scope, switch-case
scope, catch-parameter scope, and for-in shadow/restore. Shadowing emits
warning `W101` (never an error); block-scoped leaks are compile errors.

## Adding a case

1. Create `test/cases/NN-name/main.sol`.
2. Run the binary against it and capture output into `expected.out`.
3. For error cases, set `expected.code` to the expected exit code.
4. Re-run `./test/run.sh` until green.

Cases must be deterministic: no wall-clock output, no randomness, no
network or filesystem side effects outside a temp directory that is cleaned
up.

Cases `130`–`136` cover explicit interface delegation: a single delegate
through concrete and interface receivers, multiple delegates to different
fields, delegation of interface defaults, explicit methods beating delegated
methods and defaults, generic delegated interfaces, delegation through an
`Object` dynamic receiver, exception propagation through a forwarding method,
and interface inheritance combined with delegation. Case `148` stresses
delegated objects across many garbage-collection cycles, proving the delegate
field is traced like any other instance field.

Cases `137`–`147` cover rejected programs: nullable delegate targets, delegate
targets whose type does not conform, delegating an interface absent from the
class's `implements` closure, conflicting delegates, class `extends`,
`override`, `protected`, `public` fields, delegating a non-interface type,
explicit `private` field declarations, and `super` member access.

Cases `149`–`153` cover interface-hierarchy and generic-conformance soundness:
the most-specific default method wins through a diamond (`149`), a generic
class conforms to its interface binding after substituting its type arguments
(`151`), a non-generic interface refinement is assignable to the generic
interface it refines (`152`), and rejected programs include assigning a class
to an interface instantiation it does not conform to (`150`) and delegating a
generic interface to a field whose type conforms only to a different
instantiation (`153`). Cases `154` and `155` pin generic instantiation of
delegated methods and interface defaults: the effective implementation carries
the substituted parameter and result types for both concrete and interface
receivers. Case `156` rejects delegating to an `Object`-typed field (which
cannot guarantee conformance), and case `157` accepts a type-variable
delegate target whose nominal constraint already conforms to the delegated
interface.

Case numbers `130`–`157` are used by the composition/delegation work (see
above).

Cases `158`–`164` pin diagnostic and dispatch edge cases: literal
expressions carry source spans so diagnostics point at the actual token
(`158`), dynamic `Object` dispatch on a built-in value (String, List, ...)
produces a deterministic `no method` error naming the dynamic type (`159`),
competing default methods from unrelated interfaces are rejected unless an
explicit class method resolves them (`160`), and generic method return types
are instantiated with call-site-inferred type arguments, including through
delegation (`161`). Generic interface default methods infer their own type
parameters at the call site (`162`), method type-parameter constraints are
checked against the inferred argument types even inside generic classes
(`163`), and argument inference never rebinds the receiver's class type
arguments (`164`). The next free case number is `165`.
