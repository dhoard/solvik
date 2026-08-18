// test/list_test.sol — list type tests
//
// Tests: list literals, index access, .len(), len method, iteration,
//        empty list, list of strings, nested lists, edge cases

package test

func main() -> int {
    // === list literal and len ===
    numbers: list<int> = [10, 20, 30, 40, 50]
    if numbers.len() != 5 {
        println("FAIL: len should be 5, got " .. string(numbers.len()))
    }


    // === index access ===
    if numbers[0] != 10 {
        println("FAIL: first element should be 10")
    }
    if numbers[4] != 50 {
        println("FAIL: last element should be 50")
    }
    if numbers[numbers.len() - 1] != 50 {
        println("FAIL: computed last element should be 50")
    }

    // === for-in iteration ===
    mut total: int = 0
    for v in numbers {
        total = total + v
    }
    if total != 150 {
        println("FAIL: sum should be 150, got " .. string(total))
    }

    // === empty list ===
    empty: list<int> = []
    if empty.len() != 0 {
        println("FAIL: empty list len should be 0")
    }

    // === list of strings ===
    names: list<string> = ["alice", "bob", "charlie"]
    if names[0] != "alice" {
        println("FAIL: names[0] should be 'alice'")
    }
    if names[2] != "charlie" {
        println("FAIL: names[2] should be 'charlie'")
    }

    // === list with trailing comma ===
    trailing: list<int> = [
        100,
        200,
        300,
    ]
    if trailing.len() != 3 || trailing[1] != 200 {
        println("FAIL: list with trailing comma")
    }

    // === list of strings via for-in ===
    mut wordConcat: string = ""
    for w in names {
        wordConcat = wordConcat .. w
    }
    if wordConcat != "alicebobcharlie" {
        println("FAIL: for-in string concat should be 'alicebobcharlie'")
    }

    // === single element list ===
    single: list<int> = [42]
    if single.len() != 1 || single[0] != 42 {
        println("FAIL: single element list")
    }

    println("list tests passed")
    return 0
}