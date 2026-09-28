// Solvik TCK SOL-TCK-0389
// The explicit `: Unit` spelling is accepted and the call runs the body's side effect.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A function's return type is written only when the function returns a value; a declaration that omits the return type returns no value and has type `Unit`. Writing `: Unit` explicitly is permitted but redundant.
//
func f(): Unit {
    print("unitok")
}
f()
