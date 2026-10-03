// Solvik TCK SOL-TCK-0332
// `toString` is declared without `override`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - declaring `toString` without `override`, changing its parameter list, or returning a type other than `String` is a compile-time error, and a stored member may not reuse the reserved name `toString`.
//
class A {
    A() {
    }

    func toString(): String {
        return "a"
    }
}
val a = A()
print(a.toString())

print("EXECUTED-INVALID")
