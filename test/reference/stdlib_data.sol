package reference_stdlib_data
func main() -> int {
    // base64 roundtrip
    enc: string = base64.encode("hello world")
    if base64.decode(enc) != "hello world" {
        return 1
    }
    // hashes (known values)
    if hash.sha256("") != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" {
        return 2
    }
    if hash.md5("abc") != "900150983cd24fb0d6963f7d28e17f72" {
        return 3
    }
    // json roundtrip
    s: string = json.stringify({ "a": 1, "b": [true, null, "x"], "c": 1.5 })
    parsed: any = json.parse(s)
    m: map<string, any> = parsed
    if m["a"] != 1 {
        return 4
    }
    arr: list<any> = m["b"]
    if arr.len() != 3 || arr[0] != true || arr[1] != null || arr[2] != "x" {
        return 5
    }
    if m["c"] != 1.5 {
        return 6
    }
    if json.stringify(parsed) != s {
        return 7
    }
    // typed json access via downcast
    obj: map<string, any> = json.parse("{\"name\": \"solvik\", \"version\": 1}")
    if obj["name"] != "solvik" || obj["version"] != 1 {
        return 8
    }
    // string helpers
    if string.repeat("ab", 3) != "ababab" {
        return 9
    }
    if string.padStart("7", 3, "0") != "007" {
        return 10
    }
    if string.padEnd("7", 3, "0") != "700" {
        return 11
    }
    if string.join(["a", "b", "c"], "-") != "a-b-c" {
        return 12
    }
    // time roundtrip
    ms: int = time.parse("2024-01-15T10:30:00Z")
    if time.iso(ms) != "2024-01-15T10:30:00Z" {
        return 13
    }
    // process.args is empty for direct invocation
    if process.args().len() != 0 {
        return 14
    }
    return 0
}
