// Negative numerics conformance test: no least common widened type is an ill-typed
// operator, not a silent fallback.
// LANGUAGE_SPEC section 4 states verbatim: "When two numeric operands have no such common
// type (for example `Long` and `Float`) the operator is ill-typed." The specification
// supplies this exact operand pair as its example. `Long` widens only to `Long`, and
// `Float` widens only to `Double`, so no unique minimal type admits both and `big + f`
// must be rejected.
//
// Deliberate scope limit -- NO code asserted: the specification states this rule but
// binds no diagnostic code to it, and TCK.md section 6 forbids promoting the
// implementation's enum entry to normative status. The oracle asserts the TYPE family
// (protocol.md section 4.1) and the requirement is
// recorded with `diagnosticNormative: false`.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
val big: Long = 5L
val f: Float = 1.0f
print(big + f)
println("EXECUTED-INVALID")
