// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// "Earlier statements execute in source order, and a local declared inside the block is
// visible to later items in that block". The block declares `first`, uses it in a second
// declaration, and the tail expression uses both; `outer` is visible going in.
// first = 1, second = first + outer = 1 + 4 = 5, tail = second + 1 = 6, so the expected
// stdout is exactly "6".
val outer = 4
val v = {
    val first = 1
    val second = first + outer
    second + 1
}
print(v)
