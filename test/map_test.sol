// test/map_test.sol — map type tests
//
// Tests: map literal, index access, contains(), empty map, key not found,
//        for-in iteration, map of different types, map with enum keys,
//        map literal with trailing comma

package test

enum Color {
    Red,
    Green,
    Blue,
}

func main() -> int {
    // === map literal and index access ===
    scores: map<string, int> = {"alice": 100, "bob": 200}
    if scores["alice"] != 100 {
        println("FAIL: scores['alice'] should be 100")
    }
    if scores["bob"] != 200 {
        println("FAIL: scores['bob'] should be 200")
    }

    // === contains ===
    if !scores.contains("alice") {
        println("FAIL: should contain 'alice'")
    }
    if scores.contains("charlie") {
        println("FAIL: should not contain 'charlie'")
    }
    if scores.contains("") {
        println("FAIL: should not contain empty key")
    }

    // === for-in iteration over keys ===
    mut total: int = 0
    for name in scores {
        total = total + scores[name]
    }
    if total != 300 {
        println("FAIL: iteration sum should be 300, got " .. string(total))
    }

    // === contains with nonexistent key ===
    if scores.contains("nonexistent") {
        println("FAIL: should not contain nonexistent key")
    }

    // === map of strings ===
    lookup: map<int, string> = {1: "one", 2: "two"}
    if lookup[1] != "one" || lookup[2] != "two" {
        println("FAIL: map<int, string>")
    }

    // === map with enum keys ===
    colorScores: map<Color, int> = {
        Color.Red: 10,
        Color.Green: 20,
        Color.Blue: 30,
    }
    if colorScores[Color.Red] != 10 || colorScores[Color.Blue] != 30 {
        println("FAIL: map with enum keys")
    }
    if !colorScores.contains(Color.Green) {
        println("FAIL: map should contain Green key")
    }

    // === canonical key/value iteration ===
    mut entryTotal: int = 0
    for key, value in scores {
        entryTotal = entryTotal + value
    }
    if entryTotal != 300 {
        println("FAIL: key/value iteration sum should be 300")
        return 1
    }

    // === map with trailing comma ===
    config: map<string, string> = {
        "host": "localhost",
        "port": "8080",
    }
    if config["host"] != "localhost" || config["port"] != "8080" {
        println("FAIL: map with trailing comma")
    }
    if config.len() != 2 {
        println("FAIL: map len should be 2")
        return 1
    }

    println("map tests passed")
    return 0
}
