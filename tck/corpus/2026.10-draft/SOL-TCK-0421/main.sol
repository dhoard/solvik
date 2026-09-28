// Solvik TCK SOL-TCK-0421
// The implementation's parameter differs from the interface's only inside the nested function type, so the parameter types are not the same and the implementation fails.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical.
//   - An implementing method must use the same parameter types and a covariant return type.
//   - A function type may appear wherever another non-deferred type may appear, including as the type of a local, parameter, return, property, or static property, as a generic type argument, as the inner type of a nullable type, and in the parameter or return position of another function type:
//
interface Factory {
    func use(builder: func(): func(Integer): String): String
}

class Simple implements Factory {
    func use(builder: func(): func(): String): String {
        return "s"
    }
}
print("EXECUTED-INVALID")
