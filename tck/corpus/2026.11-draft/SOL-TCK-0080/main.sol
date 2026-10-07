// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim, immediately after
// the `List<T>` operation table:
//   "An invalid index raises a Solvik runtime bounds error."
//
// Why this program reaches that sentence and nothing else:
//   * `List<Integer>(10, 20, 30)` is the explicit type-argument form the section gives
//     (`List<Integer>(1, 2, 3)`), so the construction holds three elements;
//   * valid indices are 0, 1, 2, so `get(3)` is an invalid index and the sentence
//     requires a runtime failure rather than a value;
//   * section 11 gives `get(index: Integer): T`, so the call is well-typed: the program
//     is statically legal and the failure cannot be attributed to a different rule.
// Deliberate scope limit: the print is the only output the program could produce, and it
// never completes, so the expected stdout stream is empty. The specification names no
// stable diagnostic code for this failure, so the manifest asserts the protocol runtime
// category (protocol.md section 4.1), not a SOLV-* code, and records no exit status: the
// specification says only that a failure is raised, so asserting a particular process
// status would invent behavior. Sentinel per TCK.md section 10: any successful output
// would contradict the oracle.
var nums: List<Integer> = List<Integer>(10, 20, 30)
print(nums.get(3))
