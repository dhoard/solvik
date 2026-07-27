package example

func sum(values: ...int) -> int {
    mut total: int = 0
    for v in values {
        total = total + v
    }
    return total
}

func first(items: ...string) -> string {
    return items[0]
}

func greet(greeting: string, names: ...string) -> string {
    mut result: string = ""
    for name in names {
        result = result + greeting + " " + name + "\n"
    }
    return result
}

func main() -> int {
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
    mut result: string = greet("Hi", "Alice", "Bob")
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

    // --- string.format with variadic ---
    mut formatted: string = string.format("hello {} {}", "world", "solvik")
    if formatted != "hello world solvik" {
        return 9
    }

    // --- string.format with single vararg ---
    formatted = string.format("hello {}", "world")
    if formatted != "hello world" {
        return 10
    }

    return 0
}
