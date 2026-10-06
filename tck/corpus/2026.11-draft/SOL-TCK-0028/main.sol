// Positive numerics/equality conformance test: IEEE 754 floating-point semantics.
// Oracle derived by hand from LANGUAGE_SPEC section 4 ("`Float` and `Double` follow
// IEEE 754 arithmetic") together with the section 3 equality rules, verbatim:
// "Floating equality preserves IEEE behavior: NaN is unequal to every value including
// itself, positive and negative zero are equal, and infinities compare by their values."
// Therefore: nan == nan is false; nan != nan is true (the negation, per section 3's
// "`!=` ... negate the corresponding positive operation without evaluating either
// operand again"); 0.0 == -0.0 is true; and -infinity < +infinity is true.
// The NaN values are produced by 0.0/0.0 and the infinities by +/-1.0/0.0, which IEEE
// defines; no floating literal is parsed, so no rendering format is relied upon.
// Expected bytes: `false|true|true|true`.
var nan: Double = 0.0 / 0.0
print(nan == nan)
print("|")
print(nan != nan)
print("|")
var zero: Double = 0.0
var negZero: Double = -0.0
print(zero == negZero)
print("|")
print(-1.0 / 0.0 < 1.0 / 0.0)
