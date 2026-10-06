// Negative conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "A block expression introduces one lexical scope ... a local declared inside
// the block is visible to later items in that block and nowhere outside it."
// The block evaluates correctly and yields its tail value, but the following statement
// names the block-local `inner` outside the block, where nothing is visible. Section 4's
// reference rule names the diagnostic: a bare name that resolves to no local, parameter,
// function, or top-level declaration "is `SOLV-RESOL-001`".
var v = {
    var inner = 1
    inner
}
print(inner)
