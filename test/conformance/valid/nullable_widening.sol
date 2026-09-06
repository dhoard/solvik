// Numeric widening applies into nullable targets.
package conformance

func main() -> Int {
    f: Float? = 5
    g: Int? = byte(3)
    if (f ?? 0.0) != 5.0 || (g ?? -1) != 3 {
        return 0
    }
    return 1
}
