// Mixed int/float comparisons are numeric and type-checked.
package conformance

func main() -> int {
    a: int = 5
    b: float = 5.0
    if a == b && a < 6.5 {
        return 1
    }
    return 0
}
