// Solvik TCK SOL-TCK-0406
// A user function may not redeclare the built-in print.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Redeclaring a built-in function or type is rejected by the existing declaration checks.
//   - Built-in types and functions are always visible unqualified and cannot be shadowed by a module or alias name.
//
func print(x: Integer) {
}
print(1)

print("EXECUTED-INVALID")
