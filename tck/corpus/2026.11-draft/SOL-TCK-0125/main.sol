// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A value-required block whose every path completes abruptly has type `Nothing` and never
// evaluates a tail expression", together with 21.1's "Abrupt completion carries no value
// and does not participate in result joining."
// The block inside `f` only returns, so `f`'s body never produces a value from it and the
// value-returning-function rule applies. Section 17 names that rule's code: a value-returning
// function "must return on every path (`SOLV-TYPE-012`)". This is the observable difference
// between `Nothing` and a fabricated `Unit`, zero, `null`, or empty string, all of which
// section 21 forbids the implementation from inventing.
func f(): Integer {
    var v = {
        return 7
    }
}
print("EXECUTED-INVALID")
