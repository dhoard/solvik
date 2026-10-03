// Oracle derived by hand from LANGUAGE_SPEC section 4, verbatim:
//   "`..` concatenates: both operands are rendered through toString and the result is
//    always String, so `1 .. "x"` is `"1x"` and `"x" .. null` is `"xnull"`."
// Obligation: string concatenation via `..`. print appends no line separator
// (section 5: only `println` appends the platform separator), so expected stdout is exact.
print(1 .. "x")
