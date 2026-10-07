// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "An `if` may be used in expression position", illustrated by the chained
// `if (value < 0) { "negative" } else if (value == 0) { "zero" } else { "positive" }`.
// Each arm's string is fixed by that example, so driving the construct with one value per
// arm fixes the whole output. Values are bracketed so the three arms are distinguishable
// in a single stream regardless of print's separator behavior (section 5 defines print to
// append nothing). Expected stdout is "[negative][zero][positive]".
var low: Integer = -3
var zero: Integer = 0
var high: Integer = 9
var a: String = if (low < 0) {
    "negative"
}
else if (low == 0) {
    "zero"
}
else {
    "positive"
}
var b: String = if (zero < 0) {
    "negative"
}
else if (zero == 0) {
    "zero"
}
else {
    "positive"
}
var c: String = if (high < 0) {
    "negative"
}
else if (high == 0) {
    "zero"
}
else {
    "positive"
}
print("[")
print(a)
print("]")
print("[")
print(b)
print("]")
print("[")
print(c)
print("]")
