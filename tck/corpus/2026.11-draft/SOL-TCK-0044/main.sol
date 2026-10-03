// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "An omitted condition is `true`."
// Obligation: a three-clause `for` whose condition clause is omitted iterates until the
// body's own `break`. The body increments only when the guard has not fired, so the
// printed pass count is exactly 3 (i = 0,1,2 increment; i = 3 breaks first).
// Discriminating value: an implementation treating an omitted condition as `false` would
// run zero iterations and print 0. The oracle is the derived integer 3, not an
// observed value.
mutable val n: Integer = 0

for (mutable val i: Integer = 0;;i = i + 1) {
    if (i >= 3) {
        break
    }

    n = n + 1
}

print(n)
