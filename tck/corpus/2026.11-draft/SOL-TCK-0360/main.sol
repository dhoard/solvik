// Solvik TCK SOL-TCK-0360
// A case label naming a runtime binding is not a compile-time constant.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Constant case expressions must be compile-time constants assignable to the switched value's type.
//
var x = 1
switch (x) {
    case x {
        print("same")
    }
    default {
    }
        print("default")
}
print("EXECUTED-INVALID")
