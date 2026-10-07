// Solvik TCK SOL-TCK-0106
// The sentence fixes only that construction accepts raw strings, so the test uses the same pattern under both spellings and requires them to agree; the raw form is what the sentence licenses and the escaped form is the control that shows the pattern itself behaves. The doubled backslash inside the ordinary string is the string-escape convention of section 1, not a claim about regex escaping.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Regex construction accepts raw strings
//
var raw: Regex = Regex(r#"\d+"#)
print(raw.matches("77"))
print(" ")
var escaped: Regex = Regex("^\\d+$")
print(escaped.matches("77"))
