// Positive conformance test: `$` has no interpolation meaning.
// Oracle derived by hand from LANGUAGE_SPEC section 15, verbatim: "String interpolation
// is deferred. A `$` has no interpolation meaning in the initial implementation."
// Therefore a `$` in a normal string, including one in the `${...}` shape that
// interpolation would consume, is ordinary text and is emitted unchanged. Asserting
// this positive form (rather than rejecting interpolation) is exactly what the
// specification determines: it defers the feature and fixes `$` as literal text, which
// is observable. `print` appends no separator, so the expected bytes are the literal
// text of the string.
var s = "no $ interpolation and not ${value} either"
print(s)
