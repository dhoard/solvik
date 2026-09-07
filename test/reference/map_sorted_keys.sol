package map_sorted_keys

// Map keys are accessed in canonical sorted order (TreeMap semantics), never
// insertion order: type rank first (bool < numbers < char < string < enum),
// then the value within the type.  Covers one-binding iteration, two-binding
// iteration, out-of-order literals, numeric (not lexicographic) int ordering,
// update stability, and json.parse/stringify round trips.

enum Color {
    Blue,
    Red,
    Green,
}

func main() -> Int {
    // Out-of-order inserts come out sorted on every key access.
    mut m: Map<String, Int> = Map.new()
    m["banana"] = 2
    m["apple"] = 1
    m["cherry"] = 3
    mut keys: String = ""
    for k in m {
        keys = keys .. k
    }
    test.assertEq(keys, "applebananacherry")
    mut pairs: String = ""
    for k, v in m {
        pairs = pairs .. k .. string(v)
    }
    test.assertEq(pairs, "apple1banana2cherry3")
    test.assertEq(json.stringify(m), "{\"apple\": 1, \"banana\": 2, \"cherry\": 3}")

    // Updating an existing key keeps its sorted position.
    m["apple"] = 100
    test.assertEq(json.stringify(m), "{\"apple\": 100, \"banana\": 2, \"cherry\": 3}")

    // Out-of-order literals are stored sorted too.
    lit: Map<String, String> = { "z": "1", "a": "2", "m": "3" }
    test.assertEq(json.stringify(lit), "{\"a\": \"2\", \"m\": \"3\", \"z\": \"1\"}")

    // Int keys sort numerically, not lexicographically.
    nums: Map<Int, String> = { 10: "ten", 2: "two", 1: "one" }
    mut nv: String = ""
    for k, v in nums {
        nv = nv .. v
    }
    test.assertEq(nv, "onetwoten")

    // Mixed key types order by type rank: bool < number < char < string.
    mixed: Map<Any, Int> = { "s": 1, 'b': 2, 1.5: 3, true: 4, 2: 5 }
    mut mv: String = ""
    for k, v in mixed {
        mv = mv .. string(v)
    }
    test.assertEq(mv, "43521")

    // Enum keys sort by their string form: Blue < Green < Red.
    colors: Map<Color, Int> = { Color.Red: 1, Color.Blue: 2, Color.Green: 3 }
    mut cv: String = ""
    for k, v in colors {
        cv = cv .. string(v)
    }
    test.assertEq(cv, "231")

    // json.parse builds maps with sorted keys; stringify round trips them.
    parsed: Any = json.parse("{\"z\": 1, \"a\": { \"y\": 1, \"b\": 2 }}")
    test.assertEq(json.stringify(parsed), "{\"a\": {\"b\": 2, \"y\": 1}, \"z\": 1}")

    println("map sorted keys passed")
    return 0
}
