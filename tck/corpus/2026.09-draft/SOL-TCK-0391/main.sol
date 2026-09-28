// Solvik TCK SOL-TCK-0391
// A declared function cannot see the implicit main's top-level local.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - a top-level `val`/`var` is therefore a local of the implicit main, not a global.
//
val x = 1
func f(): Integer {
    return x
}
print("EXECUTED-INVALID")
