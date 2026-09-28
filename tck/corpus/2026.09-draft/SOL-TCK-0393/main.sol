// Solvik TCK SOL-TCK-0393
// The break inside the scope block exits the enclosing range loop, so the loop body does not run to completion and only the post-loop text appears.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A scope block is neither a loop nor a function boundary: `break`, `continue`, and `return` inside it apply to the enclosing loop or function.
//
for (i in 1...5) {
    {
        break
    }
}
print("sbdone")
