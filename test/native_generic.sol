package native_generic

func identity<T>(value: T) -> T {
    return value
}

func main() -> int {
    return identity(42) - 42
}
