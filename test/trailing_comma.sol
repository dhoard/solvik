// test/trailing_comma.sol — trailing comma tests
//
// Tests: trailing commas in function calls, list literals, map literals,
//        enum definitions, struct definitions

package test

// === enum with trailing comma ===
enum Color {
    Red,
    Green,
    Blue,
}

// === struct with trailing comma ===
struct Point {
    pub x: Int,
    pub y: Int,
}

func combine(a: String, b: String) -> String {
    return a .. b
}

func combineThree(a: String, b: String, c: String) -> String {
    return a .. b .. c
}

func greet() -> String {
    return "hello"
}

func identity(x: String) -> String {
    return x
}

func main() -> Int {
    // === trailing comma in function calls ===

    // Single argument with trailing comma
    print("hello",)

    // Multiple arguments with trailing comma
    mut result: String = combine("a", "b",)
    if result != "ab" {
        println("FAIL: combine with trailing comma")
    }

    // Three arguments with trailing comma
    result = combineThree("x", "y", "z",)
    if result != "xyz" {
        println("FAIL: combineThree with trailing comma")
    }

    // Multiline with trailing comma
    result = combineThree(
        "a",
        "b",
        "c",
    )
    if result != "abc" {
        println("FAIL: multiline with trailing comma")
    }

    // Expression as final arg with trailing comma
    result = combine("hello", string(42),)
    if result != "hello42" {
        println("FAIL: expression with trailing comma")
    }

    // Nested calls with trailing commas
    result = combine(
        identity("inner",),
        "outer",
    )
    if result != "innerouter" {
        println("FAIL: nested calls with trailing commas")
    }

    // === trailing comma in list literals ===

    numbers: List<Int> = [10, 20, 30,]
    if numbers.len() != 3 || numbers[0] != 10 || numbers[2] != 30 {
        println("FAIL: list with trailing comma")
    }

    single: List<Int> = [42,]
    if single.len() != 1 || single[0] != 42 {
        println("FAIL: single-element list with trailing comma")
    }

    emptyList: List<Int> = []
    if emptyList.len() != 0 {
        println("FAIL: empty list should have len 0")
    }

    // === trailing comma in map literals ===

    scores: Map<String, Int> = {
        "alice": 100,
        "bob": 200,
    }
    if scores["alice"] != 100 || scores["bob"] != 200 {
        println("FAIL: map with trailing comma")
    }

    singleMap: Map<String, Int> = {"key": 42,}
    if singleMap["key"] != 42 {
        println("FAIL: single-element map with trailing comma")
    }

    // === trailing comma in enum ===

    if int(Color.Red) != 0 || int(Color.Green) != 1 || int(Color.Blue) != 2 {
        println("FAIL: enum with trailing comma")
    }

    // === trailing comma in struct ===

    p: Point = Point { x: 3, y: 4 }
    if p.x != 3 || p.y != 4 {
        println("FAIL: struct with trailing comma")
    }

    println("trailing comma tests passed")
    return 0
}
