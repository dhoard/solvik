// Solvik TCK SOL-TCK-0434
// A `render` value fills a binding of the function type written from an unrelated declaration's shape and stays `===` to a second binding of that written type, so the producing declaration is not part of function-type identity.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical. The declarations that produced values of those types do not affect type identity.
//   - Structural comparison is confined to function types: two unrelated classes with identical members remain assignment-incompatible (section 3).
//   - Semantic equality for function values is reference identity, and `hashCode()` is the matching reference-identity hash. These operations are fixed and cannot be overridden.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
func format(value: Integer): String {
    return "v" .. value.toString()
}

func render(value: Integer): String {
    return "r" .. value.toString()
}

func through(callback: func(Integer): String): String {
    return callback(1)
}

// Function types are structural, and the declarations that produced values of those types
// do not affect type identity: `format` and `render` are unrelated declarations, and a
// value of one is assignable to a binding of the other's type.
var substituted: func(Integer): String = render

// Two values of one written function type compare by reference identity, not by which
// declaration produced them, so a structural substitution does not change identity.
var alsoSubstituted: func(Integer): String = render
print(through(format) .. "-" .. through(substituted) .. "-" .. through(alsoSubstituted) .. "-" .. (substituted === alsoSubstituted))
