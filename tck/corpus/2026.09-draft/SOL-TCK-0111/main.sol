// Solvik TCK SOL-TCK-0111
// One construct per test, because a program mixing them would surface only the first rejection and silently stop covering the others. "ab" is chosen as the input that a lookaround-supporting engine would *match*, so an implementation that accepted the pattern cannot pass this manifest by accident.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Backreferences, lookaround, embedded flags, and engine-specific extensions are rejected.
//
print(Regex(r#"a(?=b)"#).matches("ab"))
