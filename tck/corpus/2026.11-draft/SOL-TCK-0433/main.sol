// Solvik TCK SOL-TCK-0433
// Both directions between `List<func(Animal): Dog>` and `List<func(Dog): Animal>` are refused, because generic type arguments stay invariant even where the element types are comparable. The sources carry explicit type arguments so inference cannot explain it.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every non-null function type has `Any` as its top supertype, and a nullable function type relates to another under those same rules.
//   - The shared type join understands function types: for two same-arity function types each joined parameter takes the more specific of the two when one is assignable to the other, and the joined result is their nearest common result type. That joined function type is the least common function supertype allowed by contravariant parameters and covariant results.
//   - When a parameter pair is unrelated or the results have no unique join, no function-type join exists and the ordinary join may still select a shared nominal supertype such as `Any`; a join never introduces `Nothing`, a union, or an intersection in order to manufacture a function supertype.
//   - Generic type arguments remain invariant, so `List<func(Dog): Animal>` and `List<func(Animal): Dog>` are unrelated applications even though the function types inside them are comparable.
//   - An invocation whose callee is not a function type is `SOLV-TYPE-002`, and a call with the wrong number of arguments is `SOLV-TYPE-003`.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
mutable class Animal {
    var name: String = "animal"
}

class Dog extends Animal {
}

// Generic type arguments remain invariant, so these two applications are unrelated even
// though `func(Animal): Dog` is assignable to `func(Dog): Animal`. Both directions are
// static initializers, the placement whose non-assignable diagnostic the section names
// verbatim, so each direction is pinned.
class Invariant {
    static var wide: List<func(Animal): Dog> = List<func(Animal): Dog>()
    static var narrow: List<func(Dog): Animal> = List<func(Dog): Animal>()
    static var intoNarrow: List<func(Dog): Animal> = Invariant.wide
    static var intoWide: List<func(Animal): Dog> = Invariant.narrow
}

print("EXECUTED-INVALID")
