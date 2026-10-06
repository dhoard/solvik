// Oracle derived by hand from LANGUAGE_SPEC section 9, verbatim:
//   "Explicit methods declared on the class take precedence over delegated members."
// Obligation: with a delegate present AND an explicit method of the same member name, the
// explicit method must run, not the forwarded one. Paired with the forwarding case (where
// the same program shape with no explicit method yields the delegate's result), the two
// tests pin the precedence in both directions from identical scaffolding: the only
// difference between them is the explicit method, and the expected value changes exactly
// when that method is added.
interface Greeter {
    func greet(): String
}

class English implements Greeter {
    func greet(): String {
        return "hello"
    }
}

class Host implements Greeter {
    delegate var impl: Greeter

    Host(impl: Greeter) {
        this.impl = impl
    }

    func greet(): String {
        return "explicit"
    }
}

print(Host(English()).greet())
