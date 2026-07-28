// underscore_test.sol -- Tests for Java-style underscore numeric literals
//
// Underscore rules:
// - Must appear between two valid digits (decimal or hex)
// - No leading/trailing underscore (adjacent to prefix, decimal point, or suffix)
package example

func main() -> int {
    println("=== Underscore Numeric Literal Tests ===")

    // ---- Integer underscores ----
    println("=== Integer Underscores ===")

    // Basic integer underscore (between digits)
    mut a: int = 1_000
    if a != 1000 {
        println("FAIL: 1_000 should be 1000, got " + string(a))
        return 1
    }
    println("  PASS: 1_000 = " + string(a))

    // Multiple groups
    b: int = 10_000
    if b != 10000 {
        println("FAIL: 10_000 should be 10000, got " + string(b))
        return 1
    }
    println("  PASS: 10_000 = " + string(b))

    // Larger groups
    c: int = 100_000
    if c != 100000 {
        println("FAIL: 100_000 should be 100000, got " + string(c))
        return 1
    }
    println("  PASS: 100_000 = " + string(c))

    // Multiple underscores with 3-digit groups
    d: int = 1_234_567
    if d != 1234567 {
        println("FAIL: 1_234_567 should be 1234567, got " + string(d))
        return 1
    }
    println("  PASS: 1_234_567 = " + string(d))

    // Small groups (1-2 digits) are valid — underscore just needs digits on both sides
    e: int = 1_2_3
    if e != 123 {
        println("FAIL: 1_2_3 should be 123, got " + string(e))
        return 1
    }
    println("  PASS: 1_2_3 = " + string(e))

    f: int = 12_34
    if f != 1234 {
        println("FAIL: 12_34 should be 1234, got " + string(f))
        return 1
    }
    println("  PASS: 12_34 = " + string(f))

    // Arithmetic with underscores
    sum: int = 1_000 + 2_000
    if sum != 3000 {
        println("FAIL: 1_000 + 2_000 should be 3000, got " + string(sum))
        return 1
    }
    println("  PASS: 1_000 + 2_000 = " + string(sum))

    // Three groups
    g: int = 1_000_000
    if g != 1000000 {
        println("FAIL: 1_000_000 should be 1000000, got " + string(g))
        return 1
    }
    println("  PASS: 1_000_000 = " + string(g))

    // ---- Long underscores ----
    println("=== Long Underscores ===")
    big: long = 1_000_000_000L
    if big != 1000000000 {
        println("FAIL: 1_000_000_000L should be 1000000000, got " + string(big))
        return 1
    }
    println("  PASS: 1_000_000_000L = " + string(big))

    // ---- Hex underscores (underscore between two hex digits) ----
    println("=== Hex Underscores ===")

    // Basic hex
    hex1: int = 0xFF
    if hex1 != 255 {
        println("FAIL: 0xFF should be 255, got " + string(hex1))
        return 1
    }
    println("  PASS: 0xFF = " + string(hex1))

    // Hex underscore with 2-digit groups (between hex digits)
    hex2: int = 0xFF_FF
    if hex2 != 65535 {
        println("FAIL: 0xFF_FF should be 65535, got " + string(hex2))
        return 1
    }
    println("  PASS: 0xFF_FF = " + string(hex2))

    // Hex with 3-digit groups
    hex3: int = 0xFFF_FFF
    if hex3 != 16777215 {
        println("FAIL: 0xFFF_FFF should be 16777215, got " + string(hex3))
        return 1
    }
    println("  PASS: 0xFFF_FFF = " + string(hex3))

    // Hex with different groups
    hex4: int = 0xABC_DEF
    if hex4 != 11259375 {
        println("FAIL: 0xABC_DEF should be 11259375, got " + string(hex4))
        return 1
    }
    println("  PASS: 0xABC_DEF = " + string(hex4))

    // Single hex digit group with underscore
    hex5: int = 0xF_F
    if hex5 != 255 {
        println("FAIL: 0xF_F should be 255, got " + string(hex5))
        return 1
    }
    println("  PASS: 0xF_F = " + string(hex5))

    // Hex bitwise with underscore groups
    mask: int = 0xFF_00
    low: int = 0x00_FF
    combined: int = mask | low
    if combined != 0xFF_FF {
        println("FAIL: mask | low should be 0xFF_FF, got " + string(combined))
        return 1
    }
    println("  PASS: 0xFF_00 | 0x00_FF = " + string(combined))

    // ---- Float/double underscores ----
    println("=== Float/Double Underscores ===")

    // Underscore in integer part
    val1: double = 1_000.5
    if val1 < 999.0 || val1 > 1001.0 {
        println("FAIL: 1_000.5 out of range, got " + string(val1))
        return 1
    }
    println("  PASS: 1_000.5 = " + string(val1))

    // Underscores in both parts
    val2: double = 1_000.123_456
    if val2 < 999.0 || val2 > 1001.0 {
        println("FAIL: 1_000.123_456 out of range, got " + string(val2))
        return 1
    }
    println("  PASS: 1_000.123_456 = " + string(val2))

    // Small groups in float
    val3: double = 1_2.3_4
    if val3 < 10.0 || val3 > 15.0 {
        println("FAIL: 1_2.3_4 out of range, got " + string(val3))
        return 1
    }
    println("  PASS: 1_2.3_4 = " + string(val3))

    // ---- Edge cases ----
    println("=== Edge Cases ===")

    // Underscore before type suffix (caught by trailing underscore rule → compiles fine
    // because suffix is not part of the number token)
    big2: long = 100_000L
    if big2 != 100000 {
        println("FAIL: 100_000L should be 100000, got " + string(big2))
        return 1
    }
    println("  PASS: 100_000L = " + string(big2))

    println("=== All underscore tests passed ===")
    return 0
}
