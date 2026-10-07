// Solvik TCK SOL-TCK-0504
// The inner mutable shadow is reassigned and printed; the outer immutable binding is unchanged.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A nested block may shadow an outer declaration.
//
var x: Integer = 10
{
    var mutable x: Integer = 20
    x = 30
    print("s" .. x)
}
print("o" .. x)
