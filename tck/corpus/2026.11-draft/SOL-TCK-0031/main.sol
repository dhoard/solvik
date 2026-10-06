// Positive nullability conformance test: narrowing must be real, not cosmetic.
// Oracle derived by hand from LANGUAGE_SPEC section 5, which requires flow-sensitive
// narrowing and annotates the narrowed use as `// name is String here`. This test makes
// the narrowing load-bearing: `greet` is declared to take a NON-NULL `String`, so the
// call `greet(nn)` inside `if (nn != null)` type-checks only because narrowing actually
// changed the analyzed type of `nn` from `String?` to `String`. SOL-TCK-0032 asserts the
// converse -- the same call outside a narrowing context is rejected -- so the pair
// proves narrowing both permits and, absent narrowing, forbids the use.
// Expected bytes: `Doug`.
func greet(s: String): String {
    return s
}

var nn: String? = "Doug"
if (nn != null) {
    print(greet(nn))
}
