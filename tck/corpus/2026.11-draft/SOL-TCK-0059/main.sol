// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "An overriding method must have exactly the inherited parameter types and may return a
//    subtype of the inherited return type."
// Obligation: an `override` whose parameter list does not match the inherited member is
// rejected. Declaring the parameter types as a hard requirement (unlike the return type,
// which is permitted to vary covariantly) makes the rejection direction unambiguous, so
// this cannot be misread as a permissive covariance rule. Semantic family only; the
// specification names no code for this rule.
mutable class Base {
    mutable func label(): String {
        return "base"
    }
}

class Sub extends Base {
    override func label(x: Integer): String {
        return "sub"
    }
}

print("unreachable")
