// Oracle derived by hand from LANGUAGE_SPEC section 6, verbatim:
//   "For a callable declared with only required parameters, the required count is the
//    number of declared parameters"
//   "`add(1)         // compile error: too few arguments`"
// Obligation: supplying too few arguments is a source-located compile-time error. The
// specification also fixes WHEN it is reported --
//   "A statically resolved call's arity is verified before its argument types and before
//    generic type-argument inference."
// -- so the diagnostic must be an arity rejection rather than an argument-type rejection;
// the specification names SOLV-TYPE-003 only for `Result` operation calls (section
// 23.4), so the general arity code is not adopted and the typing family is asserted.
func add(a: Integer, b: Integer): Integer {
    return a + b
}

print(add(1))
