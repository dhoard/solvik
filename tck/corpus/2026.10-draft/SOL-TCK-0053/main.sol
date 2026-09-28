// Oracle derived by hand from LANGUAGE_SPEC section 17, verbatim:
//   "`break` and `continue` are valid only inside a loop."
// Obligation: a top-level `break` with no enclosing loop must be rejected before
// execution, so no output is produced. The rejection is a semantic rule about enclosing
// construct, so it is asserted at the semantic diagnostic family. The specification
// states the rule but names no stable code for it, and the code the current
// implementation emits is not adopted as normative (see ORACLE_REVIEW.md), so only the
// family is portable.
print("reach")

break
