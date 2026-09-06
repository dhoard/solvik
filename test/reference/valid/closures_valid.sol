package reference_closures_valid

// Compile-only coverage of function-type and closure syntax shapes.

func apply3(a: Int, b: Int, f: Func<Int, Int, Int>) -> Int {
    return f(a, b)
}

func makeMapper<T, R>(step: T) -> Func<T, R> {
    return func(x: T) -> R {
        return "unreachable"
    }
}

struct Registry {
    pub onEvent: Func<String, Void>?
    pub onValue: Func<Int, Int>

    pub func notify(msg: String) {
        cb: Func<String, Void>? = self.onEvent
        if cb != null {
            cb(msg)
        }
    }

    pub func process(v: Int) -> Int {
        return self.onValue(v)
    }
}

func main() -> Int {
    // Nested function types.
    transform: Func<Func<Int, Int>, Func<Int, Int>> = func(f: Func<Int, Int>) -> Func<Int, Int> {
        return func(x: Int) -> Int {
            return f(x) + 1
        }
    }
    base: Func<Int, Int> = func(x: Int) -> Int { return x * 2 }
    enhanced: Func<Int, Int> = transform(base)
    if enhanced(3) != 7 {
        return 1
    }

    // Functions as collection elements.
    ops: List<Func<Int, Int>> = [func(x: Int) -> Int { return x }, func(x: Int) -> Int { return x + 1 }]
    if ops.len() != 2 {
        return 2
    }

    // Void callback in a struct literal.
    mut calls: Int = 0
    reg: Registry = Registry {
        onEvent: func(msg: String) { calls = calls + 1 },
        onValue: func(v: Int) -> Int { return v * 2 },
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
        onValue: func(v: Int) -> Int { return v },
    }
    reg2.notify("x")
    return 0
}
