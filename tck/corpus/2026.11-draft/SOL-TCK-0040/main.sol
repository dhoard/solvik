// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`..<` ascends from the start and excludes the end"
// Obligation: `0..<5` yields exactly 0,1,2,3,4 -- the end bound is excluded. The
// distinguishing value is the leading 0 and the absence of a trailing 5.
var mutable s: String = ""

for (i in 0..<5) {
    s = s .. i
}

print(s)
