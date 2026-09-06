package example

trait Shape {
    func describe() -> String
}

struct Point {
    pub mut x: Int,
    pub mut y: Int,

    pub func describe() -> String {
        return "Point(" .. x .. ", " .. y .. ")"
    }

    pub mut func move(dx: Int, dy: Int) {
        x = x + dx
        y = y + dy
    }
}

func sum(values: List<Int>) -> Int {
    mut result: Int = 0
    for value in values {
        result = result + value
    }
    return result
}
func fibonacci(value: Int) -> Int {
    if value <= 1 {
        return value
    }
    mut previous: Int = 0
    mut current: Int = 1
    mut index: Int = 2
    while index <= value {
        next: Int = previous + current
        previous = current
        current = next
        index = index + 1
    }
    return current
}
func main() -> Int {
    values: List<Int> = [
        10,
        20,
        30,
        40
    ]
    total: Int = sum(values)
    fib: Int = fibonacci(20)
    print("Total: " .. total)
    print("Fibonacci: " .. fib)
    expected: Int = 100
    if total != expected {
        print("Unexpected total")
        return 1
    }

    // Struct usage
    mut p: Point = Point { x: 3, y: 4 }
    if p.x != 3 || p.y != 4 {
        print("Unexpected point")
        return 1
    }
    p.move(10, 20)
    if p.x != 13 || p.y != 24 {
        print("Unexpected point after move")
        return 1
    }
    print(p.describe())

    // Struct equality
    q: Point = Point { x: 13, y: 24 }
    if p != q {
        print("Struct equality failed")
        return 1
    }

    // Trait support
    shape: Shape = Point { x: 1, y: 2 }
    shapeResult: String = shape.describe()
    if shapeResult != "Point(1, 2)" {
        print("Trait method call failed")
        return 1
    }

    return 0
}
