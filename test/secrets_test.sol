// test/secrets_test.sol — built-in secrets module tests
//
// Tests: secrets.token, secrets.hex

package test

func main() -> int {
    // === secrets.token — URL-safe base64 output ===

    t: string = secrets.token(32)
    if t == "" {
        println("FAIL: secrets.token returned empty")
    }

    // Two calls should produce different tokens
    t2: string = secrets.token(32)
    if t == t2 {
        println("FAIL: secrets.token should produce unique tokens")
    }

    // secrets.token(32) should be approximately 43 chars (ceil(32*4/3))
    tLen: int = t.length()
    if tLen < 40 || tLen > 46 {
        println("FAIL: secrets.token(32) length expected ~43, got " .. string(tLen))
    }

    // secrets.token(16) should be approximately 22 chars
    t16: string = secrets.token(16)
    t16Len: int = t16.length()
    if t16Len < 20 || t16Len > 24 {
        println("FAIL: secrets.token(16) length expected ~22, got " .. string(t16Len))
    }

    // === secrets.hex — hex output ===

    h: string = secrets.hex(16)
    if h == "" {
        println("FAIL: secrets.hex returned empty")
    }

    // secrets.hex(16) should be exactly 32 hex chars
    hLen: int = h.length()
    if hLen != 32 {
        println("FAIL: secrets.hex(16) length expected 32, got " .. string(hLen))
    }

    // Two calls should produce different hex strings
    h2: string = secrets.hex(16)
    if h == h2 {
        println("FAIL: secrets.hex should produce unique tokens")
    }

    // secrets.hex(8) should be exactly 16 hex chars
    h8: string = secrets.hex(8)
    if h8.length() != 16 {
        println("FAIL: secrets.hex(8) length expected 16, got " .. h8.length())
    }

    // secrets.hex(1) should be exactly 2 hex chars
    h1: string = secrets.hex(1)
    if h1.length() != 2 {
        println("FAIL: secrets.hex(1) length expected 2, got " .. h1.length())
    }

    // secrets.hex(32) should be exactly 64 hex chars
    h32: string = secrets.hex(32)
    if h32.length() != 64 {
        println("FAIL: secrets.hex(32) length expected 64, got " .. h32.length())
    }

    println("secrets tests passed")
    return 0
}
