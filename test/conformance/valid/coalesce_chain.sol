// ?? selects the first non-null value from left to right; chains of any
// length work without parentheses, and non-null falsy values are preserved.
package conformance

func main() -> int {
    // 2-operand
    a: string? = null
    r2: string = a ?? "Guest"
    // 3-operand
    b: string? = null
    c: string? = null
    r3: string = a ?? b ?? "fallback"
    // 4-operand
    d: int? = null
    r4: int = d ?? null ?? 0 ?? 4
    if r2 == "Guest" && r3 == "fallback" && r4 == 0 {
        return 1
    }
    return 0
}
