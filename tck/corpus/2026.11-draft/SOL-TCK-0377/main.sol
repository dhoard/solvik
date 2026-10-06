// Solvik TCK SOL-TCK-0377
// Both a scalar and a String are assignable to Any and print their stored values; the is test shows the dynamic type remains inspectable.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `Any` is the sole top type for every non-null Solvik value, including every class, interface, and enum value.
//
var a: Any = 42
var s: Any = "hi"
print(a)
print("|")
print(s)
print("|")
print(a is Integer)
