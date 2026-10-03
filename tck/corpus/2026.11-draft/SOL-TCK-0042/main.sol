// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "A reversed or empty range performs zero iterations rather than raising an error."
// Obligation: `5...1` is a reversed ascending range, so the body runs zero times. The
// observable is a counter that must remain at its initial value, proving the loop
// neither iterated nor raised.
mutable val n: Integer = 0

for (i in 5...1) {
    n = n + 1
}

print(n)
