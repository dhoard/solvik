// Solvik TCK SOL-TCK-0468
// A binding of a one-parameter function type is initialized from a method that also reads its receiver, and calling it at the declared type formats the argument
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Reading an instance method without calling it produces a bound method value:
//   - The method's implicit receiver does not appear in the function type.
//   - It never reports a top-level function reference or a bound reference to a declared instance method, because this revision accepts both. Section 3 and section 23.4 retain their existing bare-member-read rejections unchanged.
//
class Formatter {
    func format(value: Integer): String {
        return "v" .. value.toString()
    }
}

val formatter = Formatter()
val operation: func(Integer): String = formatter.format
print(operation(42))
