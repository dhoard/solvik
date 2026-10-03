// Solvik TCK SOL-TCK-0423
// The interface's parameter is a nullable function value and the implementation's is a function returning a nullable `String`; the two groupings name different types, so the implementation fails.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Parentheses are required when nullability applies to the function value itself:
//   - (func(Integer): String)?  // nullable function value
//   - func(Integer): String?    // non-null function returning String?
//   - The grouping is part of the written type, not a property a compiler may recover from source text.
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical.
//   - An implementing method must use the same parameter types and a covariant return type.
//
interface Holder {
    func take(operation: (func(Integer): String)?): String
}

class Simple implements Holder {
    func take(operation: func(Integer): String?): String {
        return "s"
    }
}
print("EXECUTED-INVALID")
