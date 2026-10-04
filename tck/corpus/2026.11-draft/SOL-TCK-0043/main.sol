// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "There is no three-clause `for` statement: it would require semicolons inside its
//    header, which section 16 reserves for separating constructs on one line.
//    Initializer-scoped counting loops are written as a scope block around a `while` loop."
//   "`break` and `continue` are valid only inside a loop."
// Obligation: the prescribed scope-plus-`while` idiom drives counter mutation, a
// condition tested before each iteration, and a `break` that binds the enclosing loop.
// Accumulated operands: 0+1+2+4+5 = 12. Each statement is discriminating on its own,
// and each variant was computed rather than assumed: removing the `!= 3` guard yields
// operands 0..5 (total 15); removing the `break` guard yields 0,1,2,4..9 (total 42);
// removing both yields 0..9 (total 45).
mutable val total: Integer = 0

{
    mutable val i: Integer = 0

    while (i < 10) {
        if (i == 6) {
            break
        }

        if (i != 3) {
            total = total + i
        }

        i = i + 1
    }
}

print(total)
