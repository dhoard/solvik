// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "A `break` inside a case is illegal unless it exits a loop nested inside that case."
// Obligation: a `break` written inside a loop that is itself nested in a case body is
// LEGAL (it exits that loop) and must not be mistaken for the illegal bare `break`. The
// program prints once and terminates: a loop that ignored the break would not terminate,
// and a switch that rejected the legal form would not compile.
mutable val v: Integer = 2

switch (v) {
    case 1 {
        print("one")
    }

    case 2 {
        while (true) {
            print("inner")
            break
        }
    }

    default {
        print("other")
    }
}
