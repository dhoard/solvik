// expected C096: explicit type argument count must match the declaration
package conformance

func identity<T>(value: T) -> T {
    return value
}

func main() -> int {
    return identity<int, string>(42)
}
