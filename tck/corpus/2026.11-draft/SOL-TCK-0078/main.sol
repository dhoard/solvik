// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "`Map` takes `key: value` entries, each key assignable to `K` and each value
//    assignable to `V`; a repeated key keeps its position and takes the latest value."
//   "A call with no value arguments constructs an empty collection (`List<Integer>()`)."
// and gives the operation table:
//   "`Map<K, V>`: `var isEmpty: Boolean`, `var size: Integer`, `func put(key: K, value: V)`,
//    `func get(key: K): V`, `func containsKey(key: K): Boolean`,
//    `func remove(key: K): Boolean`, `func clear()`."
//
// Expected bytes derived by hand from the program text and those sentences:
//   * the construction is empty, so the leading isEmpty is true;
//   * put declares no return type, so it is Unit-returning and is used only as a
//     statement; after two distinct keys size is 2;
//   * get returns V, so get("b") prints 2;
//   * the repeated key "a" keeps its position and takes the latest value, so get("a")
//     prints 7 and the entry count is unchanged at 2;
//   * containsKey returns Boolean and "z" was never put, so it prints false;
//   * remove returns Boolean and "b" is present, so remove("b") prints true;
//   * containsKey("b") is then false and size is 1.
// Printed values in order: isEmpty, size, get("b"), get("a"), size, containsKey("z"),
// remove("b"), containsKey("b"), size -> `true 2 2 7 2 false true false 1`.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
var scores: Map<String, Integer> = Map<String, Integer>()
print(scores.isEmpty)
print(" ")
scores.put("a", 1)
scores.put("b", 2)
print(scores.size)
print(" ")
print(scores.get("b"))
print(" ")
scores.put("a", 7)
print(scores.get("a"))
print(" ")
print(scores.size)
print(" ")
print(scores.containsKey("z"))
print(" ")
print(scores.remove("b"))
print(" ")
print(scores.containsKey("b"))
print(" ")
print(scores.size)
