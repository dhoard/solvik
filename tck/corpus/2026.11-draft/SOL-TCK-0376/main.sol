// Solvik TCK SOL-TCK-0376
// An unassigned delegate violates the normal constructor initialization rule.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A delegate is an immutable, explicitly typed property that must be initialized under the normal constructor rules.
//
interface P {
    func go(): Integer
}
class Impl implements P {
    Impl() {
    }

    func go(): Integer {
        return 1
    }
}
class X implements P {
    delegate var a: P

    X() {
    }
}
print("EXECUTED-INVALID")
