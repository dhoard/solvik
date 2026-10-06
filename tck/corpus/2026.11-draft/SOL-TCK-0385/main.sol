// Solvik TCK SOL-TCK-0385
// An abstract class is not constructible, so its constructor call is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An `abstract class` is not constructible: naming it as a constructor is `SOLV-SEM-028`, and a program must construct one of its subtypes instead.
//
abstract class Shape {
    Shape() {
    }
}
var s = Shape()
print("EXECUTED-INVALID")
