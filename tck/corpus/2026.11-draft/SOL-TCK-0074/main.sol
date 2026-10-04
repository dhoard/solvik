// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "A `break` inside a case is illegal unless it exits a loop nested inside that case."
//   "`break` and `continue` are valid only inside a loop." (section 17)
// Obligation: a bare `break` in a case body, with no enclosing loop, is rejected. The
// two quoted sentences combine to fix this without ambiguity. The specification names no
// stable code for it, so only the semantic diagnostic family is asserted. This is the
// negative twin of the legal nested-loop case above.
mutable val v: Integer = 2

switch (v) {
    case 1 {
        break
    }

    default {
        print("other")
    }
}
