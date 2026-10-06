// Negative numerics conformance test: `Integer` does not widen to `Float`.
// LANGUAGE_SPEC section 4 states the named exception explicitly: "`Integer` does not
// widen to `Float` (the 24-bit `Float` significand cannot hold every `Integer`)". The
// declaration below asserts exactly that named non-relation, so it must be rejected even
// though `Integer` DOES widen to `Double` (which section 4 also states, and which is the
// positive control this negative is distinguished from).
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
var i: Integer = 1
var f: Float = i
print(f)
println("EXECUTED-INVALID")
