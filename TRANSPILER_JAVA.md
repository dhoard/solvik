# Java 17 transpiler

The repository is a single-module Maven project. The transpiler lives under
`src/main/java/org/solvik/transpiler/`. It has a handwritten lexer and
recursive-descent parser,
an immutable AST, a symbol/type checker, a typed expression IR, Java name
mangling, and a deterministic source emitter. It is self-contained and does
not invoke a native compiler or VM.

## Compiler pipeline

```text
.sol source
  -> Lexer            lexical analysis only
  -> Parser           tokens -> immutable AST (Solvik syntax)
  -> SemanticAnalyzer name resolution, scopes, type checking, diagnostics
  -> SolvikProgram    typed declaration IR (structs/interfaces/enums/methods/fields)
  -> SolvikStmt       typed structured statement IR (blocks/if/loops/switch/try/match)
  -> SolvikIr         typed expression IR (resolved meaning, no Java spellings)
  -> IrOptimizer      constant folding / branch simplification over the typed IR
  -> JavaIr           Java expression IR (Java representation decisions)
  -> JavaEmitter      Java 17 source (formatting and Java spellings)
  -> .java            compiled by javac 17
```

Responsibilities:

- **AST** describes what the programmer wrote. It stays close to Solvik syntax
  and contains no Java concepts.
- **SemanticAnalyzer** is the sole place where Solvik meaning is decided: names,
  scopes, mutability, types, numeric promotion, overloads, interfaces, and
  diagnostics. Its results are attached to the analyzed program. Scope stacks
  are index-addressed maps (O(1) per level, no iterator allocation per name
  lookup), and definite assignment uses an undo log of assignment additions so
  control-flow joins cost only the assignments made inside the joined branches
  instead of copying every assigned set.
- **SolvikProgram** is the typed declaration IR: resolved struct/interface/enum
  names, field/parameter/variant types, resolved method signatures (including
  whether a slot must use the boxed/reference spelling), and lowered bodies and
  initializers.
- **SolvikStmt** is the typed structured statement IR. Control flow is kept
  structured (`If`, `While`, `ForRange`, `ForEach`, `Switch`, `Try`,
  `MatchStmt`) rather than lowered to jumps, so the backend can emit readable
  Java.
- **SolvikIr** is the backend-neutral typed expression IR. Every node carries
  its resolved Solvik type and describes Solvik meaning (`Self`, `Local`,
  `Field`, `Literal`, `Call`, `Binary`, `Coerce`, `Match`, ...), never a Java
  spelling.
- **IrOptimizer** is a small, conservative pass over the typed IR. It folds
  integer constants and constant comparisons, simplifies boolean/short-circuit
  expressions and literal `??`, and removes constant `if`/`while` branches. It
  runs after semantic analysis, so it cannot hide compile-time errors, and it
  never rewrites an expression that would overflow or divide by zero: those
  still reach the runtime helpers so the Solvik error is preserved. There is no
  data-flow, loop, or inlining optimizer.
- **JavaIr** is the Java expression IR produced by the Java lowering. It records
  Java representation decisions (checked helper calls, promoted comparisons,
  reference null checks, casts) and renders Java text deterministically via
  `JavaIr.render`.
- **JavaEmitter** renders Java text and resolves type references with
  allocation-free loops on the hot path (see "Compiler hot-path allocation"
  below). It performs deterministic formatting and Java representation:
  indentation, braces, Java identifiers, type spellings, escaping, and helper
  spellings. It renders from `SolvikProgram`/`SolvikStmt`/`SolvikIr` and does not
  walk the parser AST in its rendering path.

The AST-to-IR lowering currently lives in `JavaEmitter` (`lowerProgram`,
`lowerStmt`, `lower`, `lowerValue`, `lowerPattern`); it is the phase that reads
the analyzed AST, and it can be extracted to a dedicated `lowering/` class
without changing the IR or the backend.

Where Solvik and Java differ, Solvik semantics are lowered into equivalent Java
constructs (checked overflow, cross-type numeric equality, code-point string
handling, per-collection synchronization). Java is an implementation target,
not the language specification.

Build and use it with:

```sh
./build.sh
./transpile.sh example.sol ExampleProgram
javac --release 17 -Xlint:all ExampleProgram.java
java ExampleProgram
```

The equivalent low-level invocation is
`java -jar target/solvik.jar example.sol ExampleProgram`.

The required two-argument form writes exactly `ExampleProgram.java` in the
current directory. The generated file contains only the nested Solvik runtime
facilities the program actually reaches (for example the synchronized `List`,
`Map`, `Stack`, and `Set` implementations when those types are used), so it can
be copied to a clean directory and compiled without a runtime JAR.

