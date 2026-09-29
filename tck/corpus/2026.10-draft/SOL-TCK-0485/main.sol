// Solvik TCK SOL-TCK-0485
// The generic method sits on a generic class read through `Cell(Integer)`, so the receiver's own type arguments are already closed and only the method's parameter is inferred
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - It must be instantiated to one monomorphic function type at each value-reference site, and that instantiation is contextual:
//   - A generic method reference is instantiated contextually under the same monomorphic rules as a generic top-level function reference, so `val operation: func(Integer): Integer = object.identity` is accepted and an unconstrained reference is `SOLV-TYPE-030`.
//
open class Cell<T> {
    val stored: T
    Cell(stored: T) {
        this.stored = stored
    }
    open func swap<E>(value: E): E {
        return value
    }
}

val cell: Cell<Integer> = Cell(1)
val swapString: func(String): String = cell.swap
print(swapString("swapped"))
print("|")
print(cell.stored)
