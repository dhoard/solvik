// Solvik TCK SOL-TCK-0337
// A static member mentioning the class type parameter pins the specification-named SOLV-SEM-048.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static member may not mention a type parameter of its enclosing class, which is `SOLV-SEM-048`: the member is reached through the bare class name, where no instantiation of that parameter exists.
//   - | `SEM_TYPE_PARAMETER_IN_STATIC_MEMBER` | `SOLV-SEM-048` | a static member mentions a type parameter of its class |
//
class C<T> {
    static func f(x: T): T {
        return x
    }

    C() {
    }
}
print("EXECUTED-INVALID")
