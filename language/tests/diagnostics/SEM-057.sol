// expected: SOLV-SEM-057
// Capture binds imvar mutableues only. A `var mutable` is mutable, so a capture item naming one is rejected at
// the item, and reading that name in the body is rejected too (docs/LANGUAGE_SPEC.md section 6, "Explicit
// immutable closure capture": a capture item naming a mutable binding reports SOLV-SEM-057).
func demo(): func(): Integer {
    var mutable count: Integer = 1
    return func [count](): Integer {
        return count
    }
}
print(demo)
