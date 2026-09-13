# Java transpiler benchmarks

This directory holds the manual benchmark suite for the Java 17 transpiler. It
is deliberately **not** part of `./build.sh`: wall-clock numbers depend on the
machine and the JVM. Functional correctness tests live in the Maven Surefire
suites under `src/test/java/org/solvik/transpiler/`.

## Layout

```
solvik/           Solvik source programs, each with a Main.run entry point
programs/         Classic Solvik benchmark programs carried over from the old suite
java/             Hand-written idiomatic Java 17 equivalents for comparison
generate_large.sh Generates large synthetic sources for scaling checks
PhaseBench.java   Warmed, single-JVM per-phase compiler timing
run.sh            End-to-end runner
RESULTS.md        Recorded before/after numbers for the optimizations
```

The suite covers recursive and iterative fibonacci, tight arithmetic loops,
string concatenation, regex reuse, value/variant matches, object construction
and method calls, interface dispatch, list sorting, and collections.

## Running

Build the transpiler first:

```sh
./build.sh
```

Then:

```sh
benchmarks/run.sh                 # wall clock: transpile, javac, run, compare
benchmarks/run.sh --phases        # warmed per-phase compiler timing
benchmarks/run.sh --large         # compiler scaling on generated 500..4000 decl sources
RUNS=9 WARMUP=3 benchmarks/run.sh # longer, more stable measurement
```

`run.sh` reports the median of `RUNS` measured iterations after `WARMUP`
warm-up iterations. It measures transpiler time (a fresh JVM per call, so it
includes process start-up), `javac` time, generated-program time, and the same
for the hand-written Java equivalent. It also prints a JVM start-up baseline
(repetition of an empty `main`) so start-up can be subtracted from the
generated-program numbers.

`--phases` compiles and runs `PhaseBench`, which measures the lexer, parser,
semantic analyzer, and Java emitter inside one warmed JVM. Those numbers
exclude JVM start-up and are the right ones for comparing compiler phases.

`--large` generates valid sources with many declarations and reports scaling.
Compilation time should stay close to linear as the source grows. The `locals`
kind puts many locals plus one `if` per local into a single method; it is the
regression workload for the definite-assignment join cost in semantic analysis
and must stay linear.

## Methodology notes

- **Separate start-up from work.** Generated-program timings include a fresh
  JVM. The empty-program baseline makes the start-up share explicit.
- **Use medians, not single runs.** JIT compilation and GC make single
  measurements noisy.
- **Prevent dead-code elimination.** Every benchmark accumulates a result and
  prints it, so the JVM cannot discard the workload.
- **Compare against Java.** `java/` contains hand-written equivalents. Large,
  unexplained gaps point at a transpiler problem. The target is not identical
  performance: Solvik checked integer overflow, nullability checks, and
  synchronized collections are intentional and cost more than unchecked Java.
- **Do not gate CI on wall-clock thresholds.** Structural regressions are
  covered by `FrontendTests` instead (for example, that constants fold, that a
  value match does not allocate a `Supplier`, and that string/number
  concatenation does not route through `RT.cat`).

## Interpreting the results

Solvik is intentionally more defensive than Java in several places, so the
generated code is not expected to match hand-written Java exactly:

- integer arithmetic uses `Math.addExact`/`subtractExact`/`multiplyExact`
  (Solvik raises an overflow error);
- `RT.SList`/`SMap`/`SStack`/`SSet` synchronize mutations (Solvik collections
  are shared between threads);
- `Double`/`Float` formatting goes through `RT.format` to strip trailing
  zeros;
- nullable receivers are checked with `RT.require`.

HotSpot is expected to inline the small checked helpers and optimize ordinary
control flow. The transpiler does not attempt SSA, register allocation, manual
unrolling, or bytecode peephole optimization.
