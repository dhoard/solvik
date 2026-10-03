// Solvik TCK SOL-TCK-0355
// A value returned from a value-less function is not assignable to its Unit result.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `return;` is valid only in a function declared without a return type; `return value` requires the value to be assignable to the declared return type.
//
func f() {
    return 1
}
f()
print("EXECUTED-INVALID")
