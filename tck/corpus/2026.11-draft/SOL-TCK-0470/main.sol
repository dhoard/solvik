// Solvik TCK SOL-TCK-0470
// Both references are written through a `Base`-typed receiver, so the printed texts can only come from the runtime class of each receiver
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Ordinary virtual dispatch is preserved. A reference obtained through a class or interface type invokes the implementation selected by the captured receiver's runtime class. Overrides, interface defaults, delegated implementations, and inherited instance methods behave the same through a bound reference as through an immediate method call.
//   - formatter.format === formatter.format // false: two bound-value creations
//
mutable class Base {
    mutable func name(): String {
        return "base"
    }
}

mutable class Mid extends Base {
    override mutable func name(): String {
        return "mid"
    }
}

class Leaf extends Mid {
    override func name(): String {
        return "leaf"
    }
}

var base: Base = Base()
var leaf: Base = Leaf()
var fromBase: func(): String = base.name
var fromLeaf: func(): String = leaf.name
print(fromBase())
print("|")
print(fromLeaf())
