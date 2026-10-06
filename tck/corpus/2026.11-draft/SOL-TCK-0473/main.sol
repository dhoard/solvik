// Solvik TCK SOL-TCK-0473
// The receiver declares `greet` nowhere; the implementation arrives through a `delegate` property, which is the delegated-implementation route the sentence names
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type invokes the implementation selected by the captured receiver's runtime class. Overrides, interface defaults, delegated implementations, and inherited instance methods behave the same through a bound reference as through an immediate method call.
//   - formatter.format === formatter.format // false: two bound-value creations
//
interface Greeter {
    func greet(): String
}

class FrenchGreeter implements Greeter {
    func greet(): String {
        return "bonjour"
    }
}

class Host implements Greeter {
    delegate var greeter: Greeter

    Host(greeter: Greeter) {
        this.greeter = greeter
    }
}

var host = Host(FrenchGreeter())
var method: func(): String = host.greet
print(method())
