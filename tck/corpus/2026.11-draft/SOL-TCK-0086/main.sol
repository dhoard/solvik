// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "For `List`, `Set`, and `Stack`, the value arguments are the initial elements and
//    each must be assignable to the element type; `Set` keeps only the first of equal
//    elements."
//
// `List<Integer>(`"x"`)` supplies an initial element whose type is `String`, which is not
// assignable to the element type `Integer`, so the quoted sentence requires rejection.
// Unlike the constructions rejected by SOL-TCK-0084 and SOL-TCK-0085, the phase here is
// forced by the specification's own vocabulary: "assignable" is a typing relation, and an
// implementation cannot decide element assignability in any phase other than type
// checking. That is why this manifest asserts the diagnostic family where the other two
// deliberately assert nothing beyond "rejected at compile time".
// The specification names no stable code for element-assignability failures in a
// collection construction -- the codes it does name for SOLV-TYPE-001 belong to
// assignment-to-declared-type and message-argument rules elsewhere -- so no code is
// asserted, and TCK.md section 6 forbids promoting the implementation's enum entry to
// normative status. Sentinel per TCK.md section 10: the print would be observable if this
// invalid construction were accepted.
var nums: List<Integer> = List<Integer>("x")
print(nums.size)
