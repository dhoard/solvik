// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.4,
// verbatim: "The condition must be `Boolean`, exactly as for statement `if`." The condition
// here is an Integer, so the program is not well typed and must be rejected before
// execution.
// The expectation is a bare rejection on purpose. The specification states the requirement
// but names no diagnostic code for this site, and the implementation's own code for it does
// not appear anywhere in LANGUAGE_SPEC.md, so pinning one here would assert a choice the
// specification never made.
val v = if (7) {
    "yes"
} else {
    "no"
}
print("EXECUTED-INVALID")
