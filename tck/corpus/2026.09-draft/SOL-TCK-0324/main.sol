// Solvik TCK SOL-TCK-0324
// Two same-named functions with different parameter types are an overload pair.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Functions are not overloaded in the initial language: two functions with the same name in one scope are a compile-time error.
//
func f(a: Integer): Integer {
    return a
}
func f(a: String): String {
    return a
}
print(f(1))

print("EXECUTED-INVALID")
