// expected C102: calls to non-callable values
package reference_invalid

func main() -> int {
    x: int = 5
    return x(3)
}
