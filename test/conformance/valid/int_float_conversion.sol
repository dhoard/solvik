// int/float accept numeric values and parseable strings.
package conformance

func main() -> Int {
    a: Int = int(42)
    b: Int = int(3.9)
    c: Float = float(42)
    return a + b
}
