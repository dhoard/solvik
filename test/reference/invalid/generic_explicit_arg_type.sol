// expected C101: explicit type arguments pin the parameter type
package reference_invalid

func identity<T>(value: T) -> T {
    return value
}

func main() -> Int {
    e: Any = identity<String>(42)
    return 0
}
