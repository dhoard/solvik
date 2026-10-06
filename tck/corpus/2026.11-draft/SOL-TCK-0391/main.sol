// Solvik TCK SOL-TCK-0391
// A declared function cannot see the implicit main's top-level local.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - a top-level `var`, whether or not it is `mutable`, is therefore a local of the implicit main, not a global.
//
var x = 1
func f(): Integer {
    return x
}
print("EXECUTED-INVALID")
