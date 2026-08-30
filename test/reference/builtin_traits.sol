package reference_builtin_traits

func render<T: Stringable>(value: T) -> string {
    return value.string()
}

func count<T: Countable>(value: T) -> int {
    return value.len()
}

func main() -> int {
    if render(42) != "42" {
        return 1
    }
    if render(true) != "true" {
        return 2
    }
    values: list<int> = [1, 2, 3]
    if count(values) != 3 {
        return 3
    }
    if count("solvik") != 6 {
        return 4
    }
    return 0
}
