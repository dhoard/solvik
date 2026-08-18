// test/struct_test.sol — struct type tests
//
// Tests: named-field construction, field access, mutable field assignment,
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

    pub mut func increment() {
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
    // === named-field construction ===
    p: Point = Point { x: 3, y: 4 }
    if p.x != 3 || p.y != 4 {
        println("FAIL: Point construction")
    }

    // === field access ===
    sum: int = p.x + p.y
    if sum != 7 {
        println("FAIL: field access sum should be 7")
    }

    // === empty struct ===
    e: Empty = Empty {}
    println("Empty created")

    // === struct with mutable fields ===
    cfg: Config = Config { host: "localhost", port: 8080, timeout: 30 }
    if cfg.host != "localhost" || cfg.port != 8080 {
        println("FAIL: Config construction")
    }

    // Assign to mutable field
    cfg.port = 9090
    if cfg.port != 9090 {
        println("FAIL: mutable field assignment")
    }

    // === struct equality ===
    q: Point = Point { x: 3, y: 4 }
    if p != q {
        println("FAIL: equal structs should be equal")
    }

    r: Point = Point { x: 5, y: 6 }
    if p == r {
        println("FAIL: different structs should not be equal")
    }

    // === methods (non-mutating) ===
    mut counter: Label = Label { value: 0, name: "count" }
    if counter.describe() != "count=0" {
        println("FAIL: describe should be 'count=0'")
    }

    // === methods (mutating) ===
    mut c: Label = Label { value: 5, name: "hits" }
    c.increment()
    c.increment()
    c.increment()
    if c.describe() != "hits=8" {
        println("FAIL: after 3 increments should be 'hits=8', got '" .. c.describe() .. "'")
    }

    // === struct with methods on mutable variable ===
    mut label: Label = Label { value: 10, name: "fixed" }
    if label.describe() != "fixed=10" {
        println("FAIL: immutable struct method call")
    }

    // === nested structs ===
    addr: Address = Address { city: "New York", zip: 10001 }
    person: Person = Person { name: "Alice", addr: addr }
    if person.name != "Alice" || person.addr.city != "New York" {
        println("FAIL: nested struct field access")
    }

    println("struct tests passed")
    return 0
}
