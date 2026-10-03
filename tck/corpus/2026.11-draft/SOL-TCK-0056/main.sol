// Oracle derived by hand from LANGUAGE_SPEC section 6, verbatim:
//   "`add(1, 2, 3)   // compile error: too many arguments`"
// Obligation: supplying too many arguments is a compile-time error. Paired with the
// too-few case, the two together pin that the required count is exactly the declared
// parameter count in both directions. Typing diagnostic family only; see the too-few
// case for why the specific code is not adopted.
func add(a: Integer, b: Integer): Integer {
    return a + b
}

print(add(1, 2, 3))
