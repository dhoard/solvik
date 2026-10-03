// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.8,
// verbatim: "Block, `if`, and `switch` expressions are accepted wherever the grammar
// accepts an expression ... including local initializers, assignment right-hand sides, call
// arguments, explicit `return` values, operands and nested expression constructs".
// Four of those named contexts appear here, each producing a fixed value: assignment
// right-hand side yields 10, call argument yields "n", explicit return yields "nonzero",
// and a nested construct yields 2. Expected stdout is exactly "10nnonzero2".
mutable val score: Integer = 0
score = if (true) {
    10
} else {
    0
}
print(score)
print(if (false) {
    "d"
} else {
    "n"
})
func classify(value: Integer): String {
    return switch (value) {
        case 0:
            "zero"
        default:
            "nonzero"
    }
}
print(classify(5))
val nested = if (true) {
    if (false) {
        1
    } else {
        2
    }
} else {
    3
}
print(nested)
