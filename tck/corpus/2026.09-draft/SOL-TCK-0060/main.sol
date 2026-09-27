// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "A class with no written superclass derives directly from `Any`. Writing `extends Any`
//    is the explicit spelling of direct root derivation: it does not create a source class
//    symbol for `Any`"
// Obligation: `extends Any` is accepted and behaves as ordinary direct root derivation --
// the class is constructed normally and its property reads work. The oracle is the value
// passed to the constructor, so a rejection of the syntax or a miscompiled initializer
// both fail.
class Point extends Any {
    val x: Integer

    Point(x: Integer) {
        this.x = x
    }
}

print("root=" .. Point(4).x)
