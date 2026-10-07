class mutable Animal {
    method speak(): String {
        return "generic"
    }
}

class Dog extends Animal {
}

class Cat extends Animal {
}

// Oracle derived by hand from LANGUAGE_SPEC section 18, verbatim:
//   "An unsuccessful `as` cast raises a Solvik runtime type error."
// Obligation: `v` holds a Cat, so `v as Dog` must fail at run time -- not at compile
// time (the target is a subtype of the static type, so the program is well typed) and
// not silently. The fault therefore has to surface as a structured runtime failure in
// the protocol's cast-failure classification (protocol.md section 4.1), with
// empty stdout because the only print follows the cast. Deriving the category from the
// protocol taxonomy is not reading a value from the implementation.
var mutable v: Any = Cat()
var d: Dog = v as Dog
print("unreachable")
