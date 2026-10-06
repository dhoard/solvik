// Solvik TCK SOL-TCK-0500
// A plain `var` local is declared and read back with no reassignment.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `var` declares an immutable binding/property.
//
var x = 1
print("u" .. x)
