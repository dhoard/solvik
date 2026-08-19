// int/float accept numeric values and parseable strings.
package conformance

func main() -> int {
    a: int = int(42)
    b: int = int(3.9)
    c: float = float(42)
    return a + b
}
