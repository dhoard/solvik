// Negative LEXICAL conformance test: an unsupported escape sequence.
// Oracle derived by hand from LANGUAGE_SPEC section 15, verbatim: "They support exactly
// `\\`, `\"`, `\n`, `\r`, `\t`, `\0`, and `N` (`\N`). Any other escape is a lexical
// error." The
// program uses `\q`, which is not in that closed list, so the specification determines
// both that the program is rejected and that the rejection is LEXICAL.
//
// Deliberate scope limit: the specification names NO stable diagnostic code for this or
// any lexical error -- LANGUAGE_SPEC.md contains no SOLV-LEX-* code anywhere. TCK.md
// section 6 forbids promoting an implementation enum entry to normative status, so this
// manifest asserts only the diagnostic family, which is the protocol's own closed
// taxonomy (protocol.md section 4.1), and the requirement is recorded with
// `diagnosticNormative: false`. This is a genuine specification gap to raise, not
// something the TCK may paper over by adopting an implementation code.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
var s = "bad \q escape"
print(s)
println("EXECUTED-INVALID")
