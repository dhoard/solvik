// Solvik TCK SOL-TCK-0341
// A stored property in an interface is the negated sentence.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Interfaces contain methods, not stored properties.
//
interface Named {
    var name: String
}
class U implements Named {
    var name: String

    U(name: String) {
        this.name = name
    }
}
var u = U("x")
print(u.name)

print("EXECUTED-INVALID")
