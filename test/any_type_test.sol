// test/any_type_test.sol — any type, isType, and lowercase type names
//
// Tests: any type annotation, isType built-in, typeOf lowercase normalization

package test

func identity(val: any) -> any {
    return val
}

func main() -> int {
    // === any type in variable declarations ===

    x: any = 42
    y: any = "hello"
    z: any = [1, 2, 3]

    // === typeOf returns lowercase ===

    if typeOf(x) != "int" {
        println(r#"FAIL: typeOf(x) should be "int", got "# .. typeOf(x))
    }
    if typeOf(y) != "string" {
        println(r#"FAIL: typeOf(y) should be "string", got "# .. typeOf(y))
    }
    if typeOf(z) != "list" {
        println(r#"FAIL: typeOf(z) should be "list", got "# .. typeOf(z))
    }

    // === isType checks ===

    if isType(x, "int") == false {
        println("FAIL: x should be int")
    }
    if isType(y, "string") == false {
        println("FAIL: y should be string")
    }
    if isType(z, "list") == false {
        println("FAIL: z should be list")
    }

    // === isType with all primitive types ===

    if isType(true, "bool") == false {
        println("FAIL: true should be bool")
    }
    if isType(3.14, "float") == false {
        println("FAIL: 3.14 should be float")
    }
    if isType('A', "char") == false {
        println("FAIL: 'A' should be char")
    }
    if isType(null, "null") == false {
        println("FAIL: null should be null")
    }

    // === isType with map ===

    m: map<string, int> = {"a": 1}
    if isType(m, "map") == false {
        println("FAIL: m should be map")
    }

    // === isType returns false for wrong type ===

    if isType(42, "string") {
        println("FAIL: int should not be string")
    }
    if isType("hello", "int") {
        println("FAIL: string should not be int")
    }

    // === downcast from any to concrete type ===

    n: int = x
    if n != 42 {
        println("FAIL: downcast to int should be 42")
    }

    s: string = y
    if s != "hello" {
        println(r#"FAIL: downcast to string should be "hello")"#)
    }

    // === any in function parameter and return ===

    result: any = identity("test")
    if isType(result, "string") == false {
        println(r#"FAIL: identity("test") should be string)"#)
    }

    result2: any = identity(123)
    if isType(result2, "int") == false {
        println("FAIL: identity(123) should be int")
    }

    // === lowercase type names for all types ===

    if typeOf([1]) != "list" {
        println(r#"FAIL: list type should be lowercase "list")"#)
    }
    if typeOf({"a": 1}) != "map" {
        println(r#"FAIL: map type should be lowercase "map")"#)
    }
    if typeOf(regex(r"\d+")) != "regex" {
        println(r#"FAIL: regex type should be lowercase "regex")"#)
    }

    println("any_type tests passed")
    return 0
}
