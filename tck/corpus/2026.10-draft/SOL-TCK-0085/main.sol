// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "`Map` takes `key: value` entries, each key assignable to `K` and each value
//    assignable to `V`; a repeated key keeps its position and takes the latest value.
//    A `key: value` entry is meaningful only in a `Map` construction, and a positional
//    value is not valid in a `Map` construction."
//
// This program is exactly the last clause: `Map<String, Integer>("a")` supplies a
// positional value where only a `key: value` entry is valid. The quoted sentence requires
// rejection, and the construction otherwise satisfies every other section-11 rule -- the
// type arguments are written and the entry count is not itself restricted -- so the
// rejection is attributable to the positional-value clause rather than to inference or
// element assignability.
// Deliberate scope limit: the manifest asserts a compile-time rejection and asserts NO
// code and NO family. The clause says only that a positional value "is not valid"; it
// names no stable code and does not say which analysis phase reports it. Unlike the
// element-assignability tests, no word in this sentence forces a phase: "not valid in a
// `Map` construction" is a statement about the construction form, which an implementation
// may legitimately reject while checking the constructor's parameter types or while
// checking assignability of the entry. Pinning a family would therefore test an
// implementation choice. TCK.md section 6 likewise forbids promoting an implementation
// enum entry to normative status. Sentinel per TCK.md section 10: the print would be
// observable if this invalid construction were accepted.
val scores = Map<String, Integer>("a")
print(scores.size)
