package reference_generic_explicit

func identity<T>(value: T) -> T {
    return value
}

func first<T>(items: list<T>, fallback: T) -> T {
    if items.len() == 0 {
        return fallback
    }
    return items[0]
}

struct Box<T> {
    pub value: T

    pub func wrap<U>(other: U) -> string {
        return "wrapped"
    }
}

func main() -> int {
    // Explicit type arguments on generic function calls.
    a: int = identity<int>(42)
    if a != 42 {
        return 1
    }

    // Explicit type arguments on generic struct literals.
    b: Box<int> = Box<int> { value: 9 }
    if b.value != 9 {
        return 2
    }

    // Explicit type arguments on generic methods.
    if b.wrap<string>("s") != "wrapped" {
        return 3
    }

    // Nested explicit type arguments.
    c: Box<Box<int>> = Box<Box<int>> { value: Box<int> { value: 6 } }
    if c.value.value != 6 {
        return 4
    }

    // Explicit arguments pin parameters that values cannot infer.
    d: int = first<int>([], 3)
    if d != 3 {
        return 5
    }

    // Explicit instantiation with the correct argument type.
    e: string = identity<string>("ok")
    if e != "ok" {
        return 6
    }

    // Comparisons with '<' still parse as comparisons, not instantiation.
    f: int = 5
    if !(f < 10) {
        return 7
    }
    return 0
}
