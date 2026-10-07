// Solvik TCK SOL-TCK-0409
// A String static initializer for an Integer property pins SOLV-TYPE-001.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
class C {
    var static mutable n: Integer = "wrong"

    C() {
    }
}
print("EXECUTED-INVALID")
