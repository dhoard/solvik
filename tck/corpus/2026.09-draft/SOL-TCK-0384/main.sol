// Solvik TCK SOL-TCK-0384
// The delegate is assigned by `mutate` after initialization, which the immutable `val` forbids.
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
    delegate val a: P

    X(p: P) {
        this.a = p
    }

    func mutate(p: P) {
        this.a = p
    }
}
print("EXECUTED-INVALID")
