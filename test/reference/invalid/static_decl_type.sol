// expected C118: initializer must match the declared type
package reference_invalid

func main() -> int {
    n: int = "str"
    return 0
}
