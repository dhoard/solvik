// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "An `if` may be used in expression position", illustrated by the chained
// `if (value < 0) { "negative" } else if (value == 0) { "zero" } else { "positive" }`.
// Each arm's string is fixed by that example, so driving the construct with one value per
// arm fixes the whole output. Values are bracketed so the three arms are distinguishable
// in a single stream regardless of print's separator behavior (section 5 defines print to
// append nothing). Expected stdout is "[negative][zero][positive]".
val low = -3
val zero = 0
val high = 9
val a = if (low < 0) {
    "negative"
}
else if (low == 0) {
    "zero"
}
else {
    "positive"
}
val b = if (zero < 0) {
    "negative"
}
else if (zero == 0) {
    "zero"
}
else {
    "positive"
}
val c = if (high < 0) {
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
