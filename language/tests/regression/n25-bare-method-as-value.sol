// A bare unqualified method name in a value position is an unknown name, not a bound method value:
// using a method as a value requires `this.method` (docs/LANGUAGE_SPEC.md section 6, "Bound method
// references"). The immediate call on the same bare name is legal, which is what makes the rule about
// value position rather than about the name.

class Helper {
    func compute(): Integer {
        return 1
    }
    func asValue(): func(): Integer {
        return compute
    }
    func asCall(): Integer {
        return compute()
    }
}

println(Helper().asCall())
