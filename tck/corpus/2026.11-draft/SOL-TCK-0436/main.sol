// Solvik TCK SOL-TCK-0436
// One generic reference, instantiated at four different monomorphic function types across the static-property, instance-property, binding, assignment, and generic-argument positions, and then called at each
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A generic function declaration does not itself produce a first-class polymorphic value.
//   - It must be instantiated to one monomorphic function type at each value-reference site, and that instantiation is contextual:
//   - The expected function type supplies constraints for every declared type parameter.
//   - The compiler determines one complete substitution, applies it to the function's declared parameter and result types, and then checks ordinary function-type assignability.
//   - Inference first unifies occurrences in the declared parameter types with the expected parameter types; result positions may confirm or complete a unique substitution but never choose arbitrarily among several valid types.
//   - All type parameters must be resolved, and the decision is made before lowering: a function value performs no runtime type dispatch.
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//
func identity<T>(value: T): T {
    return value
}

func second<A, B>(first: A, other: B): B {
    return other
}

func wrap<T>(value: T): List<T> {
    return List<T>(value)
}

class Holder {
    var member: func(Integer): Integer = identity
    static var mutable shared: func(String): String = identity
}

var integerIdentity: func(Integer): Integer = identity
var stringIdentity: func(String): String = identity
var pick: func(Integer, String): String = second
var boxed: func(Integer): List<Integer> = wrap
var callbacks: List<func(Integer): Integer> = List<func(Integer): Integer>(identity)

var holder = Holder()
Holder.shared = identity
var taken: func(String): String = Holder.shared

print(integerIdentity(41).toString() .. "\n")
print(stringIdentity("ab") .. "\n")
print(pick(1, "two") .. "\n")
print(boxed(3).size.toString() .. "\n")
print(holder.member(5).toString() .. "\n")
print(taken("cd") .. "\n")
print(callbacks.get(0)(6).toString() .. "\n")
