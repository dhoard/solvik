// Solvik TCK SOL-TCK-0345
// Two interfaces supply the same default and the class does not override it.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - If multiple interfaces provide an otherwise unresolved default for the same method, the class must explicitly override it.
//
interface A {
    func speak(): String {
        return "a"
    }
}
interface B {
    func speak(): String {
        return "b"
    }
}
class C implements A, B {
    C() {
    }
}
var c = C()
print(c.speak())

print("EXECUTED-INVALID")
