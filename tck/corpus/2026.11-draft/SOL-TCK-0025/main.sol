// Negative LEXICAL conformance test: an unterminated raw string.
// Oracle derived by hand from LANGUAGE_SPEC section 15, verbatim: "An unterminated raw
// string is a lexical error at its opening delimiter. The diagnostic must show the exact
// closing delimiter that was expected." The program opens r#" and never supplies the
// matching "#, so the specification determines both the rejection and its lexical
// category.
//
// Scope limit and why the required detail is NOT asserted: section 15 requires the
// diagnostic to *show the exact closing delimiter that was expected*, but human-readable
// diagnostic wording is not normative unless the specification says it is (TCK.md
// section 7), and section 15 constrains what the message must contain without fixing the
// message text or a code. Asserting either would let the TCK choose an observable the
// specification leaves open (TCK.md section 6.1), so only the lexical family is asserted.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
var s = r#"unterminated
print(s)
println("EXECUTED-INVALID")
