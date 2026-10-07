// Positive conformance test: `var` freezes the binding, not the object graph.
// Oracle derived by hand from LANGUAGE_SPEC section 2, verbatim: "`var` freezes the
// binding, not the complete reachable object graph", demonstrated by that section's
// example in which `user.name = "Douglas"` is marked "valid" and `user = User("Other")`
// is marked "compile error". This test asserts the VALID half of that pair: a `var`
// holding an object may have its `var mutable` property reassigned, and the new value is what a
// later read observes. SOL-TCK-0010 asserts the rejected half (rebinding the `var`).
// Expected stdout is the reassigned name; `print` appends no separator (section 5).
class User {
    var mutable name: String

    User(name: String) {
        this.name = name
    }
}

var user: User = User("Doug")
user.name = "Douglas"
print(user.name)
