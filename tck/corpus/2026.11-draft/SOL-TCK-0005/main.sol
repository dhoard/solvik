// Oracle derived by hand from LANGUAGE_SPEC section 4, verbatim:
//   "Integral division truncates toward zero and division by zero raises a Solvik
//    runtime arithmetic error."
// Obligation (section 7): this is a RUNTIME_ERROR. The fault must be produced by
// execution (a structured RUNTIME_FAILURE with the protocol's arithmetic-error
// category), not by a compile-time check and not by constant folding, so the
// operands are stored in `var mutable` bindings the compiler cannot fold. No output
// precedes the fault, so the failure happens with empty stdout. The expected
// runtime category is the protocol's normative classification of a "Solvik runtime
// arithmetic error" (protocol.md section 4.1), not a value read from the IUT.
var mutable a: Integer = 10
var mutable b: Integer = 0
println(a / b)
