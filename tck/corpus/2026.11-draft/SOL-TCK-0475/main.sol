// Solvik TCK SOL-TCK-0475
// A counted receiver expression is evaluated once at creation and never again on calls, the retained receiver's own state is what the calls observe, and a copy keeps the value's identity
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The receiver expression is evaluated exactly once when the bound method value is created, and the receiver is retained strongly by that value.
//   - Semantic equality for function values is reference identity, and `hashCode()` is the matching reference-identity hash.
//
class Wrapper {
    var inner: Target
    var mutable evaluations: Integer = 0
    Wrapper(inner: Target) {
        this.inner = inner
    }
    func target(): Target {
        this.evaluations = this.evaluations + 1
        return this.inner
    }
}

class Target {
    var label: String
    Target(label: String) {
        this.label = label
    }
    func describe(): String {
        return this.label
    }
}

var wrapper = Wrapper(Target("x"))
var baseline = wrapper.evaluations
var method: func(): String = wrapper.target().describe
var copied = method
print(wrapper.evaluations - baseline)
print("|")
print(method())
print("|")
print(copied())
print("|")
print(copied === method)
var again: func(): String = wrapper.target().describe
print("|")
print(again === method)
print("|")
print(wrapper.evaluations - baseline)
