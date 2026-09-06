package json_stringify_format

// json.stringify must produce identical text on every backend: the Python
// reference's json.dumps defaults (", " and ": " separators, insertion
// order, ensure_ascii escaping, Python float repr).

func main() -> Int {
    test.assertEq(json.stringify({ "a": 1, "b": [true, null, "x"], "c": 1.5 }), "{\"a\": 1, \"b\": [true, null, \"x\"], \"c\": 1.5}")
    test.assertEq(json.stringify(1.0), "1.0")
    test.assertEq(json.stringify(1e16), "1e+16")
    test.assertEq(json.stringify(0.00001), "1e-05")
    test.assertEq(json.stringify({ 1: "a" }), "{\"1\": \"a\"}")
    test.assertEq(json.stringify("caf\u00e9"), "\"caf\\u00e9\"")
    try {
        json.stringify(byte(7))
        return 1
    } catch (e: Exception) {
        test.assertEq(e.message, "value of type Byte is not representable as JSON")
    }
    println("json stringify format passed")
    return 0
}
