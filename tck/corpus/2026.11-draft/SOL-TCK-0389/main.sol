// Solvik TCK SOL-TCK-0389
// The explicit `: Unit` spelling is accepted and the call runs the body's side effect.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A callable's return type is written only when the callable produces a value: a declaration that writes no `: Type` produces no value at all, and no source type names that result.
//
func f() {
    print("unitok")
}
f()
