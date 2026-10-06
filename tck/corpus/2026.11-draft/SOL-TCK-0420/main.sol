// Solvik TCK SOL-TCK-0420
// A non-null function-typed static property is initialized to `null`, which the non-null assignment rule refuses and section 7 pins to the assignment diagnostic.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `null` is assignable only to nullable types. If `S` is a subtype of `T`, then `S` is assignable to `T?` and `S?` is assignable to `T?`; `S?` is not assignable to non-null `T`.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
class Holder {
    static var operation: func(Integer): String = null
}
print("EXECUTED-INVALID")