Primitive numeric analysis uses Java 17 binary numeric promotion: byte and
short operands promote to `Integer`, then `Long`, `Float`, and `Double` take
precedence in that order. Integral generated arithmetic uses
`Math.addExact`/`subtractExact`/`multiplyExact`/`negateExact` (with narrow
helpers only for the `MIN_VALUE` division/remainder/absolute-value cases);
this preserves Solvik overflow behavior while using Java promotion types.

The generated wrapper invokes the static `Main.run(String...)` contract and
maps uncaught generated runtime failures to exit code 2. Source diagnostics
exit with 1; CLI/internal failures exit with 3.

The Maven build compiles the transpiler and its JUnit 5 tests with
`javac --release 17 -Xlint:all -Werror`. The `ConformanceTest` Surefire suite
then transpiles, warning-checks, and runs the repository's `.sol` fixtures,
including `example.sol`, plus wrapper-collision and clean-directory
single-file checks.

## Optimization boundaries

Optimization is split by responsibility:

```text
Solvik-specific work     source / semantic analysis / typed IR (IrOptimizer)
Java-specific lowering   SolvikIr -> JavaIr -> Java source (JavaEmitter)
low-level optimization   javac and HotSpot
```

The transpiler deliberately does **not** attempt SSA, register allocation,
manual method inlining, or loop unrolling. Those are HotSpot's job and doing
them in generated source tends to hurt javac, bytecode size, and inlining
decisions. The IR pass is intentionally small and the backend emits ordinary
Java that HotSpot already optimizes well.

### Compiler hot-path allocation

Both phases that run per declaration resolve type references through a
short-circuiting loop (`lowerTypeRefs` in the emitter, `applyTypeRefs`/
`resolveWithArgumentRefs` in the analyzer) instead of
`args().stream().map(...).toList()`. Type-reference argument lists are small
and usually empty (`Long`, `Self`, a concrete struct), so the stream, the
capturing lambda, and the empty list were pure per-reference overhead. The
emitter's `typeOf` similarly scans the owner/method type-parameter lists with a
plain loop, and parameter-list and constructor-signature rendering build their
strings with a `StringBuilder` instead of stream `reduce`. None of these change
resolved types or emitted text; they remove allocation from the lowering and
analysis inner loops.

### Typed IR optimizations

`IrOptimizer` folds only what is exact for Solvik semantics:

- integer `+ - * / %` and unary negation when both operands are literals of the
  same primitive type and the result fits (overflow and division by zero are
  left for the runtime so the error is still raised, including
  `MIN_VALUE / -1` and `MIN_VALUE % -1`);
- constant integer comparisons (`== != < <= > >=`) using exact arbitrary-
  precision values, and `==`/`!=` between string/char literals;
- `!true`/`!!x`, `true && x`, `false && x`, `x && true`, and the `||`
  equivalents, respecting short-circuit evaluation;
- `literal ?? x` and `null ?? x`;
- `if`/`while` conditions that are constant literals.

Floating-point rewriting is deliberately omitted: javac already folds literal
Java arithmetic, and reassociating floats could change results.

### Generated-Java decisions

- **Checked arithmetic.** Integral `+`, `-`, `*`, and unary `-` lower directly
to `Math.addExact`/`subtractExact`/`multiplyExact`/`negateExact`, which are the
exact Java 17 intrinsics for Solvik's checked overflow. Division and remainder
cannot use Java's `/` and `%` directly because those silently produce
`MIN_VALUE` for `MIN_VALUE / -1`, so they keep narrow
`RT.divInt`/`RT.remInt`/`RT.divLong`/`RT.remLong` helpers; `Math.abs` keeps
`RT.absInt`/`RT.absLong` for the same reason. Float and double arithmetic stays
native, and `BigInteger`/`BigDecimal` use their own methods.
- **String concatenation.** When one operand is a `String`/`Char` and the other
  is a type whose `RT.format` result is identical to Java's built-in string
  conversion, the emitter writes `a + b` directly. This avoids boxing and the
  `RT.cat`/`RT.format` dispatch for the common `"n=" .. count` case. `Float`,
  `Double`, and `Object` still go through `RT.cat` because `RT.format` strips
  trailing zeros.
- **Regex patterns.** `Regex.new(...)` compiles through a small bounded
  `RT.REGEXES` cache (`ConcurrentHashMap`, at most 1024 entries) so a pattern
  reused in a loop is not recompiled. `Pattern` is immutable and thread-safe,
  and the observable `RT.Regex` object identity is unchanged. The bound keeps a
  program that builds patterns dynamically from growing the cache without
  limit.
- **Value matches.** A match used as a `return`, variable initializer, or
  simple assignment is lowered to an if/else chain that assigns the result,
  instead of an allocating `Supplier` lambda. The subject is still evaluated
  exactly once. Matches nested inside larger expressions fall back to the
  lambda form.
