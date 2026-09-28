// Solvik TCK SOL-TCK-0112
// Same rationale as SOL-TCK-0111: "ABC" is what a flag-supporting engine would match, making the negative program maximally discriminating. The fourth named construct, engine-specific extensions, is left uncovered deliberately -- the phrase names a category rather than a construct, so writing a program for it would require inventing an example the specification does not give.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.
//
print(Regex(r#"(?i)abc"#).matches("ABC"))
