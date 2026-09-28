// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`..>` descends from the start and excludes the end"
// Obligation: `5..>0` yields exactly 5,4,3,2,1 -- descending, end bound excluded.
var s: String = ""

for (i in 5..>0) {
    s = s .. i
}

print(s)
