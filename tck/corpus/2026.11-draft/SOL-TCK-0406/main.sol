// Solvik TCK SOL-TCK-0406
// A user function may not redeclare the built-in print.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Built-in types and functions are always visible unqualified and cannot be shadowed by a module name.
//
func print(x: Integer) {
}
print(1)

print("EXECUTED-INVALID")
