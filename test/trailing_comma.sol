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
    pub x: int,
    pub y: int,
}

func combine(a: string, b: string) -> string {
    return a .. b
}

func combineThree(a: string, b: string, c: string) -> string {
    return a .. b .. c
}

func greet() -> string {
    return "hello"
}

func identity(x: string) -> string {
    return x
}

func main() -> int {
    // === trailing comma in function calls ===

    // Single argument with trailing comma
    print("hello",)

    // Multiple arguments with trailing comma
    mut result: string = combine("a", "b",)
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

    numbers: list<int> = [10, 20, 30,]
    if numbers.len() != 3 || numbers[0] != 10 || numbers[2] != 30 {
        println("FAIL: list with trailing comma")
    }

    single: list<int> = [42,]
    if single.len() != 1 || single[0] != 42 {
        println("FAIL: single-element list with trailing comma")
    }

    emptyList: list<int> = []
    if emptyList.len() != 0 {
        println("FAIL: empty list should have len 0")
    }

    // === trailing comma in map literals ===

    scores: map<string, int> = {
        "alice": 100,
        "bob": 200,
    }
    if scores["alice"] != 100 || scores["bob"] != 200 {
        println("FAIL: map with trailing comma")
    }

    singleMap: map<string, int> = {"key": 42,}
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
