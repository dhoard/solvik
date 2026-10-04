// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "There is no three-clause `for` statement: it would require semicolons inside its
//    header, which section 16 reserves for separating constructs on one line.
//    Initializer-scoped counting loops are written as a scope block around a `while` loop."
//   "`break` and `continue` are valid only inside a loop."
// Obligation: the always-true form of the prescribed idiom iterates until the body's own
// `break` exits the loop. The body increments only when the guard has not fired, so the
// printed pass count is exactly 3 (i = 0,1,2 increment; i = 3 breaks first).
// Discriminating value: an implementation whose `break` did not exit the enclosing loop
// runs forever or mis-counts instead of printing the derived 3. The oracle is the
// derived integer 3, not an observed value.
mutable val n: Integer = 0

{
    mutable val i: Integer = 0

    while (true) {
        if (i >= 3) {
            break
        }

        n = n + 1
        i = i + 1
    }
}

print(n)
