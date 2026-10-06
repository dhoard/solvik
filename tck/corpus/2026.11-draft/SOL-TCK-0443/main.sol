// Solvik TCK SOL-TCK-0443
// Argument positions instantiate a generic reference from the callee's parameter, including a parameter settled by a later argument and the same callee as a function value
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

func apply<T>(f: func(T): T, v: T): T {
    return f(v)
}

func outer<U>(f: func(U): U, v: U): U {
    return f(v)
}

var composed: func(func(Integer): Integer, Integer): Integer = apply

print(apply(identity, 42).toString() .. "\n")
print(apply(identity, "xy") .. "\n")
print(outer(identity, 1).toString() .. "\n")
print(composed(identity, 3).toString() .. "\n")
