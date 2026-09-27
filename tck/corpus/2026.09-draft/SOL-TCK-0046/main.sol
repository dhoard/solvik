// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "Both bounds are `Integer` expressions evaluated once before the first iteration."
// Obligation: the outer range `0..<n` captures `n` once, at 2, so it yields j = 0 and
// j = 1 -- exactly two passes -- even though the body assigns `n = n + 5` on every pass.
// The independent inner range `0..<3` always yields i = 0,1,2, so `sum` accumulates
// (0+1+2) + (0+1+2) = 6. The oracle "2:6" is derived from the two bounds rules alone.
// Discriminating value: re-evaluating the upper bound each iteration would give
// `6:15` (passes 0..5), which no reading of the sentence permits.
var passes: Integer = 0
var sum: Integer = 0
var n: Integer = 2

for (j in 0..<n) {
    passes = passes + 1

    for (i in 0..<3) {
        sum = sum + i
    }

    n = n + 5
}

print(passes .. ":" .. sum)
