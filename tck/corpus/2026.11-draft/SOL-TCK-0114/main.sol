// Solvik TCK SOL-TCK-0114
// `find` returns `RegexMatch?` per the section 14 API and `value` is declared on the non-null `RegexMatch`, so the access needs a non-null receiver that the analyzed type withholds. The manifest asserts the TYPE family and no code: the quoted sentence is the assignability rule, whose vocabulary forces a type-checking phase, but no code in the registry covers member access on a nullable receiver. SOL-TCK-0108 is the positive control, since it narrows with `if (m != null)` first and reads the same field successfully.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `null` is assignable only to nullable types. If `S` is a subtype of `T`, then `S` is assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null `T`.
//
var r = Regex(r#"\d+"#)
var m = r.find("ab12y")
print(m.value)
