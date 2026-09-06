// expected C095: struct type argument does not satisfy the declared constraint
package conformance

struct Point {
    pub x: Int
    pub y: Int
}

struct Shelf<T: Stringable> {
    pub item: T
}

func main() -> Int {
    s: Any = Shelf { item: Point { x: 1, y: 2 } }
    return 0
}
