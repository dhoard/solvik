# Numeric Widening (Precision-Preserving) — Implementation Plan

**Purpose:** Amend the language to permit *implicit numeric widening only where no precision or
range is lost*, then implement it end-to-end (lexer unchanged → parser unchanged → semantic
analysis → lowering → runtime reuse) with positive and negative tests and full validation.

**Authority hierarchy:** `AGENTS.md` > `docs/LANGUAGE_SPEC.md` (normative) > `docs/ARCHITECTURE.md`
> this plan. This plan first amends `docs/LANGUAGE_SPEC.md` (the spec currently forbids *all*
implicit numeric conversion), then implements to the amended spec.

**Quality gate:** `./build-all.sh` (JVM + native image, corpus OK with both launchers). No change
is complete until it passes.

---

## 1. Current state (observed, grounded in code)

- `docs/LANGUAGE_SPEC.md` §4 (line ~368): *"No implicit numeric widening or narrowing is
  permitted."* and *"Arithmetic operands must have the same numeric type and produce that type."*
- `docs/LANGUAGE_SPEC.md` §21.7 (line ~1443): *"No numeric promotion ... is introduced."*
- `type/NumericTypes.java`: `isNumeric` / `isIntegral` / `isFloating` only; no ordering concept.
- `type/Type.java` `isAssignableTo`/`isSubtypeOf`: purely nominal. Numeric types are siblings under
  `Number`, so no numeric type is assignable to another.
- `semantic/SolvikSemanticAnalyzer.java`
  - `checkBinary` (≈2787): arithmetic + ordering require `left == right`, else `TYPE_INVALID_OPERANDS`.
  - Equality `==`/`!=` (≈2799): require `left.isAssignableTo(right) || right.isAssignableTo(left)`.
  - Coercion/assignment sites: local init (1865), property init (1598/1622), return (2184),
    assignment (2353), property assign (2452/3399), arguments (`checkArgumentTypesAgainst` 3960),
    collection elements (`checkCollectionArgument` 4100): each does `!valueType.isAssignableTo(target)`
    → `TYPE_MISMATCH`.
  - `commonType` (2875) / `TypeJoin` used for `if`/`match`/block **result** typing.
  - Explicit conversion `checkConversion` (3050) + `SolvikConvertNode` runtime node.
- `lowering/SolvikLowering.java`
  - `lowerBinary` (1078): reads `program.typeOf(left)` to pick Integer fast path vs generic numeric node.
  - `lowerLocalDecl`/`lowerAssign`/`lowerReturn`/`lowerArguments`/`lowerConversion`: build nodes;
    conversion already builds `SolvikConvertNode` for `T(value)`.
- `truffle/nodes/SolvikConvertNode.java`: `executeGeneric` returns the target's boxed primitive
  (`Byte`/`Short`/`Integer`/`Long`/`Float`/`Double`). Integral targets range-check (never fires for a
  *widening* source). Reusable for implicit widening with zero runtime changes.
- Frame representation (`kindOf`, `SolvikWriteLocalVariableNode`, `SolvikReadLocalVariableNode`,
  `SolvikRootNode.copyArguments`): `Integer/Long/Float/Double` use typed slots and unbox via typed
  `execute*`; `Byte/Short` are boxed `Object`. A `SolvikConvertNode` producing a boxed widened value
  is representation-correct at every consumer (identical to the proven explicit-conversion path).
- Tests that encode today's behavior and MUST change:
  - `SolvikNumericNegativeTest.implicitWideningIsRejected` (`val x: Long = 1`),
    `implicitFloatingNarrowingIsRejected` is *narrowing* (`Double = 1.5f`) → stays rejected.
  - `mixedNumericArithmeticIsRejected` (`1 + 1L`) → now valid (must move to positive).
  - `mixedNumericEqualityIsRejected` (`1 == 1L`) → now valid (must move to positive).
- Golden corpus `gen101150.sol:31`: `if (flag) { 43 } else { 11L }` typed as `Number`; MUST stay `Number`.

## 2. Semantic rule (normative, to be written into the spec)

**Value-preserving widening is permitted implicitly; narrowing and precision-losing conversions are not.**

`W(a → b)` holds iff a value of type `a` maps to `b` with no loss of integral range and no loss of
representable precision:

- Integral → integral: `W` iff target range ⊇ source range. Chain
  `Byte ≺ Short ≺ Integer ≺ Long`.
- Integral → floating: `W` iff every value of the integral type is exactly representable in the
  floating type. `Byte`, `Short` (≤16 bits) and `Integer` (32 bits ≤ 53-bit mantissa) widen to
  `Double`; **`Integer → Float` is NOT** (24-bit mantissa); **any `Long → Float/Double` is NOT**
  (64 bits > 53-bit mantissa).
- `Float → Double` is `W` (24 ≤ 53 mantissa, exponent range grows).
- Same type is trivially compatible (not a widening).
- No other pair widens (never `Long → Float`, never `Float → Long`, never narrowing, never
  `Float → Integer`, `Double → Float`, etc.).

