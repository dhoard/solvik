// Solvik TCK SOL-TCK-0365
// Reading a static member through an instance is the second rejected form.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - In particular `var c: Counter = Counter` and a read through an instance such as `instance.limit` are rejected.
//
class Counter {
    var static mutable n: Integer = 0

    Counter() {
    }
}
var c: Counter = Counter()
print(c.n)

print("EXECUTED-INVALID")
