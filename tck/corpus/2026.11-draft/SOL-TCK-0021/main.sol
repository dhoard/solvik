// Positive conformance test: `val` freezes the binding, not the object graph.
// Oracle derived by hand from LANGUAGE_SPEC section 2, verbatim: "`val` freezes the
// binding, not the complete reachable object graph", demonstrated by that section's
// example in which `user.name = "Douglas"` is marked "valid" and `user = User("Other")`
// is marked "compile error". This test asserts the VALID half of that pair: a `val`
// holding an object may have its `mutable val` property reassigned, and the new value is what a
// later read observes. SOL-TCK-0010 asserts the rejected half (rebinding the `val`).
// Expected stdout is the reassigned name; `print` appends no separator (section 5).
class User {
    mutable val name: String

    User(name: String) {
        this.name = name
    }
}

val user = User("Doug")
user.name = "Douglas"
print(user.name)
