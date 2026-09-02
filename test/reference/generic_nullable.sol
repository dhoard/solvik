package reference_generic_nullable

func identity<T>(value: T) -> T {
    return value
}

struct Box<T> {
    pub value: T

    pub func get() -> T {
        return value
    }
}

func makeEmpty() -> Box<int?> {
    return Box { value: null }
}

func main() -> int {
    // Explicit type arguments instantiate a nullable parameter.
    a: Box<int?> = Box<int?> { value: null }
    if a.get() != null {
        return 1
    }

    // The annotation seeds the literal's instantiation.
    b: Box<int?> = Box { value: null }
    if b.value != null {
        return 2
    }

    // Seeding also applies on assignment to a declared target.
    mut c: Box<int?> = Box { value: 7 }
    c = Box { value: null }
    if c.value != null {
        return 3
    }

    // ...and on return statements.
    if makeEmpty().value != null {
        return 4
    }

    // A null argument with a declared nullable type participates in inference.
    n: int? = null
    r: int? = identity(n)
    if r != null {
        return 5
    }
    if (r ?? 8) != 8 {
        return 6
    }

    // Explicit nullable type arguments accept null directly.
    z: int? = identity<int?>(null)
    if z != null {
        return 7
    }

    // Nullable elements nest inside collections.
    xs: list<Box<int?>> = [Box<int?> { value: null }, Box<int?> { value: 5 }]
    if xs[0].value != null {
        return 8
    }
    if (xs[1].value ?? 0) != 5 {
        return 9
    }
    ys: list<int?> = [1, null, 3]
    if ys[1] != null {
        return 10
    }

    // Nullable struct fields type-check through substitution.
    struct_check: Box<string?> = Box<string?> { value: null }
    if struct_check.get() != null {
        return 11
    }
    return 0
}
