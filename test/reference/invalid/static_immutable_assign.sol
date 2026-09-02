// expected C116: immutable bindings cannot be assigned
package reference_invalid

func main() -> int {
    x: int = 5
    x = 6
    return 0
}
