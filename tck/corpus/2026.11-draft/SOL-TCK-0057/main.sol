// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "Single inheritance only:" with `class Dog extends Animal { override func speak() ...
//    return "woof" }`
//   "Overrides must always use `override`."
//   "A `mutable` member may be overridden; all other members are final."
// Obligation: a `mutable` member overridden in a subclass is dispatched dynamically through
// a base-typed reference, while the base class's own implementation is unchanged. The two
// observables are printed together, so a statically-bound call (which would print
// "base/base") and a broken base implementation both fail the oracle.
mutable class Base {
    mutable func label(): String {
        return "base"
    }
}

class Sub extends Base {
    override func label(): String {
        return "sub"
    }
}

val b: Base = Sub()
print(b.label() .. "/" .. Base().label())
