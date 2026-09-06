// ?? selects the first non-null value from left to right; chains of any
// length work without parentheses, and non-null falsy values are preserved.
package conformance

func main() -> Int {
    // 2-operand
    a: String? = null
    r2: String = a ?? "Guest"
    // 3-operand
    b: String? = null
    c: String? = null
    r3: String = a ?? b ?? "fallback"
    // 4-operand
    d: Int? = null
    r4: Int = d ?? null ?? 0 ?? 4
    if r2 == "Guest" && r3 == "fallback" && r4 == 0 {
        return 1
    }
    return 0
}
