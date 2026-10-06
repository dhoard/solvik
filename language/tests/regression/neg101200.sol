// A generic function used as a value must be instantiated to one monomorphic function type, and the
// expected function type in scope is what supplies it. A reference with no expected type has nothing to
// instantiate to (LANGUAGE_SPEC.md section 6, "Generic function values").
func identity<T>(value: T): T {
    return value
}

func use(): Unit {
    var ambiguous = identity
}
use()
