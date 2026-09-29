// Solvik TCK SOL-TCK-0444
// A callee whose type parameter appears only inside a nested function type binds it by descending two function types position by position
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A direct call whose target is statically known keeps its existing statically resolved path.
//   - Function values add an indirect call path; they do not replace direct calls, and a call such as `sum(1, 2)` is never lowered into constructing a function value and then invoking it.
//   - Function-type assignability is contravariant in parameters and covariant in the result.
//   - Inference first unifies occurrences in the declared parameter types with the expected parameter types; result positions may confirm or complete a unique substitution but never choose arbitrarily among several valid types.
//   - Function types participate in ordinary nullability, flow analysis, generic substitution, and definite initialization.
//
func identity<T>(value: T): T {
    return value
}

func duplicator<T>(f: func(T): T): func(T): T {
    return f
}

val viaString: func(String): String = duplicator(identity)
val viaInteger: func(Integer): Integer = duplicator(identity)

print(viaString("ab") .. "\n")
print(viaInteger(20).toString() .. "\n")
