// Solvik TCK SOL-TCK-0382
// The classes have identical members but no nominal relation, so the assignment is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two unrelated classes with identical members are not assignment-compatible.
//
class A {
    val value: String = "a"

    A() {
    }
}
class B {
    val value: String = "b"

    B() {
    }
}
val x: A = B()
print("EXECUTED-INVALID")
