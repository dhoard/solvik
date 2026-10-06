// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.2:
// "A block expression introduces one lexical scope ... a local declared inside the block
// is visible to later items in that block and nowhere outside it."
// Two block expressions each declare a local named `s`, so the second `s` must not see or
// disturb the first: each block reads and updates the one outer variable, and each block's
// own `s` is the only `s` visible inside it. First block: total = 0+2 = 2. Second: the
// fresh `s` is 3, so total = 2+3 = 5. Printing after each block gives "2" then "5",
// so the expected stdout is exactly "25". A shared or leaked scope could not produce 5
// (it would produce 4 from `s + s`, or fail to compile).
var mutable total = 0
var a = {
    var s = 2
    total = total + s
    total
}
var b = {
    var s = 3
    total = total + s
    total
}
print(a)
print(b)
