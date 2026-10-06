// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "For `List`, `Set`, and `Stack`, the value arguments are the initial elements and
//    each must be assignable to the element type; `Set` keeps only the first of equal
//    elements."
// and gives the operation table:
//   "`Set<T>`: `var isEmpty: Boolean`, `var size: Integer`, `func add(element: T): Boolean`,
//    `func contains(element: T): Boolean`, `func remove(element: T): Boolean`,
//    `func clear()`."
//
// Expected bytes derived by hand from the program text and those sentences:
//   * the construction lists 1, 2, 2, 3 and `Set` keeps only the first of equal
//     elements, so it holds {1,2,3} and size is 3;
//   * add returns Boolean, and 1 is already present, so add(1) is false;
//   * 9 is absent, so add(9) is true, and size becomes 4;
//   * contains(2) is true;
//   * remove returns Boolean and 2 is present, so remove(2) is true;
//   * contains(2) after that removal is false;
//   * the set still holds {1,3,9}, so the final isEmpty is false.
// Printed values in order: size, add(1), add(9), size, contains(2), remove(2),
// contains(2), isEmpty -> `3 false true 4 true true false false`.
// Note the element order is unspecified, which is exactly why every value asserted here
// is order-independent. Executed as top-level statements (section 20). Uses print, so no
// platform line separator can enter the expected bytes.
var ids = Set<Integer>(1, 2, 2, 3)
print(ids.size)
print(" ")
print(ids.add(1))
print(" ")
print(ids.add(9))
print(" ")
print(ids.size)
print(" ")
print(ids.contains(2))
print(" ")
print(ids.remove(2))
print(" ")
print(ids.contains(2))
print(" ")
print(ids.isEmpty)
