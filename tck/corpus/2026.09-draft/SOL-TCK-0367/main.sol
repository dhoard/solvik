// Solvik TCK SOL-TCK-0367
// `this` inside a static method pins the specification-named SOLV-RESOL-005.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static member has no receiver. `this` and every `super` form are rejected inside a static method body and inside a class initializer block: `this` is `SOLV-RESOL-005` and `super` is `SOLV-RESOL-006`.
//
class C {
    static var n: Integer = 1

    static func f(): Integer {
        return this.n
    }

    C() {
    }
}
print(C.f())

print("EXECUTED-INVALID")
