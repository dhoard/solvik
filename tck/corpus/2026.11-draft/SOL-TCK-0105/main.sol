// Solvik TCK SOL-TCK-0105
// Two patterns isolate what 'complete input' must mean. `^\\d+$` is anchored, so its verdict is the same under either reading; `\\d+` is not anchored and is therefore the discriminating case -- a prefix-matching engine would answer true for "12a", which this oracle records as false. Only `print` is used, because the separator of `println` is not specified. Each of the four verdicts is derived independently from the sentence.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `matches` requires the complete input to match.
//
var digit = Regex(r#"^\d+$"#)
print(digit.matches("123"))
print(" ")
print(digit.matches("12a"))
print(" ")
var bare = Regex(r#"\d+"#)
print(bare.matches("12a"))
print(" ")
print(bare.matches("123"))
