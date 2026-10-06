// Solvik TCK SOL-TCK-0419
// Static properties of parenthesized nullable function type and of nullable-result function type are declared, one initialized to `null` and two with no initializer.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//   - Parentheses are required when nullability applies to the function value itself:
//   - (func(Integer): String)?  // nullable function value
//   - func(Integer): String?    // non-null function returning String?
//   - Each cell begins at its declared type's zero value — `0` for every integer type, `0.0` for `Float`/`Double`, `false` for `Boolean`, the NUL character `'\0'` for `Character`, and `null` for every reference type — whether or not the declaration supplies an initializer, so a static property needs no initializer
//
class Holder {
    static var operation: (func(Integer): String)? = null
    static var sharedOperation: (func(Integer): String)?
    static var nullableResult: func(Integer): String?
}
print("0419")
