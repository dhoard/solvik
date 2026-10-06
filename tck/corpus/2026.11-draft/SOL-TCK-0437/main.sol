// Solvik TCK SOL-TCK-0437
// Written type arguments on direct calls keep working beside a value reference whose instantiation comes only from its declared type, so neither mechanism is doing the other's work
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

func pair<A, B>(a: A, b: B): List<A> {
    return List<A>(a)
}

var made: func(String): String = identity

print(identity<Integer>(7).toString() .. "\n")
print(identity("text") .. "\n")
print(pair("k", 1).size.toString() .. "\n")
print(pair<Integer, String>(2, "x").get(0).toString() .. "\n")
print(made("from-value"))
