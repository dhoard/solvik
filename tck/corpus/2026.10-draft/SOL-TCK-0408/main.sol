// Solvik TCK SOL-TCK-0408
// Extending a class that was not declared open is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Classes are final by default.
//   - A class must explicitly opt into inheritance:
//
class A {
    A() {
    }
}
class B extends A {
    B() {
    }
}
print("EXECUTED-INVALID")
