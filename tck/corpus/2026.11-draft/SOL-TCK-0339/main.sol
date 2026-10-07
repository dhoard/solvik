// Solvik TCK SOL-TCK-0339
// The unused class B's initializer block does not run, and A's runs exactly once at the first read of its static property.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class that is never actively used is never initialized: an unused class's `static` block does not run, and its static cells keep their type defaults.
//   - On the first active use, and before that use reads any cell or evaluates any call argument, the class runs its initializer once, and only once, in this order:
//
class A {
    static {
        print("initA")
    }

    var static mutable n: Integer = 5

    A() {
    }
}
class B {
    static {
        print("initB")
    }

    B() {
    }
}
print("start")
print(A.n)
print("mid")
print("done")
