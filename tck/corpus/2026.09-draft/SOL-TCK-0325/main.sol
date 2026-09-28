// Solvik TCK SOL-TCK-0325
// A second top-level binding of the same name is a same-scope redeclaration.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Names use lexical scope. Redeclaration in the same scope is an error. A nested block may shadow an outer declaration.
//
val x = 1
val x = 2
print(x)

print("EXECUTED-INVALID")
