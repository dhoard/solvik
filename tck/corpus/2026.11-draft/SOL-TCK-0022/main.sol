// Positive conformance test: `;` separates two constructs written on one physical
// line. Oracle derived by hand from LANGUAGE_SPEC section 16, verbatim: "The
// semicolon is a separator, not a terminator. It may separate two constructs
// written on the same physical line:" -- section 16's own example writes two
// `var` declarations and a call on one line. Both bindings must enter the same
// scope exactly as if a newline had separated them (SOL-TCK-0286 is that arm), so
// the two bindings are read back and the expected stdout is their values joined
// by `-`, i.e. the bytes `1-2`. The `..` rendering used for the join is
// separately owned by REQ-0001; here it is only the observation vehicle. A
// line-final `;` is a different rule and a rejection, asserted by SOL-TCK-0496.
var x = 1; var y = 2
print(x .. "-" .. y)
