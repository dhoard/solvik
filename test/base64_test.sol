// test/base64_test.sol — built-in base64 module tests
//
// Tests: base64.encode, base64.decode

package test

func main() -> Int {
    // === base64.encode ===

    e: String = base64.encode("Hello, Solvik!")
    if e != "SGVsbG8sIFNvbHZpayE=" {
        println("FAIL: base64.encode expected SGVsbG8sIFNvbHZpayE=, got " .. e)
    }

    // === base64.decode ===

    d: String = base64.decode("SGVsbG8sIFNvbHZpayE=")
    if d != "Hello, Solvik!" {
        println("FAIL: base64.decode expected Hello, Solvik!, got " .. d)
    }

    // === roundtrip: encode then decode ===

    original: String = "The quick brown fox jumps over the lazy dog"
    encoded: String = base64.encode(original)
    decoded: String = base64.decode(encoded)
    if decoded != original {
        println("FAIL: roundtrip mismatch: expected " .. original .. ", got " .. decoded)
    }

    // === encode empty string ===

    empty: String = base64.encode("")
    if empty != "" {
        println("FAIL: encode empty should be empty, got " .. empty)
    }

    // === decode empty string ===

    emptyDecoded: String = base64.decode("")
    if emptyDecoded != "" {
        println("FAIL: decode empty should be empty, got " .. emptyDecoded)
    }

    // === encode string with special characters ===

    special: String = base64.encode("abc123!@#$%^&*()")
    if special == "" {
        println("FAIL: encode special chars returned empty")
    }
    roundtrip: String = base64.decode(special)
    if roundtrip != "abc123!@#$%^&*()" {
        println("FAIL: special chars roundtrip mismatch")
    }

    // === encode unicode / multi-byte ===

    multi: String = base64.encode("Hello 世界")
    decodedMulti: String = base64.decode(multi)
    if decodedMulti != "Hello 世界" {
        println("FAIL: multi-byte roundtrip mismatch")
    }

    println("base64 tests passed")
    return 0
}
