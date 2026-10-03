// Solvik TCK SOL-TCK-0422
// The interface writes `func(): Unit` and parameter name `basic`; the implementation writes `func()` under a different parameter name and matches, so both spellings name one function type.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//   - Omitting the return type means `Unit`, exactly as it does for a function declaration, so `func()` and `func(): Unit` name the same type.
//   - Parameter names do not appear in a function type: parameter names belong to declarations and have no role in function-type identity or assignability.
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical.
//
interface Scheduler {
    func run(basic: func(): Unit): Unit

    func describe(task: func(Integer): String): func(Integer): String {
        return task
    }
}

class Job implements Scheduler {
    func run(ignored: func()) {
    }

    func describe(callback: func(Integer): String): func(Integer): String {
        return callback
    }
}
print("0422")
