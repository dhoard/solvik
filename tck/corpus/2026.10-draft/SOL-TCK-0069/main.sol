// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "Cases never implicitly fall through."
//   "No `break` is required to terminate a case."
// Obligation: matching `case 2` must execute its own body and NOT the following
// `default`, while still requiring no `break` to stop. Both halves of the sentence are
// observable from the single value "two": implicit fallthrough would yield "twoother"
// and a mandated-break reading would make the program illegal rather than productive.
var v: Integer = 2

switch (v) {
    case 1:
        print("one")

    case 2:
        print("two")

    default:
        print("other")
}
