// Solvik TCK SOL-TCK-0506
// A modifier-first declaration is a parse error; the keyword must come first.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `mutable` is a modifier on the declaration, never a binding kind of its own, so `var mutable` is the complete form and a bare `mutable` is a compile-time error.
//
mutable var x = 1
print("EXECUTED-INVALID")
