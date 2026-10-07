// Oracle derived from LANGUAGE_SPEC section 11's `List<T>` operation table, which gives
// verbatim:
//   "`List<T>`: `var isEmpty: Boolean`, `var size: Integer`, `func add(element: T)`,
//    `func get(index: Integer): T`, `func removeAt(index: Integer): T`,
//    `func set(index: Integer, element: T)`, `func clear()`."
// and: "A collection is constructed with a class-style call. The type arguments may be
// written explicitly (`List<Integer>(1, 2, 3)`)."
//
// Expected bytes derived by hand from the program text and that table:
//   * construction holds 3 elements, so the first `size` is 3;
//   * add/set take `element: T` and declare no return type, so they are Unit-returning
//     and are used only as statements -- section 11 gives them no value to print;
//   * get(3) returns T after appending 40, so it prints 40;
//   * set(0, 99) then get(0) prints 99;
//   * removeAt removes and returns one element: index 1 now holds 20, so 20 is printed;
//   * size starts at 3, grows to 4 by the append, and drops back to 3 when removeAt
//     removes an element, so the second size printed is 3;
//   * clear() leaves the list not-empty only if removal failed, so the final isEmpty is
//     true and the size printed just before clear is 3.
// Order of printed values: size, get(3), get(0), removeAt(1), size, isEmpty, isEmpty.
// That yields the exact stream `3 40 99 20 3 false true`.
// Executed as top-level statements (section 20; an explicit `func main` is SOLV-SEM-001).
// Uses print, so no platform line separator can enter the expected bytes.
var nums: List<Integer> = List<Integer>(10, 20, 30)
print(nums.size)
print(" ")
nums.add(40)
print(nums.get(3))
print(" ")
nums.set(0, 99)
print(nums.get(0))
print(" ")
print(nums.removeAt(1))
print(" ")
print(nums.size)
print(" ")
print(nums.isEmpty)
nums.clear()
print(" ")
print(nums.isEmpty)
