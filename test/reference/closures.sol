package reference_closures

func double(x: int) -> int {
    return x * 2
}

func apply(value: int, f: func<int, int>) -> int {
    return f(value)
}

func makeAdder(amount: int) -> func<int, int> {
    return func(x: int) -> int {
        return x + amount
    }
}

func makeCounter() -> func<int> {
    mut count: int = 0
    return func() -> int {
        count = count + 1
        return count
    }
}

func makeOuter() -> func<int, int> {
    mut base: int = 3
    inner: func<int, int> = func(x: int) -> int {
        return base + x
    }
    // `mut` capture shares storage: this assignment is visible to `inner`.
    base = 5
    return inner
}

func factorial(n: int) -> int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

struct Greeter {
    pub prefix: string

    pub func greet(name: string) -> string {
        return self.prefix .. name
    }
}

struct Accumulator {
    pub mut total: int

    pub mut func makeAdder() -> func<int, int> {
        return func(x: int) -> int {
            self.total = self.total + 1
            return x + self.total
        }
    }

    pub func peek() -> int {
        return self.total
    }
}

struct Handler {
    pub cb: func<int, int>

    pub func run(v: int) -> int {
        return self.cb(v)
    }
}

func main() -> int {
    // Top-level function as a value.
    f: func<int, int> = double
    if f(21) != 42 {
        return 1
    }

    // Function as an argument.
    if apply(5, double) != 10 {
        return 2
    }

    // Function as a return value (returned closure captures `amount`).
    add3: func<int, int> = makeAdder(3)
    if add3(4) != 7 {
        return 3
    }

    // Anonymous function literal.
    multiply: func<int, int> = func(x: int) -> int {
        return x * 10
    }
    if multiply(3) != 30 {
        return 4
    }

    // Zero-argument function type is func<int>; void return is func<..., void>.
    const5: func<int> = func() -> int {
        return 5
    }
    if const5() != 5 {
        return 5
    }
    mut logCount: int = 0
    logger: func<int, void> = func(n: int) {
        logCount = logCount + n
    }
    logger(2)
    logger(3)
    if logCount != 5 {
        return 6
    }

    // Immutable capture behaves like a value copy.
    base: int = 10
    addBase: func<int, int> = func(x: int) -> int {
        return base + x
    }
    if addBase(1) != 11 {
        return 7
    }

    // Mutable capture shares storage with the enclosing scope.
    mut total: int = 100
    addTotal: func<int, int> = func(x: int) -> int {
        total = total + x
        return total
    }
    addTotal(5)
    addTotal(7)
    if total != 112 {
        return 8
    }

    // Each call of the enclosing function creates fresh captured storage.
    c1: func<int> = makeCounter()
    c2: func<int> = makeCounter()
    if c1() != 1 {
        return 9
    }
    if c1() != 2 {
        return 10
    }
    if c2() != 1 {
        return 11
    }

    // Nested closure; `mut` capture observes later assignments.
    outer: func<int, int> = makeOuter()
    if outer(3) != 8 {
        return 12
    }

    // Shadowing: a parameter shadows a captured name.
    x: int = 1
    shadowed: func<int, int> = func(x: int) -> int {
        return x * 10
    }
    if shadowed(5) != 50 {
        return 13
    }

    // Recursive named functions are callable from closures.
    fact: func<int, int> = factorial
    if fact(5) != 120 {
        return 14
    }

    // Bound method as a value.
    g: Greeter = Greeter { prefix: "hi " }
    greet: func<string, string> = g.greet
    if greet("bob") != "hi bob" {
        return 15
    }

    // Closures in collections.
    fns: list<func<int, int>> = [
        func(x: int) -> int { return x + 1 },
        func(x: int) -> int { return x * 2 },
    ]
    if fns[0](10) != 11 {
        return 16
    }
    if fns[1](10) != 20 {
        return 17
    }

    // Struct field holding a callback.
    h: Handler = Handler { cb: func(x: int) -> int { return x - 1 } }
    if h.run(10) != 9 {
        return 18
    }

    // Closure inside a mutating method can mutate the receiver.
    mut acc: Accumulator = Accumulator { total: 10 }
    adder: func<int, int> = acc.makeAdder()
    if adder(1) != 12 {
        return 19
    }
    if acc.peek() != 11 {
        return 20
    }

    // Nullable function values and null-coalescing.
    maybe: func<int, int>? = null
    fallback: func<int, int> = maybe ?? func(x: int) -> int { return x + 100 }
    if fallback(1) != 101 {
        return 21
    }

    // Calling a null function value raises a catchable null reference.
    mut nullCaught: bool = false
    try {
        maybe(1)
        return 22
    } catch (e: exception) {
        nullCaught = true
    }
    if !nullCaught {
        return 23
    }

    // Identity: distinct closures are never equal; references to the same
    // named function are.
    d1: func<int, int> = makeAdder(1)
    d2: func<int, int> = makeAdder(1)
    if d1 == d2 {
        return 24
    }
    if d1 != d1 {
        return 25
    }
    e1: func<int, int> = double
    e2: func<int, int> = double
    if e1 != e2 {
        return 26
    }

    // String and type identity.
    if typeOf(d1) != "function" {
        return 27
    }
    if d1.string() != "<closure>" {
        return 28
    }
    if double.string() != "<function double>" {
        return 29
    }
    return 0
}
