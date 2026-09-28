// Solvik TCK SOL-TCK-0342
// The implementing method changes the interface parameter type to Integer.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An implementing method must use the same parameter types and a covariant return type.
//
interface Named {
    func greet(who: String): String
}
class U implements Named {
    U() {
    }

    func greet(who: Integer): String {
        return "hi"
    }
}
print("EXECUTED-INVALID")
