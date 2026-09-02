package reference_closures_valid

// Compile-only coverage of function-type and closure syntax shapes.

func apply3(a: int, b: int, f: func<int, int, int>) -> int {
    return f(a, b)
}

func makeMapper<T, R>(step: T) -> func<T, R> {
    return func(x: T) -> R {
        return "unreachable"
    }
}

struct Registry {
    pub onEvent: func<string, void>?
    pub onValue: func<int, int>

    pub func notify(msg: string) {
        cb: func<string, void>? = self.onEvent
        if cb != null {
            cb(msg)
        }
    }

    pub func process(v: int) -> int {
        return self.onValue(v)
    }
}

func main() -> int {
    // Nested function types.
    transform: func<func<int, int>, func<int, int>> = func(f: func<int, int>) -> func<int, int> {
        return func(x: int) -> int {
            return f(x) + 1
        }
    }
    base: func<int, int> = func(x: int) -> int { return x * 2 }
    enhanced: func<int, int> = transform(base)
    if enhanced(3) != 7 {
        return 1
    }

    // Functions as collection elements.
    ops: list<func<int, int>> = [func(x: int) -> int { return x }, func(x: int) -> int { return x + 1 }]
    if ops.len() != 2 {
        return 2
    }

    // Void callback in a struct literal.
    mut calls: int = 0
    reg: Registry = Registry {
        onEvent: func(msg: string) { calls = calls + 1 },
        onValue: func(v: int) -> int { return v * 2 },
    }
    reg.notify("hello")
    if reg.process(4) != 8 {
        return 3
    }
    if calls != 1 {
        return 4
    }

    // Empty struct callback field default is null; coalescing covers it.
    reg2: Registry = Registry {
        onEvent: null,
        onValue: func(v: int) -> int { return v },
    }
    reg2.notify("x")
    return 0
}
