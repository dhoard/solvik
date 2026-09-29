// Solvik TCK SOL-TCK-0476
// `this.greet` is read inside a superclass helper, so the current receiver -- the subclass instance or the base instance -- is what selects the implementation
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `this.method` is a bound reference to the current receiver.
//   - An unqualified method name remains legal only as an immediate call under the existing implicit-`this` rule, so using a method as a value requires `this.method` and a bare unqualified method name in a value position is `SOLV-RESOL-001`.
//   - `super.method` creates a value bound to `this` that invokes the immediate superclass implementation without virtual redispatch, matching an immediate `super.method(...)` call.
//
open class Base {
    open func greet(): String {
        return "base"
    }
    func viaThis(): func(): String {
        return this.greet
    }
}

class Leaf extends Base {
    override func greet(): String {
        return "leaf"
    }
}

print(Leaf().viaThis()())
print("|")
print(Base().viaThis()())
