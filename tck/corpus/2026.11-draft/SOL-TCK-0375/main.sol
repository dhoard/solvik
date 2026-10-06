// Solvik TCK SOL-TCK-0375
// A delegate declaration without an explicit type is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A delegate is an immutable, explicitly typed property that must be initialized under the normal constructor rules.
//
interface P {
    func go(): Integer
}
class X implements P {
    delegate var a

    X() {
    }
}
print("EXECUTED-INVALID")
