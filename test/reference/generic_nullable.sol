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

func makeEmpty() -> Box<Int?> {
    return Box { value: null }
}

func main() -> Int {
    // Explicit type arguments instantiate a nullable parameter.
    a: Box<Int?> = Box<Int?> { value: null }
    if a.get() != null {
        return 1
    }

    // The annotation seeds the literal's instantiation.
    b: Box<Int?> = Box { value: null }
    if b.value != null {
        return 2
    }

    // Seeding also applies on assignment to a declared target.
    mut c: Box<Int?> = Box { value: 7 }
    c = Box { value: null }
    if c.value != null {
        return 3
    }

    // ...and on return statements.
    if makeEmpty().value != null {
        return 4
    }

    // A null argument with a declared nullable type participates in inference.
    n: Int? = null
    r: Int? = identity(n)
    if r != null {
        return 5
    }
    if (r ?? 8) != 8 {
        return 6
    }

    // Explicit nullable type arguments accept null directly.
    z: Int? = identity<Int?>(null)
    if z != null {
        return 7
    }

    // Nullable elements nest inside collections.
    xs: List<Box<Int?>> = [Box<Int?> { value: null }, Box<Int?> { value: 5 }]
    if xs[0].value != null {
        return 8
    }
    if (xs[1].value ?? 0) != 5 {
        return 9
    }
    ys: List<Int?> = [1, null, 3]
    if ys[1] != null {
        return 10
    }

    // Nullable struct fields type-check through substitution.
    struct_check: Box<String?> = Box<String?> { value: null }
    if struct_check.get() != null {
        return 11
    }
    return 0
}
