// test/string_test.sol — string type and method tests
//
// Tests: length, byteLength, charAt, substring, contains, startsWith,
//        endsWith, indexOf, toUpper, toLower, trim, split, join,
//        concatenation, raw strings, escape sequences, empty strings

package test

func main() -> int {
    // === length ===
    if "hello".len() != 5 {
        println("FAIL: length of 'hello' should be 5")
    }
    if "".len() != 0 {
        println("FAIL: length of empty string should be 0")
    }
    // Multi-byte character (UTF-8 count)
    if "héllo".len() != 5 {
        println("FAIL: length of 'héllo' should be 5 (UTF-8 chars), got " .. string("héllo".len()))
    }

    // === byteLength ===
    if "hello".byteLength() != 5 {
        println("FAIL: byteLength of 'hello' should be 5")
    }
    if "".byteLength() != 0 {
        println("FAIL: byteLength of empty string should be 0")
    }
    // Multi-byte character uses more bytes
    if "héllo".byteLength() != 6 {
        println("FAIL: byteLength of 'héllo' should be 6, got " .. string("héllo".byteLength()))
    }

    // === charAt ===
    if "hello".charAt(0) != 'h' {
        println("FAIL: charAt(0) of 'hello' should be 'h'")
    }
    if "hello".charAt(4) != 'o' {
        println("FAIL: charAt(4) of 'hello' should be 'o'")
    }

    // === substring ===
    if "hello world".substring(0, 5) != "hello" {
        println("FAIL: substring(0,5) should be 'hello'")
    }
    if "hello world".substring(6, 11) != "world" {
        println("FAIL: substring(6,11) should be 'world'")
    }
    if "hello".substring(0, 0) != "" {
        println("FAIL: substring(0,0) should be empty")
    }
    if "hello".substring(3, 5) != "lo" {
        println("FAIL: substring(3,5) of 'hello' should be 'lo'")
    }

    // === contains ===
    if "hello world".contains("world") == false {
        println("FAIL: 'hello world' should contain 'world'")
    }
    if "hello world".contains("xyz") {
        println("FAIL: 'hello world' should not contain 'xyz'")
    }
    if "hello".contains("") == false {
        println("FAIL: 'hello' should contain empty string")
    }
    if "".contains("") == false {
        println("FAIL: empty string should contain empty string")
    }

    // === startsWith ===
    if "hello world".startsWith("hello") == false {
        println("FAIL: should start with 'hello'")
    }
    if "hello world".startsWith("world") {
        println("FAIL: should not start with 'world'")
    }
    if "hello".startsWith("") == false {
        println("FAIL: should start with empty string")
    }

    // === endsWith ===
    if "hello world".endsWith("world") == false {
        println("FAIL: should end with 'world'")
    }
    if "hello world".endsWith("hello") {
        println("FAIL: should not end with 'hello'")
    }
    if "hello".endsWith("") == false {
        println("FAIL: should end with empty string")
    }

    // === indexOf ===
    mut pos: int = "hello world".indexOf("world")
    if pos != 6 {
        println("FAIL: indexOf('world') should be 6, got " .. string(pos))
    }
    pos = "hello world".indexOf("hello")
    if pos != 0 {
        println("FAIL: indexOf('hello') should be 0, got " .. string(pos))
    }
    pos = "hello world".indexOf("xyz")
    if pos != -1 {
        println("FAIL: indexOf('xyz') should be -1, got " .. string(pos))
    }
    pos = "hello".indexOf("")
    if pos != 0 {
        println("FAIL: indexOf('') should be 0, got " .. string(pos))
    }

    // === toUpper ===
    if "hello".toUpper() != "HELLO" {
        println("FAIL: toUpper of 'hello' should be 'HELLO'")
    }
    if "Hello World".toUpper() != "HELLO WORLD" {
        println("FAIL: toUpper of 'Hello World' should be 'HELLO WORLD'")
    }
    if "".toUpper() != "" {
        println("FAIL: toUpper of empty should be empty")
    }
    if "123".toUpper() != "123" {
        println("FAIL: toUpper of '123' should be '123'")
    }

    // === toLower ===
    if "HELLO".toLower() != "hello" {
        println("FAIL: toLower of 'HELLO' should be 'hello'")
    }
    if "Hello World".toLower() != "hello world" {
        println("FAIL: toLower of 'Hello World' should be 'hello world'")
    }
    if "".toLower() != "" {
        println("FAIL: toLower of empty should be empty")
    }

    // === trim ===
    if "  hello  ".trim() != "hello" {
        println("FAIL: trim of '  hello  ' should be 'hello'")
    }
    if "hello".trim() != "hello" {
        println("FAIL: trim of 'hello' should be 'hello'")
    }
    if "  ".trim() != "" {
        println("FAIL: trim of spaces should be empty")
    }
    if "".trim() != "" {
        println("FAIL: trim of empty should be empty")
    }
    if "\n\thello\n\t".trim() != "hello" {
        println("FAIL: trim of whitespace should be 'hello'")
    }

    // === split ===
    mut parts: list<string> = "a,b,c".split(",")
    if parts.len() != 3 {
        println("FAIL: split should give 3 parts, got " .. string(parts.len()))
    }
    if parts[0] != "a" || parts[1] != "b" || parts[2] != "c" {
        println("FAIL: split parts incorrect")
    }

    // Split with no separator in string
    parts = "hello".split(",")
    if parts.len() != 1 || parts[0] != "hello" {
        println("FAIL: split with no separator should return single element")
    }

    // Split empty string
    parts = "".split(",")
    if parts.len() != 1 || parts[0] != "" {
        println("FAIL: split empty string should return list with empty string")
    }

    // === join (module function) ===
    words: list<string> = ["a", "b", "c"]
    mut joined: string = string.join(words, "-")
    if joined != "a-b-c" {
        println("FAIL: join should be 'a-b-c', got '" .. joined .. "'")
    }

    // Join with empty separator
    joined = string.join(words, "")
    if joined != "abc" {
        println("FAIL: join with empty sep should be 'abc'")
    }

    // Join single element
    joined = string.join(["only"], ",")
    if joined != "only" {
        println("FAIL: join single element should be 'only'")
    }

    // Join empty list
    joined = string.join([], ",")
    if joined != "" {
        println("FAIL: join empty list should be empty")
    }

    // === concatenation with .. ===
    if "hello" .. " " .. "world" != "hello world" {
        println("FAIL: concatenation failed")
    }
    if "" .. "hello" != "hello" {
        println("FAIL: concat with empty prefix failed")
    }
    if "hello" .. "" != "hello" {
        println("FAIL: concat with empty suffix failed")
    }
    if "" .. "" != "" {
        println("FAIL: concat both empty failed")
    }

    // === method syntax on string literal ===
    if "TEST".toLower() != "test" {
        println("FAIL: method on string literal failed")
    }

    // === raw strings ===
    raw: string = r"C:\path\to\file"
    if raw != "C:\\path\\to\\file" {
        println("FAIL: raw string with backslashes")
    }

    rawq: string = r#"The value is "quoted"."#
    if rawq != "The value is \"quoted\"." {
        println("FAIL: raw string with embedded quotes")
    }

    // === comparison ===
    if "abc" != "abc" {
        println("FAIL: equal strings should be equal")
    }
    if "abc" == "xyz" {
        println("FAIL: different strings should not be equal")
    }

    // === nullable string ===
    mut nmaybe: string? = null
    if nmaybe != null {
        println("FAIL: nullable string should be null")
    }

    // Assign a value
    nmaybe = "assigned"
    if nmaybe != "assigned" {
        println("FAIL: nullable string should equal 'assigned'")
    }

    // === null coalescing on direct null ===
    emptyMaybe: string? = null
    fallback: string = emptyMaybe ?? "default"
    if fallback != "default" {
        println("FAIL: coalesce with null should return default")
    }

    println("string tests passed")
    return 0
}