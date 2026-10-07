// Solvik TCK SOL-TCK-0335
// Each uninitialized static cell reads its declared type's zero value from the specification's list.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Each cell begins at its declared type's zero value -- `0` for every integer type, `0.0` for `Float`/`Double`, `false` for `Boolean`, the NUL character `'\0'` for `Character`, and `null` for every reference type -- whether or not the declaration supplies an initializer,
//
class C {
    var static mutable i: Integer
    var static mutable b: Boolean
    var static mutable d: Double
    var static mutable s: String

    C() {
    }
}
print(C.i)
print(C.b)
print(C.d)
print(C.s)
