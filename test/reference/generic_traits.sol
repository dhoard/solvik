package reference_generic_traits

trait Sized<Q> {
    func sized(v: Q) -> int
}

struct Pair<T> {
    pub a: T
    pub b: T

    pub func iterator() -> list<T> {
        return [a, b]
    }
}

struct Doubler {
    pub func sized(v: int) -> int {
        return v * 2
    }
}

// A type parameter that appears only as a trait argument is inferred from the
// actual argument's structural method signatures.
func apply<Q, C: Sized<Q>>(c: C) -> int {
    return c.sized(3)
}

func total<T, C: Iterable<T>>(items: C) -> T {
    return items.iterator()[0]
}

func joinAll<T: Stringable, C: Iterable<T>>(items: C) -> string {
    mut out: string = ""
    for v in items {
        out = out .. v.string()
    }
    return out
}

func main() -> int {
    // User struct satisfies a user generic trait; Q solved as int.
    if apply(Doubler {  }) != 6 {
        return 1
    }

    // Generic struct satisfies Iterable<T> after substitution; T solved as int.
    p: Pair<int> = Pair { a: 3, b: 4 }
    if total(p) != 3 {
        return 2
    }

    // Same generic struct at a different instantiation.
    q: Pair<string> = Pair { a: "x", b: "y" }
    if joinAll(q) != "xy" {
        return 3
    }

    // Built-in values satisfy the same structural traits.
    values: list<int> = [10, 20]
    if total(values) != 10 {
        return 4
    }
    if joinAll(values) != "1020" {
        return 5
    }

    // Multiple constraints on one parameter.
    if renderBoth("hi") != "hi!" {
        return 6
    }
    return 0
}

func renderBoth<T: Stringable & Equatable>(v: T) -> string {
    if !v.equals("other") {
        return v.string() .. "!"
    }
    return v.string()
}
