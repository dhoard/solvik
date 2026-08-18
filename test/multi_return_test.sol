// test/multi_return_test.sol — result struct return type tests
//
// Tests: Using struct types to return multiple values

package test

struct DivisionResult {
    pub quotient: int
    pub remainder: int
}

struct NameResult {
    pub first: string
    pub last: string
}

struct SumAndDiffResult {
    pub sum: int
    pub diff: int
}

func divide(a: int, b: int) -> DivisionResult {
    return DivisionResult {
        quotient: a / b,
        remainder: a % b,
    }
}

func splitName(full: string) -> NameResult {
    return NameResult {
        first: full,
        last: "Smith",
    }
}

func sumAndDiff(a: int, b: int) -> SumAndDiffResult {
    return SumAndDiffResult {
        sum: a + b,
        diff: a - b,
    }
}

func main() -> int {
    // === basic result struct usage ===
    result1: DivisionResult = divide(10, 3)
    if result1.quotient != 3 || result1.remainder != 1 {
        println("FAIL: divide(10,3) should be 3, 1, got " .. result1.quotient .. ", " .. result1.remainder)
    }
    println("quotient=" .. result1.quotient .. " remainder=" .. result1.remainder)

    // === multiple calls ===
    result2: DivisionResult = divide(20, 6)
    if result2.quotient != 3 || result2.remainder != 2 {
        println("FAIL: divide(20,6) should be 3, 2, got " .. result2.quotient .. ", " .. result2.remainder)
    }
    println("second call: q=" .. result2.quotient .. " r=" .. result2.remainder)

    // === result struct with strings ===
    nameResult: NameResult = splitName("Alice")
    if nameResult.first != "Alice" || nameResult.last != "Smith" {
        println("FAIL: splitName should be Alice, Smith")
    }
    println("name: " .. nameResult.first .. " " .. nameResult.last)

    // === result struct with sum and diff ===
    mathResult: SumAndDiffResult = sumAndDiff(10, 4)
    if mathResult.sum != 14 || mathResult.diff != 6 {
        println("FAIL: sumAndDiff(10,4) should be 14, 6")
    }
    println("sum=" .. mathResult.sum .. " diff=" .. mathResult.diff)

    println("result struct tests passed")
    return 0
}