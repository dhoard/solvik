// Solvik TCK SOL-TCK-0479
// `super.greet` is bound from a class that overrides `greet`, so the value prints the immediate superclass text while the immediate call on the same receiver prints the override
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
}

open class Mid extends Base {
    override func greet(): String {
        return "mid"
    }
    func viaSuper(): func(): String {
        return super.greet
    }
}

print(Mid().viaSuper()())
print("|")
print(Mid().greet())
