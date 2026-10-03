// Solvik TCK SOL-TCK-0341
// A stored property in an interface is the negated sentence.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Interfaces contain methods, not stored properties.
//
interface Named {
    val name: String
}
class U implements Named {
    val name: String

    U(name: String) {
        this.name = name
    }
}
val u = U("x")
print(u.name)

print("EXECUTED-INVALID")
