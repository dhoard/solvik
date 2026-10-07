// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "A collection is constructed with a class-style call. The type arguments may be
//    written explicitly (`List<Integer>(1, 2, 3)`) or omitted to infer them from the
//    declared type of the left-hand side (`var names: List<String> = List("a", "b")`);
//    a construction that writes neither is a compile-time error."
//
// This program is exactly the third alternative: `List(1, 2)` writes no type argument and
// the binding carries no declared type, so there is no left-hand declared type to infer
// from. The quoted sentence requires a compile-time error, and no other rule in the
// section is implicated -- the argument list is well-formed for a `List` construction and
// the statement is otherwise legal -- so the rejection must be attributable to inference
// of the type arguments.
// Deliberate scope limit: the manifest asserts a compile-time rejection and deliberately
// asserts NO code and NO family. The specification's only requirement here is the words
// "is a compile-time error"; it names no stable code and never says which analysis phase
// performs the check. Pinning a family would additionally depend on an implementation
// choice: this program can legitimately be reported by type inference, by name resolution
// of the un-inferable type, or by both at once, and a conforming implementation is free to
// pick any of those. TCK.md section 6 likewise forbids promoting an implementation enum
// entry to normative status. Sentinel per TCK.md section 10: the print would be observable
// if this invalid construction were accepted.
var nums: Any = List(1, 2)
print(nums.size)