- **Match subjects.** A match subject whose resolved static type is not
  `Object` is declared with that type (`__E_Color __match = v_d` instead of
  `Object __match = v_d`), so literal patterns lower to `==`/`.equals`, list
  patterns to direct `.size()`/`.get(...)`, and enum-variant patterns to
  `.tag() == n` without `instanceof` and cast round-trips. A trailing wildcard
  arm emits `else` rather than `else if (true)`, and redundant `&& true`
  fragments are dropped.
- **Constructors.** A struct `new` factory whose body is a pure permutation of
  its parameters (`new(a, b) { return Self { x: a, y: b } }`) lowers call sites
  straight to the generated all-fields constructor
  (`new __S_Point(12L, 5L)`); a factory that performs real initialization (for
  example calling another constructor) keeps the generated `__new` method.
- **Enum equality.** Comparing a payload-free enum variant (`c == Color.red`)
  lowers to a direct `.tag() == n` comparison; payload variants and nullable
  values still use `RT.eq` for structural equality.
- **Runtime feature reachability.** Lowering scans the rendered declarations
  and records which `RT` facilities they use, and `emitRuntime` emits only the
  base members plus the reachable feature blocks: collections, regex, process,
  thread/mutex/semaphore, JSON, hashing, file IO, dynamic dispatch, random,
  properties, environment, type queries, conversions, code-point/string
  access, ranges, checked division/remainder, time, and tests. A program that
  only prints an integer therefore contains no regex, process, thread, JSON,
  map/set/stack, crypto, file-system, or reflection runtime. Imports are
  emitted from the same feature set, so unused `java.util.regex`,
  `java.security`, `java.nio.file`, and concurrent packages are omitted.
- **Integer switches.** A `switch` over a non-nullable `Integer` subject whose
  case values are all distinct integer literals in the int range lowers to a
  real Java `switch` statement (tableswitch). Case bodies that do not diverge
  get an explicit `break`, preserving Solvik's no-fallthrough semantics. The
  lowering is refused when any case value is not a plain literal (evaluation
  order would change), when values duplicate (first-wins ordering would need
  the if/else chain), or when a case body contains a `break`/`continue` that
  binds to an enclosing loop (inside a Java switch it would bind to the
  switch). `Long` subjects stay if/else chains: recent javac builds parse
  constant-case switches over `long` as the preview "primitive patterns"
  feature, which would break `javac --release 17`.
- **String accessors.** `charAt`/`substring` use direct code-point operations
  (`codePointAt`, `offsetByCodePoints`) instead of materializing a full code-
  point array per call, and `for c in s` iterates lazily instead of building a
  list of all code points first. All three keep the VM's Unicode-scalar
  indexing and range-error messages.
- **List sorting.** `List.sort` compares with the VM's natural ordering:
  integral numbers exactly, floating numbers (and mixed integral/floating)
  as double with NaN comparing equal, `BigInteger`/`BigDecimal` exactly, and
  strings by text. Mixed kinds raise the same `list contains incomparable
  elements` runtime error as the VM instead of silently sorting formatted
  strings.
- **Input reading.** `Reader.readln` reads in 8 KiB chunks through an internal
  buffer instead of one `InputStream.read()` call per byte; `readAll` drains
  the same buffer first so mixed `readln`/`readAll` sequences see identical
  bytes.
- **Digests.** `Hash.*` hex-encodes through a lookup table instead of
  `String.format("%02x", ...)` per byte.
- **Direct dispatch.** Stays resolved by the analyzer, so calls are ordinary
  Java virtual/interface calls and field reads are ordinary field accesses;
  reflection (`RT.dynamic`) is only used for the dynamic `Object` case.

### Intentional non-optimizations

- Synchronized `RT.SList`/`SMap`/`SStack`/`SSet` operations are required by
  Solvik's shared-collection semantics.
- Big-decimal cross-type numeric equality in `RT.eq` is required to preserve
  exact comparison.
- Solvik `String` is immutable like Java's, so a loop such as
  `s = s .. i` stays quadratic; the transpiler does not silently rewrite it to
  a `StringBuilder` because that changes allocation and aliasing behavior.
- `System.getOut()`/`getErr()` stay as `RT.Writer` calls rather than direct
  `System.out`/`System.err` calls: Solvik's stream contract includes
  redirection, so the wrapper is observable behavior, not indirection to
  remove.

## Benchmarks

See [`benchmarks/README.md`](benchmarks/README.md) for the repeatable suite:

```sh
./build.sh
benchmarks/run.sh            # end-to-end transpile / javac / run
benchmarks/run.sh --phases   # warmed per-phase compiler timing
benchmarks/run.sh --large    # compiler scaling on many declarations
```

`SOLVIK_JAVA_PHASES=1 ./transpile.sh in.sol Out` also prints one-shot phase
timings to stderr for ad-hoc use.
