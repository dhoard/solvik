// expected C095: a struct without a public string() method is not Stringable
package conformance

struct Point {
    pub x: Int
    pub y: Int
}

func render<T: Stringable>(value: T) -> String {
    return value.string()
}

func main() -> Int {
    println(render(Point { x: 1, y: 2 }))
    return 0
}
