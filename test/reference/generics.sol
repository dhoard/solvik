package reference_generics

func identity<T>(value: T) -> T {
    return value
}

struct Box<T> {
    pub value: T

    pub func get() -> T {
        return value
    }
}

func main() -> Int {
    n: Int = identity(42)
    text: String = identity("solvik")
    box: Box<Int> = Box { value: n }
    if box.get() != 42 {
        return 1
    }
    if text != "solvik" {
        return 2
    }
    return 0
}
