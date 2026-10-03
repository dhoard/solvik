// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.7,
// verbatim: "If exactly one branch can complete normally, its result type is the
// construct's result type." and "`Unit` participates in the join as any other non-null
// value type."
// Section 21.4's example gives the shape: the `else` completes abruptly via `return`, so
// only the taken branch is a normally completing result and its type String is the
// construct's type. Expected stdout is "[x]" for the branch that completes normally; the
// abrupt path returns from the enclosing function before the outer print, and this test
// exercises only the normal path so the value is fixed.
func pick(k: Boolean): String {
    return if (k) {
        "x"
    } else {
        return "abrupt"
    }
}
print("[")
print(pick(true))
print("]")
