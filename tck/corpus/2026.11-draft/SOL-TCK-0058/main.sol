// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "Overrides must always use `override`."
// Obligation: a subclass member that redeclares an inherited `mutable` member without the
// `override` keyword is rejected statically. The specification states the requirement but
// names no stable code for it, so only the semantic diagnostic family is asserted (see
// ORACLE_REVIEW.md); the code the current implementation emits is not adopted.
class mutable Base {
    method mutable label(): String {
        return "base"
    }
}

class Sub extends Base {
    method label(): String {
        return "sub"
    }
}

print(Sub().label())
