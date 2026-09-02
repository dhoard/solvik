// expected C101: explicit type arguments pin the parameter type
package reference_invalid

func identity<T>(value: T) -> T {
    return value
}

func main() -> int {
    e: any = identity<string>(42)
    return 0
}
