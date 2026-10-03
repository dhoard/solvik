// Solvik TCK SOL-TCK-0358
// With no executable top-level statement the program has no entry point, prints nothing, and exits 0.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A program with no executable top-level statements has no entry point and does nothing.
//
func f(): Integer {
    return 1
}
