// Solvik TCK SOL-TCK-0354
// A bare return in a value-returning function is the forbidden form.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `return;` is valid only in a function declared without a return type; `return value` requires the value to be assignable to the declared return type.
//
func f(): Integer {
    return
}
print(f())

print("EXECUTED-INVALID")
