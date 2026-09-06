package test

func testBasicTryCatch() -> Int {
    mut result: Int = 0
    try {
        result = 1
    } catch (e: Exception) {
        result = 2
    }
    return result
}

func testBasicThrow() -> Int {
    mut result: Int = 0
    try {
        throw "something went wrong"
    } catch (e: Exception) {
        result = 2
    }
    return result
}

func testTryFinally() -> Int {
    mut result: Int = 0
    try {
        result = 1
    } finally {
        result = 2
    }
    return result
}

func testThrowWithCatchFinally() -> Int {
    mut catchHit: Int = 0
    mut finallyHit: Int = 0
    try {
        throw "error"
    } catch (e: Exception) {
        catchHit = 1
    } finally {
        finallyHit = 1
    }
    if catchHit != 1 {
        return 0
    }
    if finallyHit != 1 {
        return 0
    }
    return 1
}

func testThrowInCatch() -> Int {
    mut result: Int = 0
    try {
        throw "first error"
    } catch (e: Exception) {
        try {
            throw "second error"
        } catch (e2: Exception) {
            result = 1
        }
    }
    return result
}

func testNestedTry() -> Int {
    mut result: Int = 0
    try {
        try {
            throw "inner error"
        } catch (e: Exception) {
            result = 1
        }
    } finally {
        result = 2
    }
    return result
}

func testExceptionMessage() -> String {
    mut msgValue: String = ""
    try {
        throw "my error message"
    } catch (e: Exception) {
        msgValue = e.message
    }
    return msgValue
}

func testDivisionByZeroCaught() -> Int {
    mut result: Int = 0
    try {
        x: Int = 10
        y: Int = 0
        z: Int = x / y
        // z is never assigned due to exception
    } catch (e: Exception) {
        result = 1
    }
    return result
}

func testFinallyAlwaysExecutes() -> Int {
    mut finallyCount: Int = 0
    try {
        throw "error"
    } catch (e: Exception) {
        // caught
    } finally {
        finallyCount = 1
    }
    return finallyCount
}

func testReturnFromTryWithFinally() -> Int {
    try {
        return 100
    } finally {
        // This should execute before return
    }
    return 0
}

func testThrowNullShouldFail() -> Int {
    // This test verifies the compiler rejects throw null
    // We can't test compiler errors at runtime, so just return 0
    return 0
}

func testFinallySupersedesReturn() -> Int {
    try {
        return 10
    } finally {
        return 20
    }
    return 0
}

func testExceptionAcrossFunctions() -> Int {
    try {
        riskyFunction()
    } catch (e: Exception) {
        if e.message == "error from function" {
            return 1
        }
    }
    return 0
}

func riskyFunction() {
    throw "error from function"
}

func testFinallySupersedesException() -> String {
    mut result: String = ""
    try {
        throw "original error"
    } catch (e: Exception) {
        result = "catch:" .. e.message
    } finally {
        result = result .. ":finally"
        throw "finally error"
    }
    // Never reached because finally throws
    return result
}

func main() -> Int {
    // Test 1: Basic try/catch - normal completion
    r1: Int = testBasicTryCatch()
    if r1 != 1 {
        println("FAIL: testBasicTryCatch expected 1, got " .. r1)
        return 1
    }
    println("PASS: testBasicTryCatch")

    // Test 2: Basic throw caught
    r2: Int = testBasicThrow()
    if r2 != 2 {
        println("FAIL: testBasicThrow expected 2, got " .. r2)
        return 1
    }
    println("PASS: testBasicThrow")

    // Test 3: Try/finally normal completion
    r3: Int = testTryFinally()
    if r3 != 2 {
        println("FAIL: testTryFinally expected 2, got " .. r3)
        return 1
    }
    println("PASS: testTryFinally")

    // Test 4: Throw with catch and finally
    // Both catch and finally should execute
    r4: Int = testThrowWithCatchFinally()
    if r4 != 1 {
        println("FAIL: testThrowWithCatchFinally expected 1, got " .. r4)
        return 1
    }
    println("PASS: testThrowWithCatchFinally")

    // Test 5: Throw in catch
    r5: Int = testThrowInCatch()
    if r5 != 1 {
        println("FAIL: testThrowInCatch expected 1, got " .. r5)
        return 1
    }
    println("PASS: testThrowInCatch")

    // Test 6: Nested try
    r6: Int = testNestedTry()
    if r6 != 2 {
        println("FAIL: testNestedTry expected 2, got " .. r6)
        return 1
    }
    println("PASS: testNestedTry")

    // Test 7: Exception message
    r7: String = testExceptionMessage()
    if r7 != "my error message" {
        println("FAIL: testExceptionMessage expected 'my error message', got '" .. r7 .. "'")
        return 1
    }
    println("PASS: testExceptionMessage")

    // Test 8: Division by zero caught
    r8: Int = testDivisionByZeroCaught()
    if r8 != 1 {
        println("FAIL: testDivisionByZeroCaught expected 1, got " .. r8)
        return 1
    }
    println("PASS: testDivisionByZeroCaught")

    // Test 9: Finally always executes
    r9: Int = testFinallyAlwaysExecutes()
    if r9 != 1 {
        println("FAIL: testFinallyAlwaysExecutes expected 1, got " .. r9)
        return 1
    }
    println("PASS: testFinallyAlwaysExecutes")

    // Test 10: Return from try with finally
    r10: Int = testReturnFromTryWithFinally()
    if r10 != 100 {
        println("FAIL: testReturnFromTryWithFinally expected 100, got " .. r10)
        return 1
    }
    println("PASS: testReturnFromTryWithFinally")

    // Test 11: Finally supersedes return
    r11: Int = testFinallySupersedesReturn()
    if r11 != 20 {
        println("FAIL: testFinallySupersedesReturn expected 20, got " .. r11)
        return 1
    }
    println("PASS: testFinallySupersedesReturn")

    // Test 12: Exception propagation across functions
    r12: Int = testExceptionAcrossFunctions()
    if r12 != 1 {
        println("FAIL: testExceptionAcrossFunctions expected 1, got " .. r12)
        return 1
    }
    println("PASS: testExceptionAcrossFunctions")

    // Test 13: Finally supersedes exception (catch runs, then finally throws)
    mut r13: String = ""
    try {
        r13 = testFinallySupersedesException()
    } catch (e: Exception) {
        r13 = "caught:" .. e.message
    }
    if r13 != "caught:finally error" {
        println("FAIL: testFinallySupersedesException expected 'caught:finally error', got '" .. r13 .. "'")
        return 1
    }
    println("PASS: testFinallySupersedesException")

    println("ALL TESTS PASSED")
    return 0
}
