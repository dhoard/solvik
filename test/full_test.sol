package example

trait Shape {
    func describe() -> string
}

struct Point {
    pub mut x: int,
    pub mut y: int,

    pub func describe() -> string {
        return "Point(" .. x .. ", " .. y .. ")"
    }

    pub mut func move(dx: int, dy: int) {
        x = x + dx
        y = y + dy
    }
}

func sum(values: list<int>) -> int {
    mut result: int = 0
    for value in values {
        result = result + value
    }
    return result
}
func fibonacci(value: int) -> int {
    if value <= 1 {
        return value
    }
    mut previous: int = 0
    mut current: int = 1
    mut index: int = 2
    while index <= value {
        next: int = previous + current
        previous = current
        current = next
        index = index + 1
    }
    return current
}
func main() -> int {
    values: list<int> = [
        10,
        20,
        30,
        40
    ]
    total: int = sum(values)
    fib: int = fibonacci(20)
    print("Total: " .. total)
    print("Fibonacci: " .. fib)
    expected: int = 100
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
    shapeResult: string = shape.describe()
    if shapeResult != "Point(1, 2)" {
        print("Trait method call failed")
        return 1
    }

    return 0
}
