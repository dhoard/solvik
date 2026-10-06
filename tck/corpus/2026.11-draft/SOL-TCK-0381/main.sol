// Solvik TCK SOL-TCK-0381
// The value is outside the Integer range and is held in a mutable binding, so the conversion faults at run time with the arithmetic category.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every other conversion, including all narrowing and every precision-losing conversion, uses an explicit built-in type call such as `Long(value)`; an out-of-range integral conversion raises a Solvik runtime arithmetic error and an out-of-range constant conversion is a compile-time error.
//
var mutable l: Long = 2147483648L
var i: Integer = Integer(l)
print(i)
