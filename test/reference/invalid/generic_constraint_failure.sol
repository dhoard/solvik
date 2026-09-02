// expected C095: a struct without a public string() method is not Stringable
package conformance

struct Point {
    pub x: int
    pub y: int
}

func render<T: Stringable>(value: T) -> string {
    return value.string()
}

func main() -> int {
    println(render(Point { x: 1, y: 2 }))
    return 0
}
