// Negative nullability conformance test: narrowing is invalidated by a write.
// Oracle derived by hand from LANGUAGE_SPEC section 5, verbatim: "Narrowing is permitted
// only when the analyzed value cannot be written or invalidated along that control-flow
// path. A write to a `var` invalidates its prior narrowing."
//
// The oracle is made load-bearing rather than cosmetic: `greet` is declared to take a
// NON-NULL `String`, so `greet(mv)` inside the `mv != null` branch type-checks only while
// the narrowing holds. Writing `mv = null` on the path before the use must invalidate that
// narrowing, so the program must be REJECTED. SOL-TCK-0037 is the matched control that
// omits the write and must be ACCEPTED; reading the pair together proves the rejection is
// caused by the invalidating write and not by some unrelated rule about `var` or `greet`.
// The specification names no stable code for this rejection (it states only that narrowing
// is not permitted), so the oracle asserts the diagnostic family, which is the protocol's
// closed taxonomy (protocol.md section 4.1); TCK.md section 6 forbids promoting the
// implementation's enum entry to normative status.
// Sentinel per TCK.md section 10; the compile-only phase proves non-execution.
func greet(s: String): String {
    return s
}

var mv: String? = "a"
if (mv != null) {
    mv = null
    print(greet(mv))
}
println("EXECUTED-INVALID")
