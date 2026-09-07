// test/any_type_test.sol — any type, isType, and canonical type names
//
// Tests: any type annotation, isType built-in, typeOf canonical spelling

package test

func identity(val: Any) -> Any {
    return val
}

func main() -> Int {
    // === any type in variable declarations ===

    x: Any = 42
    y: Any = "hello"
    z: Any = [1, 2, 3]

    // === typeOf returns canonical names ===

    if typeOf(x) != "Int" {
        println(r#"FAIL: typeOf(x) should be "int", got "# .. typeOf(x))
    }
    if typeOf(y) != "String" {
        println(r#"FAIL: typeOf(y) should be "string", got "# .. typeOf(y))
    }
    if typeOf(z) != "List" {
        println(r#"FAIL: typeOf(z) should be "list", got "# .. typeOf(z))
    }

    // === isType checks ===

    if isType(x, "Int") == false {
        println("FAIL: x should be int")
    }
    if isType(y, "String") == false {
        println("FAIL: y should be string")
    }
    if isType(z, "List") == false {
        println("FAIL: z should be list")
    }

    // === isType with all primitive types ===

    if isType(true, "Bool") == false {
        println("FAIL: true should be bool")
    }
    if isType(3.14, "Float") == false {
        println("FAIL: 3.14 should be float")
    }
    if isType('A', "Char") == false {
        println("FAIL: 'A' should be char")
    }
    if isType(null, "null") == false {
        println("FAIL: null should be null")
    }

    // === isType with map ===

    m: Map<String, Int> = {"a": 1}
    if isType(m, "Map") == false {
        println("FAIL: m should be map")
    }

    // === isType returns false for wrong type ===

    if isType(42, "String") {
        println("FAIL: int should not be string")
    }
    if isType("hello", "Int") {
        println("FAIL: string should not be int")
    }

    // === downcast from any to concrete type ===

    n: Int = x
    if n != 42 {
        println("FAIL: downcast to int should be 42")
    }

    s: String = y
    if s != "hello" {
        println(r#"FAIL: downcast to string should be "hello")"#)
    }

    // === any in function parameter and return ===

    result: Any = identity("test")
    if isType(result, "String") == false {
        println(r#"FAIL: identity("test") should be string)"#)
    }

    result2: Any = identity(123)
    if isType(result2, "Int") == false {
        println("FAIL: identity(123) should be int")
    }

    // === canonical type names for all types ===

    if typeOf([1]) != "List" {
        println(r#"FAIL: list type should be lowercase "list")"#)
    }
    if typeOf({"a": 1}) != "Map" {
        println(r#"FAIL: map type should be lowercase "map")"#)
    }
    if typeOf(Regex.new(r"\d+")) != "Regex" {
        println(r#"FAIL: regex type should be lowercase "regex")"#)
    }

    println("any_type tests passed")
    return 0
}
