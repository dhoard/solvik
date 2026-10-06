// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.3,
// verbatim: "Separation never changes meaning: no separator token carries a value, and
// the last item of a value-required block is its tail expression wherever it sits" -- and
// "Comments and blank lines before `}` do not affect tail selection". Section 16 defines
// the separator forms exercised here: a physical newline and an explicit `;`
// separating two same-line statements.
// Four spellings of the same value 42, so the expected stdout is "42424242". Any spelling
// that lost the tail expression would instead be a compile-time error.
var a = {
    42
}
var b = {
    var unused: Integer = 0; 42
}
var c = {
    42

}
var d = {
    42

    // a comment and blank lines must not disturb tail selection

}
print(a)
print(b)
print(c)
print(d)
