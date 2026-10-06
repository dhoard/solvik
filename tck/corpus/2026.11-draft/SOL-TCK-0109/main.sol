// Solvik TCK SOL-TCK-0109
// The replacement carries all three spellings a capture-substituting engine would rewrite (`$1`, `$0`, `&`), so an implementation that substituted captures cannot satisfy this oracle by luck. A lone-backslash replacement is deliberately not used to make this point: that spelling already fails at the string-escape level in section 1 before `replace` is reached, so it would test the lexer rather than `replace`. The second statement covers the no-match case implied by the same sentence.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `replace` replaces all non-overlapping matches and treats the replacement as literal text
//
var r = Regex(r#"(\w+)"#)
print(r.replace("ab cd", "[$1] $0 &"))
print(" ")
print(Regex(r#"\s"#).replace("abc", "#"))
