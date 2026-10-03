// Negative nullability conformance test: null is assignable only to nullable types.
// LANGUAGE_SPEC section 5 gives this case verbatim, both as prose -- "`null` is
// assignable only to nullable types" -- and as the annotated example
// `val bad: String = null // compile error`. The program is that exact declaration.
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
val bad: String = null
print(bad)
println("EXECUTED-INVALID")
