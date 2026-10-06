// Oracle derived by hand from LANGUAGE_SPEC section 8, verbatim:
//   "Classes may implement multiple interfaces:"
//   "Interfaces define nominal contracts"
// Obligation: one class implementing two distinct interfaces is usable through BOTH
// interface types. Printing the two results in one stream shows the nominal contract
// holds per-interface (one name result and one greeting result) rather than the two
// interfaces collapsing into one. The exact text is derived from the two method bodies
// and the `..` rendering rule (section 3), not observed.
interface Named {
    func name(): String
}

interface Greetable {
    func greet(): String
}

class Person implements Named, Greetable {
    var label: String

    Person(label: String) {
        this.label = label
    }

    func name(): String {
        return this.label
    }

    func greet(): String {
        return "hi " .. this.name()
    }
}

var n: Named = Person("Doug")
var g: Greetable = Person("Doug")
print(n.name() .. "/" .. g.greet())
