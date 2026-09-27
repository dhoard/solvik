// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,
// verbatim: "Every normally completing case body, including `default`, must end in a tail
// expression; statements may precede it." The matched case body ends in a local
// declaration, which section 21.3 states is a statement and never a tail expression, so
// this normally completing path reaches no result.
// Section 21.9 gives SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041 with the primary span
// "offending block or case body", which is exactly this position.
val n = 1
val m = switch (n) {
    case 1:
        val q = 1
    default:
        "other"
}
print("EXECUTED-INVALID")
