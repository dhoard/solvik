// Solvik TCK SOL-TCK-0334
// Assigning on both branches satisfies definite initialization and the read observes the taken branch; the arm differs from the rejection only by the else-assignment.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every property without a declaration initializer must be assigned exactly once on every successful constructor path before it is read; a `var` property cannot be assigned afterward.
//
class U {
    var name: String

    U(c: Boolean) {
        if (c) {
            this.name = "a"
        }
        else {
            this.name = "b"
        }
    }
}
var u: U = U(true)
print("def" .. u.name)
