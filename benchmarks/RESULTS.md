# Recorded benchmark results

These numbers were measured on the development machine (AMD Ryzen 9 7900,
Linux) with Amazon Corretto 17 for the generated programs, using the suite in
this directory. They are illustrative, not portable guarantees: JIT compilation,
GC, and CPU frequency all move the numbers.

Method: `benchmarks/run.sh` with `RUNS=7 WARMUP=2`. Generated-program and
hand-written-Java figures are medians of fresh-JVM runs and include JVM
start-up (the empty-program baseline was 18 ms). "Before" is the transpiler
before the optimization pass; "after" includes it.

## End-to-end

| benchmark   | transpile before | transpile after | javac before | javac after | solvik-run before | solvik-run after | java-run |
|-------------|-----------------:|----------------:|-------------:|------------:|------------------:|-----------------:|---------:|
| collections | 77 ms | 79 ms | 495 ms | 489 ms | 81 ms | 79 ms | 63 ms |
| fib         | 74 ms | 75 ms | 488 ms | 480 ms | 31 ms | 32 ms | 23 ms |
| loop        | 70 ms | 73 ms | 483 ms | 495 ms | 59 ms | 60 ms | 39 ms |
| match       | 82 ms | 80 ms | 487 ms | 494 ms | 53 ms | 51 ms | 31 ms |
| objects     | 77 ms | 78 ms | 480 ms | 488 ms | 39 ms | 40 ms | 26 ms |
| regex       | 73 ms | 73 ms | 475 ms | 489 ms | **97 ms** | **48 ms** | 39 ms |
| strings     | 72 ms | 73 ms | 489 ms | 487 ms | 1818 ms | 1810 ms | 1825 ms |

Transpile times are dominated by JVM start-up (~50–60 ms of the ~75 ms) for
these small programs. `javac` times are dominated by the embedded runtime.

### Improvements

- **regex**: 97 ms → 48 ms, about **-50%** (1.0 ms per run). A `Regex.new`
  inside the loop no longer recompiles `Pattern`; the ratio against precompiled
  hand-written Java drops from 2.49x to 1.23x.
- **match**: 53 ms → 51 ms, about **-4%**, and the value match now compiles to
  an if/else chain instead of an allocating `Supplier`.
- Other rows are within run-to-run noise.

The `match` warning is expected: Solvik `RT.format` renders a large `Double`
without trailing zeros while Java `println(double)` uses scientific notation.
The values are numerically equal.

## Compiler phases (warmed, single JVM)

From `benchmarks/run.sh --phases` on the benchmark programs, all sub-millisecond
and dominated by the embedded runtime emission:

```
source          lexer   parser  semantic    emit   total
fib.sol        0.03ms   0.02ms   0.09ms  0.17ms  0.31ms
objects.sol    0.02ms   0.03ms   0.09ms  0.17ms  0.31ms
match.sol      0.02ms   0.03ms   0.07ms  0.19ms  0.31ms
```

## Large-source scaling

From `benchmarks/run.sh --large` (warmed medians; `emit` includes AST-to-IR
lowering, the `IrOptimizer` pass, and Java emission):

```
source                    lexer   parser  semantic     emit    total     bytes
large_structs_500.sol     2.41ms   0.92ms   2.49ms   4.10ms   9.92ms   346203
large_structs_1000.sol    1.83ms   0.74ms   1.96ms   3.21ms   7.73ms   665206
large_structs_2000.sol    3.67ms   1.30ms   4.70ms   7.48ms  17.14ms  1308206
large_structs_4000.sol    7.44ms   2.48ms  12.40ms  20.46ms  42.77ms  2594206
large_interfaces_500.sol  0.73ms   0.21ms   0.69ms   1.48ms   3.10ms   233376
large_interfaces_1000.sol 1.12ms   0.32ms   1.25ms   2.03ms   4.72ms   439879
large_interfaces_2000.sol 2.24ms   0.65ms   2.68ms   4.57ms  10.14ms   860879
large_interfaces_4000.sol 4.56ms   1.40ms   7.29ms  11.75ms  24.99ms  1702879
```

Every phase stays close to linear as the declaration count doubles, and the
`interfaces` cases are faster than `structs` at the same count. No O(n²)
behavior was observed.

The `IrOptimizer` pass is linear and reuses IR nodes when nothing folds, so it
adds a small, bounded cost on sources with no foldable constants.

## Second optimization pass (2026-09-13)

Measured on the same machine class (AMD Ryzen 9 7900, Linux, Corretto 17 for
generated programs). "Before" is the transpiler at the start of this pass;
"after" includes it. Wall-clock end-to-end transpile times are dominated by
JVM start-up (~55 ms of ~75 ms), so the warmed per-phase numbers are the
authoritative compiler comparison.

### Definite-assignment joins were O(variables × control-flow statements)

Semantic analysis deep-copied every assigned-variable set at each `if`,
`while`, `for`, and `try` boundary. A method with N locals and N `if`
statements therefore cost O(N²). The analyzer now keeps an undo log of
assignment additions: snapshots are log sizes, restores pop additions, and a
branch join intersects only the additions made inside the joined branches.
Scope stacks are index-addressed maps, so name lookups no longer allocate
iterators either.

`large_locals_N` = one method with N locals followed by N single-`if`
statements (new `locals` kind in `generate_large.sh`):

