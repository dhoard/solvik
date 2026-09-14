# Conformance Testing

The conformance suite lives in `test/cases/` and runs under Maven Surefire via
`ConformanceTest`; it is part of `./mvnw test` and therefore of `./build.sh`.

## Layout

Each case is a directory containing:

- `main.sol` — the program under test.
- `expected.out` — expected program output (optional). For successful
  programs (`expected.code` absent or `0`) it is compared exactly as a
  whole-string match after trailing newlines are stripped. Runtime-error
  cases (`expected.code=2`) still pin the exit code but are not compared
  against `expected.out`, whose text describes the removed native VM's
  messages.
- `expected.code` — expected exit code (optional; default `0`). A successful
  program's status is the `Integer` returned by `Main.run`; source
  diagnostics exit `1` and generated runtime failures exit `2`.
- `args.txt` — program arguments, one per line (optional).
- `stdin.txt` — standard input for the generated program (optional).

## How a case is checked

For every fixture directory, in deterministic name order, `ConformanceTest`:

1. transpiles `main.sol` in-process with `Transpiler.toJava`, or asserts a
   `CompileException` when `expected.code=1`;
2. rejects any generated source that contains a `package` declaration;
3. compiles the generated Java with `javac --release 17 -Xlint:all -Werror`;
4. runs the program in a separate JVM with its `args.txt`/`stdin.txt` and
   checks the exit code (`0`, `1`, `2`, or the `Integer` value returned by
   `Main.run`) and, for successful programs, the exact `expected.out`;
5. cleans up after itself in JUnit temporary directories so no generated
   `.java` files are written into the source tree.

Generated programs run with the repository root as their working directory,
matching the historical runner; fixtures that use file-system paths resolve
them relative to the repository root.

## Running

```sh
./mvnw test                                   # unit + conformance suites
./mvnw test -Dtest=ConformanceTest            # conformance suite only
./mvnw test -Dtest='ConformanceTest#fixture'  # parameterized fixtures only
./build.sh                                    # full quality gate
```

Surefire HTML and text reports are written under `target/surefire-reports/`.

## Adding a case

1. Create `test/cases/NN-name/main.sol`.
2. Transpile and run it, then capture the output into `expected.out`.
3. For error cases, set `expected.code` to the expected exit code (`1` for a
   source diagnostic, `2` for a generated runtime failure). Successful
   programs may return a custom `Integer` exit status from `Main.run`.
4. Add `args.txt` or `stdin.txt` when the case reads them.
5. Re-run `./mvnw test -Dtest='ConformanceTest#fixture'` until green.

Cases must be deterministic: no wall-clock output, no randomness, no network
or filesystem side effects outside a temp directory that is cleaned up.

## Coverage

The numbered fixtures cover, among other areas:

- entry point (`Main.run` returns the `Integer` process exit status),
  integer/floating arithmetic and overflow checks, strings,
  regex, conversions, and numeric lattice behavior;
- lists, maps, stacks, sets, and their Java-shaped APIs, including
  synchronization and type safety;
- structs, traits, default methods, trait inheritance, generics,
  enums with payloads, `match`, and composition/delegation;
- nullability, coalescing, narrowing, and definite assignment;
- control flow, scope blocks, shadowing rules, `let` declarations, and
  `try`/`catch`/`finally`;
- static fields, static blocks, static initialization failure, visibility,
  and `Self`/type references;
- `System` process/runtime services, threads, mutexes/semaphores, file I/O,
  JSON, digests, and Base64;
- raw strings, string escapes, Unicode code-point operations, and list
  sorting (including the incomparable-element error);
- rejected programs that must exit `1` at the diagnostic phase;
- runtime faults such as overflow, negative indexing, conversion-range
  errors, and uncaught exceptions that must exit `2`.

`example.sol` is transpiled, compiled, and run as a top-level smoke test.
Additional tests pin the wrapper-name collision behavior and single-file
independence (compiling and running a generated file copied to a clean
directory).
