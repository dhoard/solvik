package example

func sum(values: ...Int) -> Int {
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    return total
}

func first(items: ...String) -> String {
    return items[0]
}

func greet(greeting: String, names: ...String) -> String {
    mut result: String = ""
    for name in names {
        result = result .. greeting .. " " .. name .. "\n"
    }
    return result
}

func main() -> Int {
    // --- Zero variadic args ---
    if sum() != 0 {
        return 1
    }

    // --- Single variadic arg ---
    if sum(5) != 5 {
        return 2
    }

    // --- Multiple variadic args ---
    if sum(1, 2, 3) != 6 {
        return 3
    }

    // --- Many variadic args ---
    if sum(10, 20, 30, 40, 50) != 150 {
        return 4
    }

    // --- Variadic with fixed param ---
    mut result: String = greet("Hi", "Alice", "Bob")
    if result != "Hi Alice\nHi Bob\n" {
        return 5
    }

    // --- Zero variadic with fixed param ---
    result = greet("Hi")
    if result != "" {
        return 6
    }

    // --- Access variadic param as list ---
    if first("a", "b", "c") != "a" {
        return 7
    }

    // --- Single element variadic ---
    if first("only") != "only" {
        return 8
    }

    // --- string concatenation with .. ---
    mut formatted: String = "hello " .. "world"
    if formatted != "hello world" {
        return 9
    }

    return 0
}