**Least common widened numeric type** `lcp(a, b)` for two numeric types = the unique minimal type
`t` with `W(a→t) ∧ W(b→t)`, if it exists; else none (e.g. `Long` and `Float` have no common widened
type; `Integer` and `Float` have none; `Long` and `Integer` → `Long`).

**Where widening applies (a compiler-inserted coercion, NOT a subtype/assignability change):**
1. Every *coercion* site where an expression must match a declared target type: local/`val`/`var`
   initializer, function/method argument, `return`, property initializer, property/static assignment,
   collection element / map key / map value.
2. Binary **arithmetic** (`+ - * /`) and **ordering** (`< <= > >=`): operands widen to `lcp`; result
   is that type (arithmetic) / `Boolean` (ordering). If no `lcp`, existing `TYPE_INVALID_OPERANDS`.
3. **Equality** (`==`/`!=`): permitted when the two numeric operands have an `lcp`; both widen to it
   and compare. Identity (`===`/`!==`) is unchanged (numerics are never identity-bearing).

**Where widening does NOT apply:** the type-join used for `if`/`match`/block **results** stays
"nearest common *declared* supertype" (`Integer ⋁ Long = Number`). Numeric widening is a coercion,
not an assignability edge, so nominal `isAssignableTo`/`isSubtypeOf`, hashing, `is`, casts,
generics, and joins are all unchanged.

**No overflow/precision surprises:** widening never overflows or loses precision by construction, so
no new runtime error can be introduced by an implicit coercion.

## 3. Implementation (bottom-up, each step buildable)

### Step A — type layer: the predicate (no behavior change yet)
- `type/NumericTypes.java`: add
  - `Optional<Integer> rank(Type)` — width rank for ordering integral chain / float→double, or model
    directly.
  - `boolean widens(Type from, Type to)` — the `W` relation above (single source of truth).
  - `Optional<Type> leastCommonNumeric(Type a, Type b)` — `lcp`.
- These are pure functions over the six singletons; no analyzer use yet.

### Step B — analyzer: record coercions instead of rejecting
- Add `Map<ExpressionNode, Type> coercions` (source expression → widened target type) to the analyzer
  and thread it into `CheckedProgram` + `typeOf`-style accessor `coercionOf(ExpressionNode)` and a
  binary operand-type accessor `binaryOperandTypeOf(ExpressionNode)`.
- New private helpers:
  - `boolean assignableOrWidens(Type value, Type target)` = `value.isAssignableTo(target) || NumericTypes.widens(value, target)`.
  - `void recordCoercionIfWidening(ExpressionNode expr, Type valueType, Type target)` — when not
    assignable but `widens`, `coercions.put(expr, target)`.
- Replace each `!valueType.isAssignableTo(target)` rejection with: if `assignableOrWidens` and not
  assignable → `recordCoercion(...)`; else error as before. Applies to all sites listed in §1.
  (Generics must be checked first: only widen when target/value are concrete numeric singletons;
  a type-parameter target is unaffected.)
- `checkBinary` ARITHMETIC/COMPARISON: if `left==right` numeric → as today; else if both numeric and
  `lcp` present → record `coercions.put(left|right, lcp)` when they differ, record
  `binaryOperandTypes.put(expr, lcp)`, return `lcp`/`Boolean`; else existing error.
- `checkBinary` EQUALITY (`==`/`!=`): existing assignability branch stays; add: else if both numeric
  and `lcp` present → record operand coercions + `binaryOperandTypes.put(expr, lcp)`, return `Boolean`.
- Keep `checkUnary` (NEGATE) requiring a single numeric type (no mixed widening in unary).

### Step C — lowering: wrap coercions with the existing convert node
- In `lowerLocalDecl`, `lowerAssign` (both local and property/static value), `lowerReturn`,
  `lowerArguments`/`lowerCollectionArguments`, property/static initializer lowering: after lowering
  an operand, if `program.coercionOf(origExpr)` present, wrap in
  `new SolvikConvertNode(convertTarget(target), loweredOperand)`. (Reuse existing `convertTarget`.)
- In `lowerBinary`: choose the numeric path by the **widened operand type**:
  `operandType = program.binaryOperandTypeOf(expr).orElseGet(() -> program.typeOf(left))`; wrap each
  operand via its recorded `coercionOf`. Integer fast path retained iff operand type is `Integer`.
- No new runtime nodes; no changes to `SolvikConvertNode`.

### Step D — docs
- Amend `docs/LANGUAGE_SPEC.md` §4 and §21.7 wording to the §2 rule (replace "No implicit numeric
  widening" with "implicit widening only where no range or precision is lost"; restate operand rule;
  keep explicit `T(value)` for narrowing/other conversions).
