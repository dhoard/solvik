// Solvik TCK SOL-TCK-0395
// Assigning to the implicitly immutable loop variable is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The loop variable is an implicitly declared immutable `Integer` binding scoped to the loop body.
//
for (i in 1...3) {
    i = 0
}
print("EXECUTED-INVALID")
