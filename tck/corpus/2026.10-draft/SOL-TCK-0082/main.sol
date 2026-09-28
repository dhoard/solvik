// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim, immediately after
// the `Stack<T>` operation table:
//   "`peek` and `pop` on an empty stack raise a Solvik collection error."
//
// This test covers the `pop` half of that sentence; SOL-TCK-0083 covers the `peek` half.
// Both are needed because the sentence names two operations and a single test cannot show
// that an implementation handles both rather than only the one it happened to reach.
// Why this program reaches that sentence and nothing else:
//   * `Stack<Integer>()` is the empty-construction form the section gives ("A call with
//     no value arguments constructs an empty collection"), so no push has occurred and
//     the stack is empty at the call;
//   * `pop(): T` is the table's signature, so the call is well-typed and statically
//     legal: the failure cannot be attributed to another rule.
// Deliberate scope limit: the specification names no stable diagnostic code for a Solvik
// collection error, so the manifest asserts the protocol runtime category (protocol.md
// section 4.1) and records no process exit status. The print cannot complete, so the
// expected stdout stream is empty. Sentinel per TCK.md section 10.
val frames = Stack<Integer>()
print(frames.pop())