- Update `docs/ARCHITECTURE.md` if it states a numeric-coercion boundary (verify).
- Update `docs/SEMANTIC-TEST-COVERAGE.md` / `LOWERING-TEST-COVERAGE.md` rows for the new behavior.

## 4. Tests (positive AND negative) — every rule covered

### Positive (new `SolvikNumericWideningTest`, plus runtime)
1. `val x: Long = 1` (Integer→Long); assert typed `Long`, run → prints `1`.
2. `val s: Short = b` Byte→Short; `val i: Integer = s` Short→Integer; `val i2: Integer = b` Byte→Integer.
3. `val i: Integer = 5` then `val l: Long = i`; chained `Byte→Long`.
4. Integral→Double only: `val d: Double = 1` (Integer), `val d2: Double = 1L`? NO — that is
   Long→Double (precision loss) and is a NEGATIVE case; positive is `Byte/Short/Integer → Double`.
5. `Float → Double`: `val d: Double = 1.5f` → `1.5`.
6. Arithmetic widening: `1 + 1L` → `Long`; `1.0f + 2.0` → `Double`? Float+Double lcp=Double → `3.0`.
   `2 (Integer) + 3.0 (Double)` → Double.
7. Ordering widening: `val b = 1 < 2L` → `true`; `1.5f < 2.0` → true.
8. Equality widening: `1 == 1L` → true; `1.5f == 1.5` → true; `1 != 2L` → true.
9. Argument widening: `func g(x: Long) ...; g(1)` runs; `g(1.5f)` if Double param.
10. Return widening: `func f(): Double { return 1 }` → `1.0`.
11. Collection widening: `val xs: List<Long> = List<Long>(1, 2)` elements widen.
12. Property widening: class field `val n: Long` initialized from `Integer`; assignment widens.
13. Widening node is inserted: assert `program.coercionOf(initializerExpr)` present at the analyzer level.

### Negative (extend `SolvikNumericNegativeTest`) — precision loss / narrowing still rejected
1. **`val x: Float = 1` (Integer→Float) → `TYPE_MISMATCH`** (mantissa loss).  [core]
2. **`val x: Double = 1L` (Long→Double) → `TYPE_MISMATCH`** (53-bit mantissa).  [core]
3. **`val x: Float = 1L` (Long→Float) → `TYPE_MISMATCH`.**
4. Narrowing still rejected: `val x: Integer = 1L`, `val x: Double = 1.5f` (Float→Double is widening, so this
   is `val x: Float = 1.5` narrowing → reject). Keep the existing `implicitNarrowingIsRejected`.
5. Float→integral rejected: `val x: Long = 1.5f` → `TYPE_MISMATCH`.
6. No common widened type in arithmetic: `1L + 1.5f` (Long, Float → none) → `TYPE_INVALID_OPERANDS`.
   `1 + 1.5f`? Integer+Float: is there lcp? Integer→Float is NOT widening; Integer→Double, Float→Double,
   so lcp=Integer,Float = Double → valid → `2.5f` as Double. (This is a POSITIVE; confirm.)
   The true negative is `1L < 1.5f` (Long vs Float, no lcp) → rejected.
7. No-common equality: `1L == 1.5f` (Long vs Float) → `TYPE_INVALID_OPERANDS`.
8. Identity `1 === 1L` still `TYPE_IDENTITY_OPERANDS` (numerics not identity-bearing) — unchanged.
9. Type-parameter target unaffected: `func id<T>(x: T): T`; `id<Long>(1)`? T=Long, arg Integer → not
   numeric-singleton target (T) → existing rule (mismatch) — verify no accidental widening into a
   type parameter. Keep as-is.
10. Existing explicit conversion out-of-range tests unchanged.
11. `mixedNumericEqualityIsRejected` REMOVED/replaced by positive equality widening.

### Golden corpus
- `gen101150.sol` unchanged (`Number` join preserved). Verify its `.output` still matches.
- Add a new regression program `language/tests/regression/NN-widening.sol` + `.output` exercising a
  representative widening program through the shipped launcher.

## 5. Validation sequence
1. `JAVA_HOME=/opt/graalvm ./mvnw -q -pl language test` (focused: `SolvikNumeric*`, `SolvikTypeJoinTest`,
   `SolvikConversionRuntimeTest`, `SolvikEqualityTest`).
2. Full `./build.sh` (JVM + corpus), then `./build-native.sh` (native + corpus), i.e. `./build-all.sh`.
3. Review `git diff`; confirm no `Number`-join regression, no new runtime error paths, spec/docs aligned.

## 6. Non-goals / risks
- Do NOT add widening to the type join, `isAssignableTo`, hashing, `is`/casts, or generics inference.
- Do NOT add a new runtime node (reuse `SolvikConvertNode`).
- Risk: equality operand lowering path after widening → covered by explicit tests + `binaryOperandTypes`.
- Risk: a coercion recorded on an expression that is lowered more than once → each expression node
  has exactly one AST parent, so one coercion target per node; `IdentityHashMap` keying is safe.
