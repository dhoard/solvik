// Solvik TCK SOL-TCK-0384
// The delegate is assigned by `mutate` after initialization, which the immutable `var` forbids.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A delegate is an immutable, explicitly typed property that must be initialized under the normal constructor rules.
//
interface P {
    method go(): Integer
}
class Impl implements P {
    Impl() {
    }

    method go(): Integer {
        return 1
    }
}
class X implements P {
    delegate a: P

    X(p: P) {
        this.a = p
    }

    method mutate(p: P) {
        this.a = p
    }
}
print("EXECUTED-INVALID")
