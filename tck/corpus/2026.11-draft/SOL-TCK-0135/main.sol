// Negative conformance test. Oracle derived by hand from the same section 21.7 sentence as
// SOL-TCK-0134: the join of an `Integer` branch and a `Long` branch is "`Number`, not
// `Long`". Assigning that
// construct to a `Long` local therefore requires a widening the specification explicitly
// refuses to introduce at a join, so the program must be rejected.
// This rejection is the discriminating half of the join rule: an implementation applying
// numeric promotion would infer `Long`, accept this program, and pass SOL-TCK-0134 while
// contradicting the sentence quoted above.
// Asserted as a bare rejection: the specification names SOLV-TYPE-001 for a *static*
// declaration initializer that is not assignable to its declared type, not for a local
// initializer, so no code is mandated at this particular site.
val joined: Long = if (true) {
    1
}
else {
    1L
}
print("EXECUTED-INVALID")
