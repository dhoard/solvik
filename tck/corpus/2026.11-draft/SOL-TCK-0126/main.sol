// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A standalone scope block remains a statement block". In statement position a brace
// block is therefore not a value: it contributes nothing, and its only effect is running
// its statements in source order.
// `x` starts at 10; the block declares a local `d` and assigns x = x - d = 10 - 7 = 3, so
// the expected stdout is exactly "[3]". The brackets are the test's own contribution, not
// a specification claim: another test in this corpus already derives the bare bytes "3"
// from an unrelated rule, and an oracle that coincides with a sibling's tests nothing
// about ordering, so bracketing both marks this stream and keeps the two derivations
// independently checkable. If the block were treated as an expression its value
// would be required, and section 21.2 makes that a compile-time error instead.
mutable val x = 10
{
    val d = 7
    x = x - d
}
print("[")
print(x)
print("]")
