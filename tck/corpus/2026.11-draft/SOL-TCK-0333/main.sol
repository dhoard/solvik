// Solvik TCK SOL-TCK-0333
// The property is assigned only on the true branch, so a false-path instance is not definitely initialized.
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
    }
}
var u = U(true)
print("EXECUTED-INVALID")
