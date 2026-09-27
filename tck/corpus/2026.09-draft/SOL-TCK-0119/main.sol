// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2,
// verbatim: "A block expression introduces one lexical scope. Earlier statements execute
// in source order, and a local declared inside the block is visible to later items in
// that block and nowhere outside it." The section also gives both shapes used here:
//   val answer = { val base = 20; base + 22 }   -- "has type Integer and value 42"
//   val logged: Unit = { println("done") }      -- "the second has type Unit"
// `println` is replaced by `print` throughout this corpus so no platform line separator
// can enter the expected bytes (section 6 defines println's separator as the platform's).
// Ordering is fixed by source order: the Unit block runs at its declaration and emits
// "d", then the final print emits the Integer block's 42. Expected stdout is "d42".
val base = 20
val answer = {
    val inner = base
    inner + 22
}
val logged: Unit = {
    print("d")
}
print(answer)
