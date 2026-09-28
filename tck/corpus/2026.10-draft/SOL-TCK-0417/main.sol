// Solvik TCK SOL-TCK-0417
// Static functions carry function types in parameter and nested result position in each written spelling and forward through them, so every position resolves.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//   - Omitting the return type means `Unit`, exactly as it does for a function declaration, so `func()` and `func(): Unit` name the same type.
//   - Parameter names do not appear in a function type: parameter names belong to declarations and have no role in function-type identity or assignability.
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical.
//
class Holder {
    static func forward(callback: func(Integer): String): func(Integer): String {
        return Holder.pass(callback)
    }

    static func pass(callback: func(Integer): String): func(Integer): String {
        return callback
    }

    static func forwardUnit(basic: func()): Unit {
        Holder.run(basic)
    }

    static func run(basic: func()) {
    }

    static func forwardNullable(nullableResult: func(Integer): String?): func(Integer): String? {
        return nullableResult
    }
}
print("0417")
