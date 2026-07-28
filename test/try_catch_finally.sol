package test

func testBasicTryCatch() -> int {
    mut result: int = 0
    try {
        result = 1
    } catch (e: exception) {
        result = 2
    }
    return result
}

func testBasicThrow() -> int {
    mut result: int = 0
    try {
        throw "something went wrong"
        result = 1 // should not execute
    } catch (e: exception) {
        result = 2
    }
    return result
}

func testTryFinally() -> int {
    mut result: int = 0
    try {
        result = 1
    } finally {
        result = 2
    }
    return result
}

func testThrowWithCatchFinally() -> int {
    mut catchHit: int = 0
    mut finallyHit: int = 0
    try {
        throw "error"
    } catch (e: exception) {
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

func testThrowInCatch() -> int {
    mut result: int = 0
    try {
        throw "first error"
    } catch (e: exception) {
        try {
            throw "second error"
        } catch (e2: exception) {
            result = 1
        }
    }
    return result
}

func testNestedTry() -> int {
    mut result: int = 0
    try {
        try {
            throw "inner error"
        } catch (e: exception) {
            result = 1
        }
    } finally {
        result = 2
    }
    return result
}

func testExceptionMessage() -> string {
    mut msgValue: string = ""
    try {
        throw "my error message"
    } catch (e: exception) {
        msgValue = e.message
    }
    return msgValue
}

func testDivisionByZeroCaught() -> int {
    mut result: int = 0
    try {
        x: int = 10
        y: int = 0
        z: int = x / y
        // z is never assigned due to exception
    } catch (e: exception) {
        result = 1
    }
    return result
}

func testFinallyAlwaysExecutes() -> int {
    mut finallyCount: int = 0
    try {
        throw "error"
    } catch (e: exception) {
        // caught
    } finally {
        finallyCount = 1
    }
    return finallyCount
}

func testReturnFromTryWithFinally() -> int {
    try {
        return 100
    } finally {
        // This should execute before return
    }
    return 0
}

func testThrowNullShouldFail() -> int {
    // This test verifies the compiler rejects throw null
    // We can't test compiler errors at runtime, so just return 0
    return 0
}

func testFinallySupersedesReturn() -> int {
    try {
        return 10
    } finally {
        return 20
    }
    return 0
}

func testExceptionAcrossFunctions() -> int {
    try {
        riskyFunction()
    } catch (e: exception) {
        if e.message == "error from function" {
            return 1
        }
    }
    return 0
}

func riskyFunction() {
    throw "error from function"
}

func testFinallySupersedesException() -> string {
    mut result: string = ""
    try {
        throw "original error"
    } catch (e: exception) {
        result = "catch:" .. e.message
    } finally {
        result = result .. ":finally"
        throw "finally error"
    }
    // Never reached because finally throws
    return result
}

func main() -> int {
    // Test 1: Basic try/catch - normal completion
    r1: int = testBasicTryCatch()
    if r1 != 1 {
        println("FAIL: testBasicTryCatch expected 1, got " .. r1)
        return 1
    }
    println("PASS: testBasicTryCatch")

    // Test 2: Basic throw caught
    r2: int = testBasicThrow()
    if r2 != 2 {
        println("FAIL: testBasicThrow expected 2, got " .. r2)
        return 1
    }
    println("PASS: testBasicThrow")

    // Test 3: Try/finally normal completion
    r3: int = testTryFinally()
    if r3 != 2 {
        println("FAIL: testTryFinally expected 2, got " .. r3)
        return 1
    }
    println("PASS: testTryFinally")

    // Test 4: Throw with catch and finally
    // Both catch and finally should execute
    r4: int = testThrowWithCatchFinally()
    if r4 != 1 {
        println("FAIL: testThrowWithCatchFinally expected 1, got " .. r4)
        return 1
    }
    println("PASS: testThrowWithCatchFinally")

    // Test 5: Throw in catch
    r5: int = testThrowInCatch()
    if r5 != 1 {
        println("FAIL: testThrowInCatch expected 1, got " .. r5)
        return 1
    }
    println("PASS: testThrowInCatch")

    // Test 6: Nested try
    r6: int = testNestedTry()
    if r6 != 2 {
        println("FAIL: testNestedTry expected 2, got " .. r6)
        return 1
    }
    println("PASS: testNestedTry")

    // Test 7: Exception message
    r7: string = testExceptionMessage()
    if r7 != "my error message" {
        println("FAIL: testExceptionMessage expected 'my error message', got '" .. r7 .. "'")
        return 1
    }
    println("PASS: testExceptionMessage")

    // Test 8: Division by zero caught
    r8: int = testDivisionByZeroCaught()
    if r8 != 1 {
        println("FAIL: testDivisionByZeroCaught expected 1, got " .. r8)
        return 1
    }
    println("PASS: testDivisionByZeroCaught")

    // Test 9: Finally always executes
    r9: int = testFinallyAlwaysExecutes()
    if r9 != 1 {
        println("FAIL: testFinallyAlwaysExecutes expected 1, got " .. r9)
        return 1
    }
    println("PASS: testFinallyAlwaysExecutes")

    // Test 10: Return from try with finally
    r10: int = testReturnFromTryWithFinally()
    if r10 != 100 {
        println("FAIL: testReturnFromTryWithFinally expected 100, got " .. r10)
        return 1
    }
    println("PASS: testReturnFromTryWithFinally")

    // Test 11: Finally supersedes return
    r11: int = testFinallySupersedesReturn()
    if r11 != 20 {
        println("FAIL: testFinallySupersedesReturn expected 20, got " .. r11)
        return 1
    }
    println("PASS: testFinallySupersedesReturn")

    // Test 12: Exception propagation across functions
    r12: int = testExceptionAcrossFunctions()
    if r12 != 1 {
        println("FAIL: testExceptionAcrossFunctions expected 1, got " .. r12)
        return 1
    }
    println("PASS: testExceptionAcrossFunctions")

    // Test 13: Finally supersedes exception (catch runs, then finally throws)
    mut r13: string = ""
    try {
        r13 = testFinallySupersedesException()
    } catch (e: exception) {
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
