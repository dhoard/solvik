// Solvik TCK SOL-TCK-0329
// The constructor invokes itself as `this.User()`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - is not declared by an interface, is not forwarded by a `delegate`, and cannot be invoked as `this.User(...)`.
//
class User {
    User() {
        this.User()
    }
}
var u = User()
print("EXECUTED-INVALID")
