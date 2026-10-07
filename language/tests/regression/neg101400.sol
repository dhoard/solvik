// A generic function reference in an argument position waits for the callee's parameters to be decided,
// and a callee whose own type parameter no argument determines never gets decided, so the reference
// cannot be instantiated either (LANGUAGE_SPEC.md section 6).
func takesOnly<U>(f: func(U): U): Integer {
    return 0
}

func identity<T>(value: T): T {
    return value
}

func use() {
    takesOnly(identity)
}
use()
