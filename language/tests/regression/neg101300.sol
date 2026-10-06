// An expected function type whose arity differs from the declaration's is still a complete signature, so
// the substitution comes from the positions the two lists share and the resulting monomorphic type is
// simply not assignable to what was declared (LANGUAGE_SPEC.md section 6).
func pick<T>(first: T, second: T): T {
    return first
}

func use(): Unit {
    var wrong: func(Integer): Integer = pick
}
use()
