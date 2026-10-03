// Solvik TCK SOL-TCK-0396
// The loop variable is not visible after the loop.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The loop variable is an implicitly declared immutable `Integer` binding scoped to the loop body.
//
for (i in 1...3) {
    print(i)
}
print(i)

print("EXECUTED-INVALID")
