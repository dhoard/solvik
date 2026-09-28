// Solvik TCK SOL-TCK-0390
// The var local is reassigned and the assigned value is observed.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `var` declares a mutable binding/property.
//
var x: Integer = 1
x = 2
print("var" .. x)
