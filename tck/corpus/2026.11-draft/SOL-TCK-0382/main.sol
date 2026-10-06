// Solvik TCK SOL-TCK-0382
// The classes have identical members but no nominal relation, so the assignment is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two unrelated classes with identical members are not assignment-compatible.
//
class A {
    var value: String = "a"

    A() {
    }
}
class B {
    var value: String = "b"

    B() {
    }
}
var x: A = B()
print("EXECUTED-INVALID")
