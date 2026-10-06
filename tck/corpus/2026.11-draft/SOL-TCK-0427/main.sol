// Solvik TCK SOL-TCK-0427
// Two `Any` bindings, one holding a function value, remain a compatible pair with no identity-bearing operand, which the section pins to `SOLV-TYPE-039`.
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

var boxed: Any = format
var other: Any = 1
print(boxed === other)
print("EXECUTED-INVALID")
