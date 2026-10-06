// Solvik TCK SOL-TCK-0484
// One generic method reference instantiates to two different monomorphic function types from one receiver, each driven entirely by its expected type
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - It must be instantiated to one monomorphic function type at each value-reference site, and that instantiation is contextual:
//   - A generic method reference is instantiated contextually under the same monomorphic rules as a generic top-level function reference, so `var operation: func(Integer): Integer = object.identity` is accepted and an unconstrained reference is `SOLV-TYPE-030`.
//
class Box {
    func pick<T>(value: T): T {
        return value
    }
}

var box = Box()
var fromInteger: func(Integer): Integer = box.pick
var fromString: func(String): String = box.pick
print(fromInteger(42))
print("|")
print(fromString("s"))
