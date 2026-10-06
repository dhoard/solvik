// Solvik TCK SOL-TCK-0426
// A nullable function value is `===` to the concrete value of the same declaration and not to `null`; two declarations are not `===` to each other while one value's hash matches itself; and `println`, `..`, and `toString()` all render `func`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The identity-bearing static types are exactly:
//   - - function types (section 6);
//   - A function value is identity-bearing, so a concrete function type and its nullable form are valid operands of `===` and `!==` when the ordinary compatibility rule also holds.
//   - `Any` remains invalid for identity operations without refinement, as it is for every other identity-bearing runtime value.
//   - Semantic equality for function values is reference identity, and `hashCode()` is the matching reference-identity hash. These operations are fixed and cannot be overridden.
//   - `toString()` for every function value returns the exact string `func`. It must not expose a Java class name, memory address, node name, module path, captured values, or implementation details, so `print`, `println`, and `..` render every function value as `func`.
//   - A failure of assignability uses the ordinary invalid-operand diagnostic; a compatible pair with no identity-bearing operand uses `SOLV-TYPE-039`.
//   - Every reference evaluation to the same declared top-level function produces the same canonical
//
func format(value: Integer): String {
    return "v" .. value.toString()
}

func describe(value: Integer): String {
    return "d" .. value.toString()
}

func makeFormatter(): func(Integer): String {
    return format
}

func take(operation: func(Integer): String): String {
    return operation(1)
}

func takeAny(value: Any): String {
    return value.toString()
}

var asAny: Any = format
var branch: Any = makeFormatter()
var optional: (func(Integer): String)? = format
print(take(makeFormatter()) .. take(describe) .. takeAny(asAny) .. takeAny(branch) .. "\n")
println(optional === format)
println(optional === null)
println(format === describe)
println(format.hashCode() == format.hashCode())
println(format)
println("" .. format .. "|" .. format.toString())
