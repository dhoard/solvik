// Solvik TCK SOL-TCK-0411
// A default that is not the last clause is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A switch contains at most one `default`, and it must be last.
//
var x: Integer = 1
switch (x) {
    default {
        print("d")
    }
    case 2 {
        print("two")
    }
}
print("EXECUTED-INVALID")
