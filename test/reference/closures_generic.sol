package reference_closures_generic

func mapOne<T, R>(value: T, transform: Func<T, R>) -> R {
    return transform(value)
}

func twice<T>(value: T, f: Func<T, T>) -> T {
    return f(f(value))
}

func reduce(values: List<Int>, acc: Int, combine: Func<Int, Int, Int>) -> Int {
    mut total: Int = acc
    for v in values {
        total = combine(total, v)
    }
    return total
}

func countIf(values: List<Int>, keep: Func<Int, Bool>) -> Int {
    mut n: Int = 0
    for v in values {
        if keep(v) {
            n = n + 1
        }
    }
    return n
}

func doubleIt(x: Int) -> Int {
    return x * 2
}

struct CallbackBox<T> {
    pub cb: Func<T, T>
}

func main() -> Int {
    // Generic higher-order functions with inferred instantiations.
    s: String = mapOne(42, func(n: Int) -> String { return "v=" .. n })
    if s != "v=42" {
        return 1
    }
    f: Float = mapOne(2, func(n: Int) -> Float { return float(n) / 2.0 })
    if f != 1.0 {
        return 2
    }
    // A named function instantiates a func parameter too.
    d: Int = mapOne(3, doubleIt)
    if d != 6 {
        return 3
    }
    // Same generic function at different instantiations.
    if twice(2, func(x: Int) -> Int { return x * 3 }) != 18 {
        return 4
    }
    if twice("a", func(x: String) -> String { return x .. x }) != "aaaa" {
        return 5
    }
    // Accumulators with closures.
    mut sum: Int = reduce([1, 2, 3, 4], 0, func(a: Int, b: Int) -> Int { return a + b })
    if sum != 10 {
        return 6
    }
    mut product: Int = reduce([1, 2, 3, 4], 1, func(a: Int, b: Int) -> Int { return a * b })
    if product != 24 {
        return 7
    }
    n: Int = countIf([1, 2, 3, 4, 5], func(v: Int) -> Bool { return v % 2 == 0 })
    if n != 2 {
        return 8
    }
    // Function values inside generic structs.
    box: CallbackBox<Int> = CallbackBox { cb: func(x: Int) -> Int { return x + 1 } }
    if box.cb(5) != 6 {
        return 9
    }
    sbox: CallbackBox<String> = CallbackBox { cb: func(x: String) -> String { return x .. "!" } }
    if sbox.cb("go") != "go!" {
        return 10
    }
    return 0
}
