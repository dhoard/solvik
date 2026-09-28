// Solvik TCK SOL-TCK-0407
// A user class may not redeclare the built-in Byte type.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Redeclaring a built-in function or type is rejected by the existing declaration checks.
//   - Built-in types and functions are always visible unqualified and cannot be shadowed by a module or alias name.
//
class Byte {
    Byte() {
    }
}
print("EXECUTED-INVALID")
