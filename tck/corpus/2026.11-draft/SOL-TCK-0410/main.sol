// Solvik TCK SOL-TCK-0410
// A second default is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A switch contains at most one `default`, and it must be last.
//
val x = 1
switch (x) {
    case 1 {
        print("one")
    }
    default {
        print("d")
    }
    default {
        print("d2")
    }
}
print("EXECUTED-INVALID")
