// expected C118: initializer must match the declared type
package reference_invalid

func main() -> Int {
    n: Int = "str"
    return 0
}
