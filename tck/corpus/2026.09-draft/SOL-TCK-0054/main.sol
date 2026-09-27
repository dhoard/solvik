// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`if` and loop conditions must have type `Boolean`."
// Obligation: an `Integer` condition is ill-typed and must be rejected statically. The
// specification states the requirement as a typing constraint but names no stable code
// for this case, so only the typing diagnostic family is asserted.
val n: Integer = 5

if (n) {
    print("y")
}
