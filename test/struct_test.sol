// test/struct_test.sol — struct type tests
//
// Tests: positional construction, field access, mutable field assignment,
//        methods (mutating and non-mutating), empty struct, struct equality,
//        nested structs, struct with different field types

package test

struct Point {
    pub x: int,
    pub y: int,
}

struct Empty {}

struct Config {
    pub mut host: string,
    pub mut port: int,
    pub timeout: int,
}

struct Label {
    pub mut value: int,
    name: string,

    pub func increment() -> void {
        value = value + 1
    }

    pub func describe() -> string {
        return name .. "=" .. value
    }
}

struct Address {
    pub city: string,
    pub zip: int,
}

struct Person {
    pub name: string,
    pub addr: Address,
}

func main() -> int {
    // === positional construction ===
    p: Point = Point(3, 4)
    if p.x != 3 || p.y != 4 {
        println("FAIL: Point construction")
    }

    // === field access ===
    sum: int = p.x + p.y
    if sum != 7 {
        println("FAIL: field access sum should be 7")
    }

    // === empty struct ===
    e: Empty = Empty()
    println("Empty created")

    // === struct with mutable fields ===
    cfg: Config = Config("localhost", 8080, 30)
    if cfg.host != "localhost" || cfg.port != 8080 {
        println("FAIL: Config construction")
    }

    // Assign to mutable field
    cfg.port = 9090
    if cfg.port != 9090 {
        println("FAIL: mutable field assignment")
    }

    // === struct equality ===
    q: Point = Point(3, 4)
    if p != q {
        println("FAIL: equal structs should be equal")
    }

    r: Point = Point(5, 6)
    if p == r {
        println("FAIL: different structs should not be equal")
    }

    // === methods (non-mutating) ===
    mut counter: Label = Label(0, "count")
    if counter.describe() != "count=0" {
        println("FAIL: describe should be 'count=0'")
    }

    // === methods (mutating) ===
    mut c: Label = Label(5, "hits")
    c.increment()
    c.increment()
    c.increment()
    if c.describe() != "hits=8" {
        println("FAIL: after 3 increments should be 'hits=8', got '" .. c.describe() .. "'")
    }

    // === struct with methods on mutable variable ===
    mut label: Label = Label(10, "fixed")
    if label.describe() != "fixed=10" {
        println("FAIL: immutable struct method call")
    }

    // === nested structs ===
    addr: Address = Address("New York", 10001)
    person: Person = Person("Alice", addr)
    if person.name != "Alice" || person.addr.city != "New York" {
        println("FAIL: nested struct field access")
    }

    println("struct tests passed")
    return 0
}