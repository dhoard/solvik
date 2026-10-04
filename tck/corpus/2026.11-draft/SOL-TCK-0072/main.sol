// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "Initial Solvik does not provide a `fallthrough` keyword. Shared cases are expressed
//    directly, for example:" with `case 1, 2:` printing "one or two"
// Obligation: a comma-separated shared case list matches any of its values, and this is
// the specified replacement for fallthrough. The expected text is the shared body's own
// literal, taken from the specification's example rather than observed.
mutable val v: Integer = 2

switch (v) {
    case 1, 2 {
        print("one or two")
    }

    default {
        print("other")
    }
}
