package reference_static_typing

// Positive coverage of Phase 4 static typing: null narrowing, invariance,
// downcasting, coercion, variadics, and control-flow analysis.

func lenOf(s: string?) -> int {
    if s != null {
        // narrowing: s is a string here
        return s.len()
    }
    return 0
}

func lenOf2(s: string?) -> int {
    if s == null {
        return 0
    } else {
        return s.len()
    }
}

func coerce(x: int?) -> int {
    return x ?? 0
}

func find(xs: list<int>, target: int) -> int {
    mut i: int = 0
    for x in xs {
        if x == target {
            return i
        }
        i = i + 1
    }
    return -1
}

func sumAll(values: ...int) -> int {
    mut total: int = 0
    for v in values {
        total = total + v
    }
    return total
}

func pick(values: ...string) -> string {
    return values[0]
}

func describe(n: int) -> string {
    if n > 0 {
        return "positive"
    } else if n < 0 {
        return "negative"
    } else {
        return "zero"
    }
}

struct Box<T> {
    pub value: T
}

func main() -> int {
    if lenOf("hi") != 2 {
        return 1
    }
    if lenOf2("hi") != 2 {
        return 2
    }
    if coerce(5) != 5 {
        return 3
    }
    if coerce(null) != 0 {
        return 4
    }
    if find([1, 2, 3], 2) != 1 {
        return 5
    }
    if find([1, 2, 3], 9) != -1 {
        return 6
    }
    if sumAll(1, 2, 3) != 6 {
        return 7
    }
    if pick("a", "b") != "a" {
        return 8
    }
    if describe(5) != "positive" {
        return 9
    }

    // Downcasting from `any` stays a runtime-checked operation.
    anything: any = 42
    n: int = anything
    if n != 42 {
        return 10
    }

    // String -> exception coercion remains legal.
    failure: exception = "custom error"
    if failure.message != "custom error" {
        return 11
    }

    // Generic instantiations are invariant; widening works outside generics.
    b: Box<int> = Box { value: 7 }
    f: float = b.value
    if f != 7.0 {
        return 12
    }

    // Mutable bindings, mutable fields, and mutating methods.
    mut total: int = 0
    total = 5
    mut p: Point = Point { x: 1 }
    p.x = 3
    p.move(2)
    if p.x != 5 {
        return 13
    }
    return 0
}

struct Point {
    pub mut x: int

    pub mut func move(dx: int) {
        self.x = self.x + dx
    }
}
