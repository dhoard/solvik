// Solvik TCK SOL-TCK-0474
// The specification's own example: two reads of `formatter.format` are two bound-value creations and therefore unequal, while each still dispatches to the receiver's implementation
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type invokes the implementation selected by the captured receiver's runtime class. Overrides, interface defaults, delegated implementations, and inherited instance methods behave the same through a bound reference as through an immediate method call.
//   - formatter.format === formatter.format // false: two bound-value creations
//
class Formatter {
    var tag: String
    Formatter(tag: String) {
        this.tag = tag
    }
    func format(): String {
        return this.tag
    }
}

var formatter = Formatter("f")
print(formatter.format === formatter.format)
print("|")
print(formatter.format())
print("|")
print(formatter.format())
