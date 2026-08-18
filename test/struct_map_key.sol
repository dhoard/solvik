package example

struct Point {
    pub x: int,
    pub y: int,
}

func main() -> int {
    // Structs as map keys (equality is structural)
    p1: Point = Point { x: 1, y: 2 }
    p2: Point = Point { x: 1, y: 2 }
    p3: Point = Point { x: 3, y: 4 }

    // p1 and p2 are equal (same fields)
    if p1 == p2 {
        println("p1 == p2: true")
    } else {
        println("p1 == p2: false")
    }

    // p1 and p3 are not equal
    if p1 == p3 {
        println("p1 == p3: true")
    } else {
        println("p1 == p3: false")
    }

    println("struct equality test passed")
    return 0
}
