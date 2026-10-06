// Solvik TCK SOL-TCK-0378
// Each in-range value converts to the named target type through an explicit built-in call and prints its converted value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Every other conversion, including all narrowing and every precision-losing conversion, uses an explicit built-in type call such as `Long(value)`; an out-of-range integral conversion raises a Solvik runtime arithmetic error and an out-of-range constant conversion is a compile-time error.
//   - `Byte` and `Short` values use explicit conversion.
//
var b: Byte = Byte(1)
var s: Short = Short(2)
var i: Integer = Integer(3L)
var l: Long = Long(4)
print(b)
print("|")
print(s)
print("|")
print(i)
print("|")
print(l)
