// Solvik TCK SOL-TCK-0502
// A typed mutable declaration is reassigned and the assigned value is observed.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `var mutable` declares a mutable binding/property.
//
var mutable x: Integer = 3
x = 4
print("m" .. x)
