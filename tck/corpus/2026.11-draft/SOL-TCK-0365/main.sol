// Solvik TCK SOL-TCK-0365
// Reading a static member through an instance is the second rejected form.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The class name in that position is a receiver, not a value: it is legal only as the root of a static member reference, and a class name used anywhere else remains `SOLV-TYPE-016`.
//   - In particular `var c = Counter` and a read through an instance such as `instance.limit` are rejected.
//
class Counter {
    static var mutable n: Integer = 0

    Counter() {
    }
}
var c = Counter()
print(c.n)

print("EXECUTED-INVALID")
