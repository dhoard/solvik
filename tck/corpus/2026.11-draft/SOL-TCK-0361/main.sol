// Solvik TCK SOL-TCK-0361
// A regex case on an Integer switch value is not a String switch value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Regex cases require a `String` switch value.
//
var x: Integer = 1
switch (x) {
    case regex r"1" {
        print("one")
    }
    default {
    }
        print("d")
}
print("EXECUTED-INVALID")
