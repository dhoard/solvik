// Numeric widening applies into nullable targets.
package conformance

func main() -> int {
    f: float? = 5
    g: int? = byte(3)
    if (f ?? 0.0) != 5.0 || (g ?? -1) != 3 {
        return 0
    }
    return 1
}
