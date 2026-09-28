// Positive conformance test: `..` renders any operand through toString, including null,
// and is left-associative over more than two operands.
// Oracle derived by hand from LANGUAGE_SPEC section 3, verbatim: "`..` concatenates:
// both operands are rendered through `toString` and the result is always `String`, so
// `1 .. "x"` is `"1x"` and `"x" .. null` is `"xnull"`", together with "it is
// left-associative". The spec gives the `null` case literally as `"xnull"`. For the
// chain `1 .. 2 .. 3`, left-associativity makes it `(1 .. 2) .. 3`: the first
// concatenation yields the String `12`, which renders through toString unchanged, and
// the second appends `3`, giving `123`. `print` appends no separator (section 5), so
// the expected bytes are `xnull|123`. No value was read from the IUT.
print("x" .. null)
print("|")
print(1 .. 2 .. 3)