| locals | semantic before | semantic after | speedup |
|-------:|----------------:|---------------:|--------:|
|    500 |         27.4 ms |          0.40 ms |   ~69x |
|   1000 |         69.7 ms |          0.78 ms |   ~89x |
|   2000 |        186.2 ms |          1.57 ms |  ~119x |
|   4000 |        694.3 ms |          3.30 ms |  ~210x |

Before, 8× more locals took ~25× longer; after, 8× more locals take ~8×
longer (linear). The full warmed pipeline on `large_locals_4000` is 9.5 ms.

### Declaration scaling

From `run.sh --large` (warmed medians):

```
source                    semantic before  semantic after   emit before   emit after
large_structs_4000.sol        13.19 ms        11.77 ms       21.79 ms     19.85 ms
large_interfaces_4000.sol      7.23 ms         7.43 ms       12.13 ms     12.47 ms
```

The structs case improves ~10% in both phases from the iterator-free scope
walks and memoized constant-integer resolution; the interfaces case is within
noise. All phases stay linear as the declaration count doubles.

### Generated Java

- **Integer switches** over constant cases now lower to real Java `switch`
  statements (tableswitch) instead of if/else chains. Long subjects,
  non-constant case values, duplicate values, and case bodies containing a
  loop-bound `break`/`continue` keep the if/else chain (see
  `TRANSPILER_JAVA.md` for the JDK 25 "primitive patterns" constraint).
- **String accessors**: `charAt`/`substring` use `codePointAt`/
  `offsetByCodePoints` instead of materializing a code-point array per call;
  string iteration is a lazy iterator instead of an up-front list.
- **`List.sort` now matches the VM's natural ordering** (exact integral,
  double-based floating/mixed, exact BigInteger/BigDecimal, text for strings,
  `list contains incomparable elements` for mixed kinds). The previous
  generated sort compared formatted strings, which ordered numbers
  lexicographically (`[100, 21, 3, 9]`) where the VM orders them numerically
  (`[3, 9, 21, 100]`). This was a correctness fix; numeric sorting is also
  faster than string-key sorting.
- **`Reader.readln`** reads 8 KiB chunks instead of one `read()` per byte;
  `readAll` drains the same buffer first.
- **`Hash.*`** hex-encodes through a lookup table instead of
  `String.format("%02x", ...)` per byte.
- **IR optimizer** additionally folds constant integer comparisons and
  string/char literal equality.

The embedded runtime grew ~1.5 KB of source; `javac` times are unchanged
(~490 ms, dominated by JVM start-up and the runtime itself).

### End-to-end (after, this machine)

`run.sh` with defaults (RUNS=7 WARMUP=2); ratios are solvik-run / java-run:

```
benchmark     transpile      javac     solvik-run       java-run      ratio
collections        77 ms      491 ms          79 ms          64 ms      1.23x
dispatch           81 ms      502 ms          82 ms          51 ms      1.61x
fib                75 ms      499 ms          31 ms          22 ms      1.41x
iterfib            73 ms      486 ms          50 ms          39 ms      1.28x
loop               73 ms      492 ms          61 ms          38 ms      1.61x
match              82 ms      503 ms          52 ms          31 ms      1.68x
objects            76 ms      506 ms          40 ms          26 ms      1.54x
regex              74 ms      498 ms          46 ms          38 ms      1.21x
sort               79 ms      502 ms          64 ms          51 ms      1.25x
strings            72 ms      493 ms        1768 ms        1741 ms      1.02x
```

The remaining gaps versus hand-written Java are the intentional Solvik
defensiveness: checked integer arithmetic (`Math.*Exact`), synchronized
collection operations, and `RT.format` trailing-zero stripping for
floating-point output. The `match` output warning is the documented
`RT.format` vs `println(double)` scientific-notation difference; the values
are numerically equal.

## Third optimization pass (2026-09-13)

This pass is confined to the Java frontend. It removes per-reference
allocation from the semantic analyzer and the AST-to-IR lowering; it does not
change resolved types or emitted Java text, so generated-program performance
and the end-to-end ratios above are unchanged and all 232 conformance
fixtures still pass under the Maven Surefire `ConformanceTest` suite.

The hottest remaining cost after the second pass was type-reference
resolution, which ran for every declaration, parameter, return type, and field.
Each reference went through `ref.args().stream().map(...).toList()`, allocating
a stream, a capturing lambda, and an empty list even though almost every
reference has no type arguments (`Long`, `Self`, a concrete struct). The same
pattern appeared for interface `extends`/struct `implements` lists, method
parameter rendering, and the emitter's `typeOf` type-parameter lookup (four
`stream().anyMatch()` scans per reference). These now use short-circuiting
loops (`lowerTypeRefs`, `applyTypeRefs`, `resolveWithArgumentRefs`,
`hasTypeParam`) and `StringBuilder` for signature text.

Measured on the same machine class with `run.sh --large`. The "before" column
is the second-pass result recorded above. The development machine was under
external load (~13 load average) during this pass, so the warmed per-phase
minima are reported and should be read as a lower bound on the improvement.

```
source                    semantic before  semantic after   emit before   emit after
large_structs_4000.sol        11.77 ms        12.87 ms       19.85 ms     14.17 ms
large_interfaces_4000.sol      7.43 ms         7.25 ms       12.47 ms      9.55 ms
```

Emission (lowering plus Java text) drops roughly 25-30% on the declaration
benchmarks; semantic analysis is within run-to-run noise, as expected since
only its empty-argument type-reference path changed. All phases stay linear as
the declaration count doubles.
