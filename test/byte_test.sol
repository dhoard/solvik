// byte_test.sol -- Tests for byte type and byte() conversion
package example

func testBasicByte() -> int {
    // byte from valid int
    b1: byte = byte(0)
    if b1 != 0 {
        return 1
    }
    b2: byte = byte(255)
    if b2 != 255 {
        return 2
    }
    b3: byte = byte(128)
    if b3 != 128 {
        return 3
    }
    return 0
}

func testByteArithmetic() -> int {
    a: byte = byte(200)
    b: byte = byte(100)

    // byte arithmetic promotes to int
    sum: int = a + b
    if sum != 300 {
        return 1
    }

    // mixed byte and int
    mixed: int = a + 50
    if mixed != 250 {
        return 2
    }

    return 0
}

func testByteList() -> int {
    data: list<byte> = [byte(10), byte(20), byte(30)]
    if len(data) != 3 {
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

func testByteFromFloat() -> int {
    // byte from float (truncation toward zero)
    b1: byte = byte(42.9)
    if b1 != 42 {
        return 1
    }
    b2: byte = byte(10.1)
    if b2 != 10 {
        return 2
    }
    return 0
}

func main() -> int {
    mut result: int = 0

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
