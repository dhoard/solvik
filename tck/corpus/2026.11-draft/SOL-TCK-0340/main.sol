// Solvik TCK SOL-TCK-0340
// Constructing B triggers B's initialization, which initializes the direct superclass A first; the expected order is `A` then `B`, then the post-construction `end`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - its direct superclass is initialized first, transitively up to the root, so a base class is always set up before a derived one that relies on it;
//
class mutable A {
    static {
        print("A")
    }

    A() {
    }
}
class B extends A {
    static {
        print("B")
    }

    B() {
    }
}
print("start")
var b: B = B()
print("end")
