// Oracle derived from LANGUAGE_SPEC section 11's operation table, which gives verbatim:
//   "`Stack<T>`: `val isEmpty: Boolean`, `val size: Integer`, `func push(element: T)`,
//    `func peek(): T`, `func pop(): T`, `func clear()`."
// and: "A call with no value arguments constructs an empty collection
// (`List<Integer>()`)." A `Stack` is a last-in first-out collection, which is what the
// peek/pop pair reports below.
//
// Expected bytes derived by hand from the program text and those sentences:
//   * the stack is constructed empty, so the leading isEmpty is true;
//   * push declares no return type, so it is Unit-returning and used only as a
//     statement; after two pushes size is 2;
//   * peek returns T without removing, so it reports the element pushed last, 2;
//   * pop returns T and removes it, so it also reports 2, and size is then 1;
//   * one element remains, so the final isEmpty is false.
// Printed values in order: isEmpty, size, peek, pop, size, isEmpty
//   -> `true 2 2 2 1 false`.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
val frames = Stack<Integer>()
print(frames.isEmpty)
print(" ")
frames.push(1)
frames.push(2)
print(frames.size)
print(" ")
print(frames.peek())
print(" ")
print(frames.pop())
print(" ")
print(frames.size)
print(" ")
print(frames.isEmpty)
