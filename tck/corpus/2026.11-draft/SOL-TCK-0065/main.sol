// Oracle derived by hand from LANGUAGE_SPEC section 8, verbatim:
//   "Interfaces define nominal contracts and may have default method implementations."
// Obligation: a class implementing an interface supplies only the abstract member; the
// interface's default implementation is usable on the class without being redeclared, and
// its body dispatches back to the implemented member (dynamic binding through the
// interface's own default). `shout` is never written on the class, so its presence is the
// observable. `..` is the concatenation operator defined by section 3; the section 8
// example writes the default body with `+`, which is the separately reported `+`-on-String
// conflict and is deliberately avoided here so this test depends on nothing disputed.
interface Named {
    func name(): String

    func shout(): String {
        return this.name() .. "!"
    }
}

class Person implements Named {
    func name(): String {
        return "doug"
    }
}

var p: Named = Person()
print(p.shout())
