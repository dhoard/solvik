// Negative numerics conformance test: widening is exactly the enumerated relation, and
// no other conversion is implicit.
// LANGUAGE_SPEC section 4 lists the implicit widening relation as holding "for exactly":
// integral along Byte->Short->Integer->Long; Byte/Short to Float and Byte/Short/Integer
// to Double; and Float to Double. It then states "No other conversion is implicit" and
// "No narrowing is implicit." `Double` to `Float` is narrowing AND in the precision-losing
// direction, so it is not implicit and the declaration below must be rejected.
//
// Deliberate scope limit -- NO code asserted. The specification names `SOLV-TYPE-001`
// for an assignability failure in only two other contexts (section 7: 'A static
// declaration initializer that is not assignable to the declared type is
// `SOLV-TYPE-001`', and section 22.1: 'a non-`String?` message is `SOLV-TYPE-001`
// (`TYPE_MISMATCH`)'), and never binds a code to an ordinary local declaration failing
// assignability. Generalizing from those neighbouring cases would be the TCK inventing a
// normative code, which TCK.md section 6 forbids, so the oracle asserts the TYPE family
// (protocol.md section 4.1) and the requirement is recorded
// with `diagnosticNormative: false`. This is a genuine specification gap worth raising.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
var x: Double = 1.5
var y: Float = x
print(y)
println("EXECUTED-INVALID")
