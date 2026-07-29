package example

struct Point {
    pub mut x: int,
    pub mut y: int,

    pub func distance() -> float {
        sqSum: float = x * x + y * y
        return math.sqrt(sqSum)
    }

    pub func move(dx: int, dy: int) -> void {
        x = x + dx
        y = y + dy
    }

    pub func describe() -> string {
        return "Point(" .. x .. ", " .. y .. ")"
    }

    pub func sum() -> int {
        return x + y
    }

    // Private helper — only callable inside Point methods
    func validate() -> bool {
        return x >= 0 && y >= 0
    }
}

struct Counter {
    pub mut value: int,
    label: string,

    pub func increment() -> void {
        value = value + 1
    }

    pub func getLabel() -> string {
        return label .. "=" .. value
    }
}

func sqrtOf(n: int) -> float {
    f: float = n
    return math.sqrt(f)
}

func main() -> int {
    // Create a mutable point
    mut p: Point = Point(3, 4)
    println("Initial: " .. p.describe())

    // Call mutating method
    p.move(10, 20)
    println("After move: " .. p.describe())

    // Call non-mutating method returning int
    s: int = p.sum()
    println("Sum: " .. s)

    // Distance via helper function (int->float widening for native args)
    d: float = sqrtOf(p.x * p.x + p.y * p.y)
    println("Distance: " .. d)

    // Counter with methods
    mut c: Counter = Counter(0, "count")
    c.increment()
    c.increment()
    c.increment()
    println(c.getLabel())

    // Verify private method is not accessible (commented out — would be compile error)
    // p.validate()

    // Verify private field is not accessible (commented out — would be compile error)
    // c.label

    return 0
}
