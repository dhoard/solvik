package reference_static_valid

// Compile-only coverage of static-typing shapes.

func narrowing(s: String?) -> String {
    if s != null {
        return s
    }
    return ""
}

func exhaustive(o: Option) -> Int {
    switch o {
        case Option.A {
            return 1
        }
        case Option.B {
            return 2
        }
    }
}

func whileTrue(v: Int) -> Int {
    while true {
        if v == 0 {
            return 1
        }
        return 2
    }
}

func variadicPick(first: String, rest: ...String) -> String {
    if rest.len() == 0 {
        return first
    }
    return rest[0]
}

func voidOk() {
    return
}

func main() -> Int {
    mut n: Int = 1
    n = 2
    mut m: Map<String, Int> = {}
    m["a"] = 1
    mut xs: List<Int> = [1]
    xs[0] = 2
    s: Stack<Int> = stack()
    s.push(1)
    s.pop()
    e: Exception = "x"
    if narrowing("hi") != "hi" {
        return 1
    }
    if exhaustive(Option.A) != 1 {
        return 2
    }
    if whileTrue(0) != 1 {
        return 3
    }
    if variadicPick("a", "b", "c") != "b" {
        return 4
    }
    voidOk()
    return 0
}

enum Option {
    A
    B
}
