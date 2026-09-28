// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "`Map` takes `key: value` entries, each key assignable to `K` and each value
//    assignable to `V`; a repeated key keeps its position and takes the latest value."
//
// The construction writes the key "a" twice, with different values. The quoted clause
// gives two observable consequences for a repeated key:
//   * the entry count does not grow with the duplicate, so `size` counts distinct keys
//     and is 2 rather than 3;
//   * the key "takes the latest value", so `get("a")` returns 9, the value written last
//     for that key, not 1.
// "Keeps its position" is not asserted: section 11 gives no iteration order for `Map`, so
// no observable derived from position exists in this language revision, and asserting one
// would invent semantics the specification defers.
// Printed values in order: size, get("a"), get("b") -> `2 9 2`. The `get("b")` term
// confirms the non-repeated key is unaffected.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
val scores = Map<String, Integer>("a": 1, "b": 2, "a": 9)
print(scores.size)
print(" ")
print(scores.get("a"))
print(" ")
print(scores.get("b"))
