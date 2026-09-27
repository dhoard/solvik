// Solvik TCK SOL-TCK-0110
// Assertion strength: the sentence names the condition and its outcome ("are rejected") but no stable code, and the required-diagnostics registry has no Regex row at all, so pinning a code would mean inventing one. The sentence's vocabulary also forces no analysis phase, so the manifest asserts a rejection with neither code nor family rather than guessing TYPE. Stated scope limit: the specification names no SOLV-* code for this rule.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.
//
print(Regex(r#"(a)\1"#).matches("aa"))
