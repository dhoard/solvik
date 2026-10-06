// Solvik TCK SOL-TCK-0486
// A generic method reference where no complete function signature is expected is SOLV-TYPE-030, the code the sentence names
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

func use(box: Box) {
    var method: Any = box.pick
    print(method)
}

use(Box())
print("EXECUTED-INVALID")
