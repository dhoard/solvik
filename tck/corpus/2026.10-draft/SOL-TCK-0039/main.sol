// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`...` ascends from the start and includes the end"
//   "The loop variable is an implicitly declared immutable `Integer` binding scoped to
//    the loop body. Both bounds are `Integer` expressions evaluated once before the
//    first iteration."
// Obligation: `1...5` yields exactly 1,2,3,4,5 (inclusive upper bound). Accumulating
// into a `String` with `..` (section 3: result is always `String`) makes the sequence
// visible as one byte-exact stdout value. `print` appends no separator, so the expected
// bytes are the concatenation alone.
var s: String = ""

for (i in 1...5) {
    s = s .. i
}

print(s)
