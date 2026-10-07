// Solvik TCK SOL-TCK-0387
// The most derived method concatenates its own text after `super.label()`, so the printed BC shows super reached the immediate parent B rather than the root A.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `super.member` accesses the immediate superclass implementation.
//   - A `mutable` member may be overridden; all other members are final.
//
class mutable A {
    method mutable label(): String {
        return "A"
    }

    A() {
    }
}
class mutable B extends A {
    method override mutable label(): String {
        return "B"
    }

    B() {
    }
}
class C extends B {
    method override label(): String {
        return super.label() .. "C"
    }

    C() {
    }
}
var c: C = C()
print(c.label())
