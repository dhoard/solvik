// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "There is no three-clause `for` statement: it would require semicolons inside
//    its header, which section 16 reserves for separating constructs on one line."
// The removed construct must be rejected, not silently reinterpreted: this is the
// canonical three-clause spelling with all three clauses present. The specification
// names no diagnostic code for the rejection, so the expectation is asserted bare;
// any compile-time error that leaves the sentinel unprinted satisfies the oracle.
func counted(limit: Integer): Integer {
    var mutable total: Integer = 0
    for (var mutable i: Integer = 0; i < limit; i = i + 1) {
        total = total + i
    }
    return total
}

print(counted(3))
print("EXECUTED-INVALID")
