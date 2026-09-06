package reference_enums_valid

// Compile-only coverage of algebraic-enum shapes.

enum Result<T, E> {
    Ok(T)
    Error(E)
}

enum Maybe {
    Just(Int)
    Nothing
    Pair(String, Float)
}

func map2<T, U>(r: Result<T, U>) -> Int {
    switch r {
        case Result.Ok(v) {
            return 1
        }
        case Result.Error(e) {
            return 2
        }
    }
}

func nullablePattern(m: Maybe?) -> Int {
    switch m {
        case Maybe.Just(v) {
            return 1
        }
        case Maybe.Nothing {
            return 2
        }
        case Maybe.Pair(s, f) {
            return 3
        }
        case null {
            return 4
        }
    }
}

func wildcards(m: Maybe) -> Int {
    switch m {
        case Maybe.Just(_) {
            return 1
        }
        case Maybe.Nothing {
            return 2
        }
        case Maybe.Pair(_, _) {
            return 3
        }
    }
}

func nested(m: Maybe) -> Int {
    switch m {
        case Maybe.Just(_) {
            return 1
        }
        case Maybe.Nothing {
            return 2
        }
        case Maybe.Pair(s, f) {
            return 3
        }
    }
}

struct Wrapper {
    pub r: Result<Int, String>
    pub m: Maybe?
}

func main() -> Int {
    a: Result<Int, String> = Result.Ok(1)
    b: Result<List<Int>, String> = Result<List<Int>, String>.Ok([1, 2])
    c: Maybe? = null
    d: Maybe = Maybe.Pair("x", 1.5)
    w: Wrapper = Wrapper { r: a, m: Maybe.Nothing }
    if map2(a) != 1 {
        return 1
    }
    if nullablePattern(c) != 4 {
        return 2
    }
    if wildcards(d) != 3 {
        return 3
    }
    return 0
}
