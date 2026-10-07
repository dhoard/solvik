// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "An empty block, a block ending in a local declaration, and a block ending in
// an assignment are invalid in expression position and do not acquire an implicit `Unit`
// result", illustrated by the spec's own `var invalid = { var local = 1 }`. Section 21.9
// names the stable code SEM_BLOCK_RESULT_REQUIRED = SOLV-SEM-041, whose primary span is
// the "offending block or case body". The trailing print is a sentinel only: a compile
// rejection prevents it from running.
var invalid: Nothing = {
    var local: Integer = 1
}
print("EXECUTED-INVALID")
