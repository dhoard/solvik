package reference_closures_generic

func mapOne<T, R>(value: T, transform: func<T, R>) -> R {
    return transform(value)
}

func twice<T>(value: T, f: func<T, T>) -> T {
    return f(f(value))
}

func reduce(values: list<int>, acc: int, combine: func<int, int, int>) -> int {
    mut total: int = acc
    for v in values {
        total = combine(total, v)
    }
    return total
}

func countIf(values: list<int>, keep: func<int, bool>) -> int {
    mut n: int = 0
    for v in values {
        if keep(v) {
            n = n + 1
        }
    }
    return n
}

func doubleIt(x: int) -> int {
    return x * 2
}

struct CallbackBox<T> {
    pub cb: func<T, T>
}

func main() -> int {
    // Generic higher-order functions with inferred instantiations.
    s: string = mapOne(42, func(n: int) -> string { return "v=" .. n })
    if s != "v=42" {
        return 1
    }
    f: float = mapOne(2, func(n: int) -> float { return float(n) / 2.0 })
    if f != 1.0 {
        return 2
    }
    // A named function instantiates a func parameter too.
    d: int = mapOne(3, doubleIt)
    if d != 6 {
        return 3
    }
    // Same generic function at different instantiations.
    if twice(2, func(x: int) -> int { return x * 3 }) != 18 {
        return 4
    }
    if twice("a", func(x: string) -> string { return x .. x }) != "aaaa" {
        return 5
    }
    // Accumulators with closures.
    mut sum: int = reduce([1, 2, 3, 4], 0, func(a: int, b: int) -> int { return a + b })
    if sum != 10 {
        return 6
    }
    mut product: int = reduce([1, 2, 3, 4], 1, func(a: int, b: int) -> int { return a * b })
    if product != 24 {
        return 7
    }
    n: int = countIf([1, 2, 3, 4, 5], func(v: int) -> bool { return v % 2 == 0 })
    if n != 2 {
        return 8
    }
    // Function values inside generic structs.
    box: CallbackBox<int> = CallbackBox { cb: func(x: int) -> int { return x + 1 } }
    if box.cb(5) != 6 {
        return 9
    }
    sbox: CallbackBox<string> = CallbackBox { cb: func(x: string) -> string { return x .. "!" } }
    if sbox.cb("go") != "go!" {
        return 10
    }
    return 0
}
