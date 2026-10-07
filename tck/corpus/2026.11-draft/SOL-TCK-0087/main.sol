// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "Generic type arguments are invariant. The initial runtime uses erasure while
//    preserving complete compile-time checking. A runtime type test against a
//    non-reified type argument is a compile-time error."
//
// `nums is List<Integer>` is a runtime type test whose type argument `Integer` is
// non-reified under the erasure the same paragraph states, so the third sentence requires
// a compile-time error.
// Deliberate scope limit: the manifest asserts a compile-time rejection and asserts NO
// code and NO family. The sentence ends at "is a compile-time error": it names no stable
// code, and unlike the element-assignability tests its words do not force an analysis
// phase. Deciding that `Integer` is non-reified is a property of the type argument itself,
// which a conforming implementation may legitimately establish while resolving the type
// argument or while checking the type test, so pinning a family would test an
// implementation choice rather than the specification. TCK.md section 6 likewise forbids
// promoting an implementation enum entry to normative status. Sentinel per TCK.md
// section 10: the print would be observable if this invalid test were accepted.
var nums: List<Integer> = List<Integer>(1, 2)
print(nums is List<Integer>)
