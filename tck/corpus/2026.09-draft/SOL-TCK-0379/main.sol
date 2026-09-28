// Solvik TCK SOL-TCK-0379
// 2147483648 is one past the signed 32-bit maximum, so the constant conversion is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every other conversion, including all narrowing and every precision-losing conversion, uses an explicit built-in type call such as `Long(value)`; an out-of-range integral conversion raises a Solvik runtime arithmetic error and an out-of-range constant conversion is a compile-time error.
//
val i: Integer = Integer(2147483648L)
print("EXECUTED-INVALID")
