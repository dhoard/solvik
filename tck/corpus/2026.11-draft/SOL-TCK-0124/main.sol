// Negative conformance test. Oracle derived from the section 21.2 sentence quoted by
// SOL-TCK-0122, plus section 21.3: "a terminal assignment is a statement and never a tail
// expression". The block's last item assigns to an outer variable, so the block never
// reaches a tail expression and must be rejected with SEM_BLOCK_RESULT_REQUIRED
// (SOLV-SEM-041) rather than acquiring a `Unit` result.
mutable val target = 0
val invalid = {
    target = 5
}
print("EXECUTED-INVALID")
