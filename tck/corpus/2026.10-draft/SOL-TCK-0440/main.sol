// Solvik TCK SOL-TCK-0440
// An expected `Any` accepts every function value yet exposes no complete function signature, so widening instead of instantiating is refused
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A generic function reference with no expected function type is `SOLV-TYPE-030`:
//   - An expected `Any`, an unbounded type parameter, or any other type that does not expose a complete function signature is insufficient.
//   - A generic function or generic method used as a value without a complete expected function type is the existing `TYPE_CANNOT_INFER` (`SOLV-TYPE-030`), reported on the function or method reference; no second inference diagnostic exists.
//
func identity<T>(value: T): T {
    return value
}

val boxed: Any = identity
print(boxed)
print("EXECUTED-INVALID")
