// Solvik TCK SOL-TCK-0387
// The most derived method concatenates its own text after `super.label()`, so the printed BC shows super reached the immediate parent B rather than the root A.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `super.member` accesses the immediate superclass implementation.
//   - An `open` member may be overridden; all other members are final.
//
open class A {
    open func label(): String {
        return "A"
    }

    A() {
    }
}
open class B extends A {
    open override func label(): String {
        return "B"
    }

    B() {
    }
}
class C extends B {
    override func label(): String {
        return super.label() .. "C"
    }

    C() {
    }
}
val c = C()
print(c.label())
