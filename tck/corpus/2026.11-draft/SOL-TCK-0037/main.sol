// Positive nullability conformance test: the matched control for SOL-TCK-0032.
// Same program shape, but with NO write to `mv` between the `mv != null` test and the
// use, so the narrowing is valid and `greet(mv)` -- whose parameter is a non-null
// `String` -- must type-check. LANGUAGE_SPEC section 5 requires flow-sensitive narrowing
// and annotates the narrowed use as `// name is String here`.
// Together with SOL-TCK-0032 this isolates the invalidating-write rule to one variable:
// identical except for the write, one accepted and one rejected.
// Expected bytes: `a`.
func greet(s: String): String {
    return s
}

mutable val mv: String? = "a"
if (mv != null) {
    print(greet(mv))
}
