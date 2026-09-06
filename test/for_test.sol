// test/for_test.sol — loop tests
//
// Tests: for-in on list, while loop, break, continue, nested loops

package test

func main() -> Int {
    // === for-in on list ===
    values: List<Int> = [10, 20, 30, 40, 50]
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    if total != 150 {
        println("FAIL: for-in sum should be 150, got " .. string(total))
    }

    // === for-in on empty list ===
    mut count: Int = 0
    for v in [] {
        count = count + 1
    }
    if count != 0 {
        println("FAIL: for-in on empty list should iterate 0 times")
    }

    // === while loop ===
    mut i: Int = 0
    mut sum: Int = 0
    while i < 10 {
        sum = sum + i
        i = i + 1
    }
    if sum != 45 {
        println("FAIL: while sum should be 45, got " .. string(sum))
    }

    // === while loop (zero iterations) ===
    mut j: Int = 0
    while j > 10 {
        j = j + 1
    }
    if j != 0 {
        println("FAIL: while false should not iterate")
    }

    // === break ===
    mut found: Int = -1
    mut idx: Int = 0
    while idx < values.len() {
        if values[idx] == 30 {
            found = idx
            break
        }
        idx = idx + 1
    }
    if found != 2 {
        println("FAIL: break should find 30 at index 2, got " .. string(found))
    }

    // === break in for-in ===
    mut broke: Int = -1
    mut bi: Int = 0
    for v in values {
        if v == 30 {
            broke = bi
            break
        }
        bi = bi + 1
    }
    if broke != 2 {
        println("FAIL: break in for-in should stop at index 2")
    }

    // === continue ===
    sum = 0
    i = 0
    while i < 10 {
        i = i + 1
        if i % 2 == 0 {
            continue
        }
        sum = sum + i
    }
    // Odd numbers: 1+3+5+7+9 = 25
    if sum != 25 {
        println("FAIL: continue should skip evens, sum=25, got " .. string(sum))
    }

    // === continue in for-in ===
    values2: List<Int> = [1, 2, 3, 4, 5]
    mut positiveSum: Int = 0
    for v in values2 {
        if v < 0 {
            continue
        }
        positiveSum = positiveSum + v
    }
    if positiveSum != 15 {
        println("FAIL: for-in continue sum should be 15, got " .. string(positiveSum))
    }

    // === nested loops ===
    mut product: Int = 0
    mut ai: Int = 0
    while ai < 3 {
        mut bi: Int = 0
        while bi < 3 {
            product = product + 1
            bi = bi + 1
        }
        ai = ai + 1
    }
    if product != 9 {
        println("FAIL: nested loops should yield 9, got " .. string(product))
    }

    // === for-in on list of strings ===
    names: List<String> = ["a", "b", "c"]
    mut concat: String = ""
    for name in names {
        concat = concat .. name
    }
    if concat != "abc" {
        println("FAIL: for-in string concat should be 'abc', got '" .. concat .. "'")
    }

    println("for tests passed")
    return 0
}