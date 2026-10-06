// Negative conformance test. Oracle derived from the same section 21.2 sentence quoted by
// SOL-TCK-0122, which names the empty block first among the invalid shapes: "An empty
// block, a block ending in a local declaration, and a block ending in an assignment are
// invalid in expression position and do not acquire an implicit `Unit` result".
// An empty block has no tail expression to supply a result, so SEM_BLOCK_RESULT_REQUIRED
// (SOLV-SEM-041) is the required diagnostic. This test shares that expectation with its
// siblings on purpose: the expectation is one specification rule exercised on three
// distinct shapes, not three independently derived byte streams.
var invalid = {
}
print("EXECUTED-INVALID")
