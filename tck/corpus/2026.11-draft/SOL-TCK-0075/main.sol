// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "Each case body is an implicit block."
// and section 17, verbatim:
//   "`break` and `continue` are valid only inside a loop."
// Obligation: a `continue` inside a case body applies to the ENCLOSING loop rather than
// acting as a case terminator, because a case body is an implicit block and not a loop or
// function boundary (section 6 states the same principle for scope blocks). The loop runs
// v = 1,2,3; the v == 1 iteration continues past the print, so the exact stream is "d2d3".
// A reading in which `continue` terminated the switch would print "d1d2d3" or stop early.
mutable val v: Integer = 0

while (v < 3) {
    v = v + 1

    switch (v) {
        case 1:
            continue

        default:
            print("d" .. v)
    }
}
