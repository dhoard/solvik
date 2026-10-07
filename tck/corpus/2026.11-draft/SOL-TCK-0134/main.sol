// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.7,
// verbatim: "No numeric promotion or widening ... is introduced: a numeric widening is a
// coercion at a conversion site, never a join rule." The join of an `Integer` branch and a
// `Long` branch is `Number`, not `Long`.
// The join is Integer and Long, whose nearest common declared supertype per that sentence
// is Number. Binding the construct to a `Number` local is therefore legal and the taken
// branch's value is 1, so the expected stdout is exactly "1". The companion rejection test
// SOL-TCK-0135 is what proves the join is not `Integer` or `Long`.
var n: Number = if (true) {
    1
}
else {
    1L
}
var joined: Number = n
print(joined)
