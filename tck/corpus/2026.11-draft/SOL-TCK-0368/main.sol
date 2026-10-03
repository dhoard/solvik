// Solvik TCK SOL-TCK-0368
// `super` inside a static method pins the specification-named SOLV-RESOL-006.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static member has no receiver. `this` and every `super` form are rejected inside a static method body and inside a class initializer block: `this` is `SOLV-RESOL-005` and `super` is `SOLV-RESOL-006`.
//
mutable class A {
    A() {
    }
}
class C extends A {
    static func f(): Integer {
        return super.hashCode()
    }

    C() {
        super()
    }
}
print(C.f())

print("EXECUTED-INVALID")
