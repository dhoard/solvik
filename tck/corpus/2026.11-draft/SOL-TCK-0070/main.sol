// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "Cases are tested in source order and exactly the first matching case executes."
//   "A switch contains at most one `default`, and it must be last."
// Obligation: with no matching constant case, the trailing `default` runs and nothing
// else does. The exact value "other" distinguishes it from the case body ("one") and
// from a switch that ran both.
mutable val v: Integer = 3

switch (v) {
    case 1:
        print("one")

    default:
        print("other")
}
