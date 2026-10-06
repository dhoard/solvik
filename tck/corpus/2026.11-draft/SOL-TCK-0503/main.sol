// Solvik TCK SOL-TCK-0503
// A mutable binding is reassigned repeatedly, including from its own previous value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Reassignment uses ordinary `=` assignment on an existing binding and is legal only when the binding was declared `mutable`
//
var mutable c = 0
c = 1
c = 2
c = c + 1
print("chain" .. c)
