// Solvik TCK SOL-TCK-0331
// The subclass constructor omits the required `super(...)` call.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A subclass constructor must invoke `super(arguments)` as its first statement when the superclass has no zero-argument initializer; otherwise `super()` is implicit.
//
mutable class A {
    A(x: Integer) {
    }
}
class B extends A {
    B() {
    }
}
var b = B()
print("EXECUTED-INVALID")
