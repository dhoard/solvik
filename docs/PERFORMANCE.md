# Solvik performance

This document describes performance work on the Java 17 Solvik transpiler. The
repeatable benchmark suite and recorded numbers live in
[`benchmarks/`](../benchmarks/); see
[`benchmarks/README.md`](../benchmarks/README.md) and
[`benchmarks/RESULTS.md`](../benchmarks/RESULTS.md).

## Architecture

```text
.sol source
  -> Lexer             lexical analysis
  -> Parser            tokens -> immutable AST
  -> SemanticAnalyzer  names, scopes, types, diagnostics
  -> SolvikProgram     typed declaration IR
  -> SolvikStmt        typed structured statement IR
  -> SolvikIr          backend-neutral typed expression IR
  -> IrOptimizer       exact constant folding / branch simplification
  -> JavaIr            Java representation decisions
  -> JavaEmitter       deterministic Java 17 source
  -> .java             compiled by javac 17 and run on HotSpot
```

The transpiler deliberately does not attempt SSA, register allocation, manual
method inlining, or loop unrolling. Those are HotSpot's job; doing them in
generated source tends to hurt javac, bytecode size, and inlining decisions.
The typed IR pass is intentionally small and the backend emits ordinary Java.

## Where the time goes

- **Transpilation** is dominated by JVM start-up for small programs. Use
  `benchmarks/run.sh --phases` to measure the lexer, parser, semantic
  analyzer, and emitter inside one warmed JVM, excluding process start-up.
- **`javac`** time for a generated program is dominated by the embedded
  runtime, which is emitted once per generated file.
- **Generated-program time** includes a fresh JVM start-up; the suite prints
  an empty-program baseline so start-up can be subtracted.

## Compiler hot-path allocation

Both phases that run per declaration resolve type references through a
short-circuiting loop (`lowerTypeRefs` in the emitter, `applyTypeRefs` /
`resolveWithArgumentRefs` in the analyzer) instead of
`args().stream().map(...).toList()`. Type-reference argument lists are small
and usually empty, so the stream, the capturing lambda, and the empty list
were pure per-reference overhead. The emitter's `typeOf` similarly scans the
owner/method type-parameter lists with a plain loop, and parameter-list and
constructor-signature rendering build their strings with a `StringBuilder`
instead of stream `reduce`. None of these change resolved types or emitted
text.

## Typed IR optimizations

`IrOptimizer` folds only what is exact for Solvik semantics:

- integer `+ - * / %` and unary negation when both operands are literals of
  the same primitive type and the result fits; overflow and division by zero
  are left for the runtime so the error is still raised, including
  `MIN_VALUE / -1` and `MIN_VALUE % -1`;
- constant integer comparisons (`== != < <= > >=`) using exact
  arbitrary-precision values, and `==`/`!=` between string/char literals;
- `!true`/`!!x`, `true && x`, `false && x`, `x && true`, and the `||`
  equivalents, respecting short-circuit evaluation;
- `literal ?? x` and `null ?? x`;
- constant `if`/`while` conditions.

Floating-point rewriting is deliberately omitted: javac already folds literal
Java arithmetic, and reassociating floats could change results.

## Generated-Java decisions

- **Checked arithmetic.** Integral `+ - * / %` use `RT.addInt`/`RT.addLong`/…
  which wrap `Math.*Exact`. This is a Solvik semantic requirement, not
  overhead to remove.
- **String concatenation.** When one operand is `String`/`Char` and the other
  formats identically to Java string conversion, the emitter writes `a + b`
  directly instead of routing through `RT.cat`/`RT.format`.
- **Regex patterns.** `Regex.new(...)` compiles through a bounded
  `ConcurrentHashMap` cache so a reused pattern is not recompiled.
- **Value matches.** A match used as a `return`, initializer, or simple
  assignment is lowered to an if/else chain instead of an allocating
  `Supplier` lambda.
- **Integer switches.** A `switch` over a non-nullable `Integer` subject with
  distinct in-range literal cases lowers to a Java `switch`; `Long` subjects
  stay if/else chains for `javac --release 17` compatibility.
- **String accessors.** `charAt`/`substring` use direct code-point operations,
  and string iteration is lazy, matching the language's Unicode-scalar
  indexing without materializing arrays.
- **List sorting.** `List.sort` uses the VM's natural ordering, raising the
  documented `list contains incomparable elements` error for mixed kinds.
- **Input reading.** `Reader.readln` reads in 8 KiB chunks; `Hash.*`
  hex-encodes through a lookup table.

## Intentional non-optimizations

- Synchronized `RT.SList`/`SMap`/`SStack`/`SSet` operations are required by
  Solvik's shared-collection semantics.
- Big-decimal cross-type numeric equality in `RT.eq` is required for exact
  comparison.
- Solvik `String` is immutable, so `s = s .. i` stays quadratic; the
  transpiler does not rewrite it to a `StringBuilder` because that changes
  allocation and aliasing behavior.
- No import pruning: the embedded runtime uses the emitter's fixed import set.

## Running the benchmarks

```sh
./build.sh                   # produce target/solvik.jar
benchmarks/run.sh            # end-to-end transpile / javac / run
benchmarks/run.sh --phases   # warmed per-phase compiler timing
benchmarks/run.sh --large    # compiler scaling on many declarations
```

`SOLVIK_JAVA_PHASES=1 ./transpile.sh in.sol Out` also prints one-shot phase
timings to stderr for ad-hoc use. Wall-clock results depend on the machine and
the JVM, so the suite is manual and is not part of `./build.sh`.
