// Oracle derived by hand from LANGUAGE_SPEC section 9, verbatim:
//   "The compiler synthesizes forwarding behavior for interface members supplied by a
//    delegate."
//   "Delegation removes forwarding boilerplate."
// Obligation: a class declares `delegate val` with an interface type and does NOT write a
// forwarding method for the interface member it implements; calling that member on the
// class must reach the delegate's implementation. `greet` is declared nowhere on `Host`,
// so producing "hello" can only come through synthesized forwarding.
interface Greeter {
    func greet(): String
}

class English implements Greeter {
    func greet(): String {
        return "hello"
    }
}

class Host implements Greeter {
    delegate val impl: Greeter

    Host(impl: Greeter) {
        this.impl = impl
    }
}

print(Host(English()).greet())
