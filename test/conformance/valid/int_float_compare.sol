// Mixed int/float comparisons are numeric and type-checked.
package conformance

func main() -> Int {
    a: Int = 5
    b: Float = 5.0
    if a == b && a < 6.5 {
        return 1
    }
    return 0
}
