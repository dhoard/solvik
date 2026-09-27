// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim, immediately after
// the `Map<K, V>` operation table:
//   "`get` for a missing key raises a Solvik collection error."
//
// Why this program reaches that sentence and nothing else:
//   * `Map<String, Integer>()` is the empty-construction form the section gives
//     ("A call with no value arguments constructs an empty collection"), so the map has
//     no entries and no key can be present;
//   * `get(key: K): V` is the table's signature, so `get("absent")` is well-typed and
//     statically legal: the failure cannot be attributed to another rule;
//   * the map is empty, so "absent" is necessarily a missing key and the quoted sentence
//     requires a runtime failure rather than a value.
// Deliberate scope limit: the specification names no stable diagnostic code for a Solvik
// collection error, so the manifest asserts the protocol runtime category
// (protocol.md section 4.1) and records no process exit status. The print cannot
// complete, so the expected stdout stream is empty. Sentinel per TCK.md section 10.
val scores = Map<String, Integer>()
print(scores.get("absent"))
