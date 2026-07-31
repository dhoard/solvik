// test/multi_return_test.sol — multiple return value tests
//
// Tests: multi-return assignment, multiple calls, different result types

package test

func divide(a: int, b: int) -> int, int {
    return a / b, a % b
}

func splitName(full: string) -> string, string {
    return full, "Smith"
}

func sumAndDiff(a: int, b: int) -> int, int {
    return a + b, a - b
}

func main() -> int {
    // === basic multi-return assignment ===
    mut q: int
    mut r: int
    q, r = divide(10, 3)
    if q != 3 || r != 1 {
        println("FAIL: divide(10,3) should be 3, 1, got " .. q .. ", " .. r)
    }
    println("quotient=" .. q .. " remainder=" .. r)

    // === multiple calls ===
    q, r = divide(20, 6)
    if q != 3 || r != 2 {
        println("FAIL: divide(20,6) should be 3, 2, got " .. q .. ", " .. r)
    }
    println("second call: q=" .. q .. " r=" .. r)

    // === multi-return with strings ===
    mut first: string
    mut last: string
    first, last = splitName("Alice")
    if first != "Alice" || last != "Smith" {
        println("FAIL: splitName should be Alice, Smith")
    }
    println("name: " .. first .. " " .. last)

    // === multi-return with sum and diff ===
    mut s: int
    mut d: int
    s, d = sumAndDiff(10, 4)
    if s != 14 || d != 6 {
        println("FAIL: sumAndDiff(10,4) should be 14, 6")
    }
    println("sum=" .. s .. " diff=" .. d)

    println("multi_return tests passed")
    return 0
}