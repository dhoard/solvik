// Oracle derived by hand from LANGUAGE_SPEC section 7, verbatim:
//   "Single inheritance only:" with `class Dog extends Animal { override func speak() ...
//    return "woof" }`
//   "Overrides must always use `override`."
//   "An `open` member may be overridden; all other members are final."
// Obligation: an `open` member overridden in a subclass is dispatched dynamically through
// a base-typed reference, while the base class's own implementation is unchanged. The two
// observables are printed together, so a statically-bound call (which would print
// "base/base") and a broken base implementation both fail the oracle.
open class Base {
    open func label(): String {
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
