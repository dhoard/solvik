// Oracle derived by hand from LANGUAGE_SPEC section 4, verbatim:
//   "Integral arithmetic is checked and raises a Solvik runtime arithmetic error on
//    overflow."  and section 4: "Decimal integer literals ... have type Integer ...
//    A literal outside the signed 32-bit range is a compile-time error."
// Obligation (section 7): this is a RUNTIME_ERROR. 2147483647 is the largest signed
// 32-bit value (2^31 - 1); adding 1 at run time overflows the checked `Integer` and
// must raise a Solvik runtime arithmetic error -- a structured RUNTIME_FAILURE with
// the protocol's arithmetic-error category. The base value is stored in a mutable
// `mutable val` so the addition is an actual run-time operation, not constant folding, and
// no output precedes the fault, so the failure happens with empty stdout. The
// expected runtime category is the protocol's normative classification of a "Solvik
// runtime arithmetic error" (protocol.md section 4.1), not a value read from the IUT.
mutable val big: Integer = 2147483647
big = big + 1
println(big)
