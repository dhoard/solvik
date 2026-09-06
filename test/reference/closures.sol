package reference_closures

func double(x: Int) -> Int {
    return x * 2
}

func apply(value: Int, f: Func<Int, Int>) -> Int {
    return f(value)
}

func makeAdder(amount: Int) -> Func<Int, Int> {
    return func(x: Int) -> Int {
        return x + amount
    }
}

func makeCounter() -> Func<Int> {
    mut count: Int = 0
    return func() -> Int {
        count = count + 1
        return count
    }
}

func makeOuter() -> Func<Int, Int> {
    mut base: Int = 3
    inner: Func<Int, Int> = func(x: Int) -> Int {
        return base + x
    }
    // `mut` capture shares storage: this assignment is visible to `inner`.
    base = 5
    return inner
}

func factorial(n: Int) -> Int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

struct Greeter {
    pub prefix: String

    pub func greet(name: String) -> String {
        return self.prefix .. name
    }
}

struct Accumulator {
    pub mut total: Int

    pub mut func makeAdder() -> Func<Int, Int> {
        return func(x: Int) -> Int {
            self.total = self.total + 1
            return x + self.total
        }
    }

    pub func peek() -> Int {
        return self.total
    }
}

struct Handler {
    pub cb: Func<Int, Int>

    pub func run(v: Int) -> Int {
        return self.cb(v)
    }
}

func main() -> Int {
    // Top-level function as a value.
    f: Func<Int, Int> = double
    if f(21) != 42 {
        return 1
    }

    // Function as an argument.
    if apply(5, double) != 10 {
        return 2
    }

    // Function as a return value (returned closure captures `amount`).
    add3: Func<Int, Int> = makeAdder(3)
    if add3(4) != 7 {
        return 3
    }

    // Anonymous function literal.
    multiply: Func<Int, Int> = func(x: Int) -> Int {
        return x * 10
    }
    if multiply(3) != 30 {
        return 4
    }

    // Zero-argument function type is func<int>; void return is func<..., void>.
    const5: Func<Int> = func() -> Int {
        return 5
    }
    if const5() != 5 {
        return 5
    }
    mut logCount: Int = 0
    logger: Func<Int, Void> = func(n: Int) {
        logCount = logCount + n
    }
    logger(2)
    logger(3)
    if logCount != 5 {
        return 6
    }

    // Immutable capture behaves like a value copy.
    base: Int = 10
    addBase: Func<Int, Int> = func(x: Int) -> Int {
        return base + x
    }
    if addBase(1) != 11 {
        return 7
    }

    // Mutable capture shares storage with the enclosing scope.
    mut total: Int = 100
    addTotal: Func<Int, Int> = func(x: Int) -> Int {
        total = total + x
        return total
    }
    addTotal(5)
    addTotal(7)
    if total != 112 {
        return 8
    }

    // Each call of the enclosing function creates fresh captured storage.
    c1: Func<Int> = makeCounter()
    c2: Func<Int> = makeCounter()
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
    outer: Func<Int, Int> = makeOuter()
    if outer(3) != 8 {
        return 12
    }

    // Shadowing: a parameter shadows a captured name.
    x: Int = 1
    shadowed: Func<Int, Int> = func(x: Int) -> Int {
        return x * 10
    }
    if shadowed(5) != 50 {
        return 13
    }

    // Recursive named functions are callable from closures.
    fact: Func<Int, Int> = factorial
    if fact(5) != 120 {
        return 14
    }

    // Bound method as a value.
    g: Greeter = Greeter { prefix: "hi " }
    greet: Func<String, String> = g.greet
    if greet("bob") != "hi bob" {
        return 15
    }

    // Closures in collections.
    fns: List<Func<Int, Int>> = [
        func(x: Int) -> Int { return x + 1 },
        func(x: Int) -> Int { return x * 2 },
    ]
    if fns[0](10) != 11 {
        return 16
    }
    if fns[1](10) != 20 {
        return 17
    }

    // Struct field holding a callback.
    h: Handler = Handler { cb: func(x: Int) -> Int { return x - 1 } }
    if h.run(10) != 9 {
        return 18
    }

    // Closure inside a mutating method can mutate the receiver.
    mut acc: Accumulator = Accumulator { total: 10 }
    adder: Func<Int, Int> = acc.makeAdder()
    if adder(1) != 12 {
        return 19
    }
    if acc.peek() != 11 {
        return 20
    }

    // Nullable function values and null-coalescing.
    maybe: Func<Int, Int>? = null
    fallback: Func<Int, Int> = maybe ?? func(x: Int) -> Int { return x + 100 }
    if fallback(1) != 101 {
        return 21
    }

    // Calling a null function value raises a catchable null reference.
    mut nullCaught: Bool = false
    try {
        maybe(1)
        return 22
    } catch (e: Exception) {
        nullCaught = true
    }
    if !nullCaught {
        return 23
    }

    // Identity: distinct closures are never equal; references to the same
    // named function are.
    d1: Func<Int, Int> = makeAdder(1)
    d2: Func<Int, Int> = makeAdder(1)
    if d1 == d2 {
        return 24
    }
    if d1 != d1 {
        return 25
    }
    e1: Func<Int, Int> = double
    e2: Func<Int, Int> = double
    if e1 != e2 {
        return 26
    }

    // String and type identity.
    if typeOf(d1) != "Func" {
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
