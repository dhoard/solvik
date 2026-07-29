// test/hash_test.sol — built-in hash module tests
//
// Tests: hash.md5, hash.sha1, hash.sha256, hash.sha512

package test

func main() -> int {
    // === hash.md5 ===

    md5: string = hash.md5("hello")
    if md5 != "5d41402abc4b2a76b9719d911017c592" {
        println("FAIL: hash.md5 expected 5d41402abc4b2a76b9719d911017c592, got " .. md5)
    }

    // === hash.sha1 ===

    sha1: string = hash.sha1("hello")
    if sha1 != "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d" {
        println("FAIL: hash.sha1 expected aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d, got " .. sha1)
    }

    // === hash.sha256 ===

    sha256: string = hash.sha256("hello")
    if sha256 != "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" {
        println("FAIL: hash.sha256 expected 2cf24dba..., got " .. sha256)
    }

    // === hash.sha512 ===

    sha512: string = hash.sha512("hello")
    if sha512 != "9b71d224bd62f3785d96d46ad3ea3d73319bfbc2890caadae2dff72519673ca72323c3d99ba5c11d7c7acc6e14b8c5da0c4663475c2e5c3adef46f73bcdec043" {
        println("FAIL: hash.sha512 expected 9b71d224..., got " .. sha512)
    }

    // === empty string hashes ===

    emptyMd5: string = hash.md5("")
    if emptyMd5 != "d41d8cd98f00b204e9800998ecf8427e" {
        println(r#"FAIL: hash.md5("") expected d41d8cd9..., got "# .. emptyMd5)
    }

    // === different inputs produce different hashes ===

    a: string = hash.sha256("hello")
    b: string = hash.sha256("world")
    if a == b {
        println("FAIL: different inputs should produce different hashes")
    }

    // === same input produces same hash (deterministic) ===

    c: string = hash.sha256("test")
    d: string = hash.sha256("test")
    if c != d {
        println("FAIL: same input should produce same hash")
    }

    // === hash length checks ===

    if hash.md5("").length() != 32 {
        println("FAIL: md5 should be 32 hex chars")
    }
    if hash.sha1("").length() != 40 {
        println("FAIL: sha1 should be 40 hex chars")
    }
    if hash.sha256("").length() != 64 {
        println("FAIL: sha256 should be 64 hex chars")
    }
    if hash.sha512("").length() != 128 {
        println("FAIL: sha512 should be 128 hex chars")
    }

    println("hash tests passed")
    return 0
}
