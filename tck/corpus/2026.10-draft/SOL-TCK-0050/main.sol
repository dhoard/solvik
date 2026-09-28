// Oracle derived by hand from LANGUAGE_SPEC section 6, verbatim:
//   "Because display is defined by `toString`, a class override is honored by `print`,
//    `println`, and `..`."
// section 4, verbatim:
//   "A user-defined class inherits the default representation (its class name) and may
//    declare `override func toString(): String` for a class-specific representation"
// Obligation: with an override present, the `..` operator must render through it, so the
// rendered text is the override's "$5" rather than the inherited default representation
// "Money". Rendering inside `print` (which appends no separator) keeps the expected
// stream byte-exact and platform-independent. Asserting the `println` half would require
// baking the platform line separator into a portable oracle, which the specification
// deliberately leaves platform-defined, so this test covers only the `..` clause.
class Money {
    val amount: Integer

    Money(amount: Integer) {
        this.amount = amount
    }

    override func toString(): String {
        return "$" .. this.amount
    }
}

val m: Any = Money(5)
print("[" .. m .. "]")
