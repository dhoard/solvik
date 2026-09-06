// expected C102: calls to non-callable values
package reference_invalid

func main() -> Int {
    x: Int = 5
    return x(3)
}
