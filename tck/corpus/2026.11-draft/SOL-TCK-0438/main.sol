// Solvik TCK SOL-TCK-0438
// Instantiations of one declaration at different types share its identity, and distinct declarations at the same type do not -- the identity belongs to the declaration
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Contextual instantiations of one generic declaration at different function types also share that declaration's canonical runtime identity: instantiation changes static typing, not the underlying executable value.
//   - Semantic equality for function values is reference identity, and `hashCode()` is the matching reference-identity hash. These operations are fixed and cannot be overridden.
//   - **Invariant.** When `left == right` is `true`, `left.hashCode() == right.hashCode()` is `true`. The converse is not required: unequal values may share a hash.
//   - A function value is identity-bearing, so a concrete function type and its nullable form are valid operands of `===` and `!==` when the ordinary compatibility rule also holds.
//
func identity<T>(value: T): T {
    return value
}

func echo<T>(value: T): T {
    return value
}

var asInteger: func(Integer): Integer = identity
var asString: func(String): String = identity
var sameShape: func(Integer): Integer = identity
var other: func(Integer): Integer = echo

print((asInteger === sameShape).toString() .. "\n")
print(asInteger.equals(asString).toString() .. "\n")
print((asInteger.hashCode() == asString.hashCode()).toString() .. "\n")
print(asInteger.equals(other).toString() .. "\n")
print(asInteger(1).toString() .. asString("two") .. "\n")
