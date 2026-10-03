// Solvik TCK SOL-TCK-0353
// The pre-test loop prints 0, 1, and 2; the condition is checked before each body run.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `while` is a pre-test loop.
//
mutable val i: Integer = 0
while (i < 3) {
    print(i)
    i = i + 1
}
