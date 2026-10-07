// Solvik TCK SOL-TCK-0322
// The parameter has no type; the declaration is rejected before execution.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Parameter types must be explicit.
//
func f(a) {
    print(a)
}
f(1)

print("EXECUTED-INVALID")
