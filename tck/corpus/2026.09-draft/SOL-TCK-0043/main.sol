// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`for` uses exactly three clauses: an optional local declaration or assignment, an
//    optional Boolean condition, and an optional assignment. The two separators inside
//    `for (...)` are explicit semicolons. An omitted condition is `true`."
//   "`break` and `continue` are valid only inside a loop."
// Obligation: three-clause `for` with all clauses present, `continue` skipping 3 and
// `break` stopping at 6. Accumulated operands: 0+1+2+4+5 = 12. Each statement is
// discriminating on its own, and each variant was computed rather than assumed: removing
// the `continue` yields operands 0..5 (total 15); removing the `break` yields
// 0,1,2,4..9 (total 42); removing both yields 0..9 (total 45).
var total: Integer = 0

for (var i: Integer = 0; i < 10; i = i + 1) {
    if (i == 3) {
        continue
    }

    if (i == 6) {
        break
    }

    total = total + i
}

print(total)
