// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "A reversed or empty range performs zero iterations rather than raising an error."
// Obligation: `0..<0` is an empty ascending range, so the body runs zero times and the
// accumulator stays empty. The marker prefix makes "zero iterations" observable as the
// exact three bytes `empty:` rather than as an empty stream, which would be
// indistinguishable from a program that never started.
var mutable s: String = ""

for (i in 0..<0) {
    s = s .. i
}

print("empty:" .. s)
