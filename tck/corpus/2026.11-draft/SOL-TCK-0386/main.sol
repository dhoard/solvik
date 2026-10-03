// Solvik TCK SOL-TCK-0386
// Variant A carries an Integer payload, so the String argument is not assignable.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Assignments are statements, not value-producing expressions. The target must be a `mutable val` local or a `mutable val` property. Calls require exact arity, and each argument must be assignable to its declared parameter type.
//
enum E {
    A(Integer)
    B(String)
}
val x: E = E.A("wrong")
print("EXECUTED-INVALID")
