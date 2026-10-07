// Solvik TCK SOL-TCK-0346
// The explicit override resolves the same default from both interfaces and its value is what runs.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - If multiple interfaces provide an otherwise unresolved default for the same method, the class must explicitly override it.
//
interface A {
    method speak(): String {
        return "a"
    }
}
interface B {
    method speak(): String {
        return "b"
    }
}
class C implements A, B {
    C() {
    }

    method speak(): String {
        return "c"
    }
}
var c: C = C()
print("conf" .. c.speak())
