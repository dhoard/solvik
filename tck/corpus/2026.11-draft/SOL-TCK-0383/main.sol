// Solvik TCK SOL-TCK-0383
// Two superclass names are forbidden.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Multiple class inheritance is forbidden.
//
class mutable A {
    A() {
    }
}
class mutable B {
    B() {
    }
}
class C extends A, B {
    C() {
    }
}
print("EXECUTED-INVALID")
