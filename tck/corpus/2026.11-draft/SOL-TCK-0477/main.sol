// Solvik TCK SOL-TCK-0477
// The bare name stays legal as an immediate call in the same class where `this.compute` is the value form, which is what separates the rule from one that forbids the name outright
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `this.method` is a bound reference to the current receiver.
//   - An unqualified method name remains legal only as an immediate call under the existing implicit-`this` rule, so using a method as a value requires `this.method` and a bare unqualified method name in a value position is `SOLV-RESOL-001`.
//   - `super.method` creates a value bound to `this` that invokes the immediate superclass implementation without virtual redispatch, matching an immediate `super.method(...)` call.
//
class Helper {
    func compute(): Integer {
        return 7
    }
    func asCall(): Integer {
        return compute()
    }
    func asValue(): func(): Integer {
        return this.compute
    }
}

print(Helper().asCall())
print("|")
print(Helper().asValue()())
