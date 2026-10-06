// Solvik TCK SOL-TCK-0501
// A typed immutable declaration binds the annotated name and reads it back.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The canonical forms are `var name = expression`, `var name: Type = expression`, `var mutable name = expression`, and `var mutable name: Type = expression`
//
var x: Integer = 2
print("t" .. x)
