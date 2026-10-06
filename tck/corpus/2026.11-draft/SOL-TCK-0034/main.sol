// Negative nullability conformance test: a nullable type is not assignable to non-null.
// LANGUAGE_SPEC section 5 states verbatim: "If `S` is a subtype of `T`, then `S` is
// assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null
// `T`." Taking S = T = String, `String?` is therefore not assignable to `String`, which
// is exactly the declaration below.
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
var n: String? = "x"
var m: String = n
print(m)
println("EXECUTED-INVALID")
