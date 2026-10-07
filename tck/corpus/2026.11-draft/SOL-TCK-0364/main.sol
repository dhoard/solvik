// Solvik TCK SOL-TCK-0364
// Binding the class name to a local uses it as a value, which pins SOLV-TYPE-016.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - In particular `var c: Counter = Counter` and a read through an instance such as `instance.limit` are rejected.
//
class Counter {
    var static mutable n: Integer = 0

    Counter() {
    }
}
var c: Any = Counter
print("EXECUTED-INVALID")
