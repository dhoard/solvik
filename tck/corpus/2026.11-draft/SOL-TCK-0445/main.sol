// Solvik TCK SOL-TCK-0445
// A two-parameter generic against a one-parameter function type resolves every shared position, so the remaining mismatch is ordinary assignability and not an inference failure
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The compiler determines one complete substitution, applies it to the function's declared parameter and result types, and then checks ordinary function-type assignability.
//   - It must be instantiated to one monomorphic function type at each value-reference site, and that instantiation is contextual:
//   - All type parameters must be resolved, and the decision is made before lowering: a function value performs no runtime type dispatch.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
func second<A, B>(first: A, other: B): B {
    return other
}

class Boundary {
    static var mismatched: func(Integer): Integer = second
}

print("EXECUTED-INVALID")
