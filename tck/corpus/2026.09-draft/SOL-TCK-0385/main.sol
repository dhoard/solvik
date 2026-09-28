// Solvik TCK SOL-TCK-0385
// A sealed class is abstract and cannot be constructed directly.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A `sealed class` is abstract and may be extended only by declarations in the same physical source file.
//
sealed class Shape {
    Shape() {
    }
}
val s = Shape()
print("EXECUTED-INVALID")
