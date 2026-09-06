package reference_enums_algebraic

enum Result<T, E> {
    Ok(T)
    Error(E)
}

enum Option<T> {
    Some(T)
    None
}

enum Shape {
    Rect(Int, Int)
    Circle(Int)
    Group(Shape)
}

enum Color {
    Red
    Green
    Blue
}

func unwrap<T>(o: Option<T>, fallback: T) -> T {
    switch o {
        case Option.Some(v) {
            return v
        }
        case Option.None {
            return fallback
        }
    }
}

func area(s: Shape) -> Int {
    switch s {
        case Shape.Rect(w, h) {
            return w * h
        }
        case Shape.Circle(r) {
            return 3 * r * r
        }
        case Shape.Group(inner) {
            return area(inner)
        }
    }
}

func main() -> Int {
    // Construction: inference from payloads, expected-type seeding, explicit args.
    r: Result<Int, String> = Result.Ok(5)
    e: Result<Int, String> = Result.Error("boom")
    ex: Result<String, Bool> = Result<String, Bool>.Ok("hi")
    if r == Result<Int, String>.Ok(5) {
        // equality compares member and payloads
    } else {
        return 1
    }
    if e == Result<Int, String>.Error("boom") {
    } else {
        return 2
    }
    if Result<Int, String>.Ok(1) == Result<Int, String>.Ok(2) {
        return 3
    }
    if typeOf(r) != "Result" {
        return 4
    }

    // Pattern matching with bound variables.
    switch r {
        case Result.Ok(v) {
            if v != 5 {
                return 5
            }
        }
        case Result.Error(err) {
            return 6
        }
    }
    switch e {
        case Result.Ok(v) {
            return 7
        }
        case Result.Error(err) {
            if err != "boom" {
                return 8
            }
        }
    }

    // Same-enum qualification may be omitted (bare case patterns).
    switch r {
        case Ok(v) {
            if v != 5 {
                return 9
            }
        }
        case Error(err) {
            return 10
        }
    }

    // Multi-field payloads, nested patterns, wildcard, literal patterns.
    c: Shape = Shape.Circle(2)
    g: Shape = Shape.Group(c)
    if area(Shape.Rect(3, 4)) != 12 {
        return 11
    }
    if area(c) != 12 {
        return 12
    }
    if area(g) != 12 {
        return 13
    }
    mut nestedOk: Bool = false
    switch g {
        case Shape.Group(Shape.Circle(_)) {
            nestedOk = true
        }
        default {
            return 15
        }
    }
    if !nestedOk {
        return 14
    }
    mut literalOk: Bool = false
    switch c {
        case Shape.Circle(2) {
            literalOk = true
        }
        default {
            return 17
        }
    }
    if !literalOk {
        return 16
    }

    // Generic enum without payload cases; default covers exhaustiveness.
    a: Option<Int> = Option.Some(5)
    b: Option<Int> = Option.None
    if unwrap(a, 0) != 5 {
        return 18
    }
    if unwrap(b, 99) != 99 {
        return 19
    }
    mut got: String = ""
    switch b {
        case Option.Some(v) {
            got = "some"
        }
        default {
            got = "none"
        }
    }
    if got != "none" {
        return 20
    }

    // Nullable interaction: exhaustive switch needs `case null`.
    mut maybe: Option<Int>? = null
    mut nullMatched: Bool = false
    switch maybe {
        case Option.Some(v) {
            return 21
        }
        case Option.None {
            return 22
        }
        case null {
            nullMatched = true
        }
    }
    if !nullMatched {
        return 23
    }

    // No-payload integer-backed enums keep their old behavior.
    col: Color = Color.Blue
    switch col {
        case Color.Red {
            return 24
        }
        case Color.Green {
            return 25
        }
        case Color.Blue {
            // expected
        }
    }
    if int(Color.Green) != 1 {
        return 26
    }

    // `any` compatibility.
    x: Any = Result<Int, String>.Ok(42)
    if typeOf(x) != "Result" {
        return 27
    }

    // Values in collections and structs.
    xs: List<Option<Int>> = [Option.Some(1), Option.None, Option.Some(3)]
    if xs.len() != 3 {
        return 28
    }
    h: Holder = Holder { value: Option.Some(9) }
    switch h.value {
        case Option.Some(v) {
            if v != 9 {
                return 29
            }
        }
        case Option.None {
            return 30
        }
    }

    // int() rejects payload cases (catchable).
    mut caught: Bool = false
    try {
        z: Int = int(r)
        return 31
    } catch (err: Exception) {
        caught = true
    }
    if !caught {
        return 32
    }
    return 0
}

struct Holder {
    pub value: Option<Int>
}
