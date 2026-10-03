// Solvik TCK SOL-TCK-0390
// The `mutable val` local is reassigned and the assigned value is observed.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `mutable val` declares a mutable binding/property.
//
mutable val x: Integer = 1
x = 2
print("var" .. x)
