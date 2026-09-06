// byte_test.sol -- Tests for byte type and byte() conversion
package example

func testBasicByte() -> Int {
    // byte from valid int
    b1: Byte = byte(0)
    if b1 != 0 {
        return 1
    }
    b2: Byte = byte(255)
    if b2 != 255 {
        return 2
    }
    b3: Byte = byte(128)
    if b3 != 128 {
        return 3
    }
    return 0
}

func testByteArithmetic() -> Int {
    a: Byte = byte(200)
    b: Byte = byte(100)

    // byte arithmetic promotes to int
    sum: Int = a + b
    if sum != 300 {
        return 1
    }

    // mixed byte and int
    mixed: Int = a + 50
    if mixed != 250 {
        return 2
    }

    return 0
}

func testByteList() -> Int {
    data: List<Byte> = [byte(10), byte(20), byte(30)]
    if data.len() != 3 {
        return 1
    }
    if data[0] != 10 {
        return 2
    }
    if data[2] != 30 {
        return 3
    }
    return 0
}

func testByteFromFloat() -> Int {
    // byte from float (truncation toward zero)
    b1: Byte = byte(42.9)
    if b1 != 42 {
        return 1
    }
    b2: Byte = byte(10.1)
    if b2 != 10 {
        return 2
    }
    return 0
}

func main() -> Int {
    mut result: Int = 0

    result = testBasicByte()
    if result != 0 {
        println("FAIL testBasicByte: " .. result)
        return result
    }
    println("PASS testBasicByte")

    result = testByteArithmetic()
    if result != 0 {
        println("FAIL testByteArithmetic: " .. result)
        return result
    }
    println("PASS testByteArithmetic")

    result = testByteList()
    if result != 0 {
        println("FAIL testByteList: " .. result)
        return result
    }
    println("PASS testByteList")

    result = testByteFromFloat()
    if result != 0 {
        println("FAIL testByteFromFloat: " .. result)
        return result
    }
    println("PASS testByteFromFloat")

    println("ALL BYTE TESTS PASSED")
    return 0
}
