package reference_static_typing

// Positive coverage of Phase 4 static typing: null narrowing, invariance,
// downcasting, coercion, variadics, and control-flow analysis.

func lenOf(s: String?) -> Int {
    if s != null {
        // narrowing: s is a string here
        return s.len()
    }
    return 0
}

func lenOf2(s: String?) -> Int {
    if s == null {
        return 0
    } else {
        return s.len()
    }
}

func coerce(x: Int?) -> Int {
    return x ?? 0
}

func find(xs: List<Int>, target: Int) -> Int {
    mut i: Int = 0
    for x in xs {
        if x == target {
            return i
        }
        i = i + 1
    }
    return -1
}

func sumAll(values: ...Int) -> Int {
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    return total
}

func pick(values: ...String) -> String {
    return values[0]
}

func describe(n: Int) -> String {
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

func main() -> Int {
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
    anything: Any = 42
    n: Int = anything
    if n != 42 {
        return 10
    }

    // String -> exception coercion remains legal.
    failure: Exception = "custom error"
    if failure.message != "custom error" {
        return 11
    }

    // Generic instantiations are invariant; widening works outside generics.
    b: Box<Int> = Box { value: 7 }
    f: Float = b.value
    if f != 7.0 {
        return 12
    }

    // Mutable bindings, mutable fields, and mutating methods.
    mut total: Int = 0
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
    pub mut x: Int

    pub mut func move(dx: Int) {
        self.x = self.x + dx
    }
}
