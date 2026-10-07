// Solvik TCK SOL-TCK-0416
// The deferred `as?` spelling is not defined and is a parse error.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An unsuccessful `as` cast raises a Solvik runtime type error. Safe-cast syntax is deferred.
//
class A {
    A() {
    }
}
var a: A = A()
var b: A = a as? A

print("EXECUTED-INVALID")
