// Solvik TCK SOL-TCK-0327
// The property name equals the class name.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class member declaration other than the constructor cannot have the same name as its class.
//
class User {
    var User: Integer = 1
}
var u = User()
print(u.User)

print("EXECUTED-INVALID")
