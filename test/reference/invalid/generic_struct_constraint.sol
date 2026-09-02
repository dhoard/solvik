// expected C095: struct type argument does not satisfy the declared constraint
package conformance

struct Point {
    pub x: int
    pub y: int
}

struct Shelf<T: Stringable> {
    pub item: T
}

func main() -> int {
    s: any = Shelf { item: Point { x: 1, y: 2 } }
    return 0
}
