// Positive numerics conformance test: integral division truncates toward zero.
// Oracle derived by hand from LANGUAGE_SPEC section 4 (repeated in section 3), verbatim:
// "Integral division truncates toward zero and division by zero raises a Solvik runtime
// arithmetic error." Truncation toward zero is sign-symmetric and differs from
// floor division on negative operands, which is why all four sign combinations are
// asserted: 7/2, -7/2, 7/-2, -7/-2 must yield 3, -3, -3, 3. (Floor division would give
// -4 for -7/2 and 7/-2, so the negative cases are what actually pin the rule.)
// Expected bytes: `3|-3|-3|3` -- `print` appends no separator (section 5).
print(7 / 2)
print("|")
print(-7 / 2)
print("|")
print(7 / -2)
print("|")
print(-7 / -2)
