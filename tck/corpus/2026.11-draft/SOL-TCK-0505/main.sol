// Solvik TCK SOL-TCK-0505
// A nested block cannot write to an outer immutable binding.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - a binding is immutable unless `mutable` follows it
//
var x: Integer = 10
{
    x = 20
}
print("EXECUTED-INVALID")
