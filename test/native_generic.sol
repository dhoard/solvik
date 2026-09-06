package native_generic

func identity<T>(value: T) -> T {
    return value
}

func main() -> Int {
    return identity(42) - 42
}
