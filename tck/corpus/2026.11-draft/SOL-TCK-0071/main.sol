// Oracle derived by hand from LANGUAGE_SPEC section 13, verbatim:
//   "Cases are tested in source order and exactly the first matching case executes."
// Obligation: when two cases match the same value, ONLY the first in source order runs.
// The duplicate-case shape is the only way to observe "exactly the first" as opposed to
// merely "one of them": a first-match-wins implementation prints "nine", an
// all-matches-wins implementation prints "ninenine again", and a last-match-wins one
// prints "nine again".
mutable val v: Integer = 9

switch (v) {
    case 1:
        print("one")

    case 9:
        print("nine")

    case 9:
        print("nine again")

    default:
        print("other")
}
