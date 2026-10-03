// Solvik TCK SOL-TCK-0328
// A second constructor declaration is the negated at-most-one rule.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class has at most one constructor declaration.
//
class User {
    User() {
    }

    User(x: Integer) {
    }
}
val u = User()
print("EXECUTED-INVALID")
