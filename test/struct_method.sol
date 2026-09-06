package example

struct Point {
    pub mut x: Int,
    pub mut y: Int,

    pub func distance() -> Float {
        sqSum: Float = x * x + y * y
        return math.sqrt(sqSum)
    }

    pub mut func move(dx: Int, dy: Int) {
        x = x + dx
        y = y + dy
    }

    pub func describe() -> String {
        return "Point(" .. x .. ", " .. y .. ")"
    }

    pub func sum() -> Int {
        return x + y
    }

    // Private helper — only callable inside Point methods
    func validate() -> Bool {
        return x >= 0 && y >= 0
    }
}

struct Counter {
    pub mut value: Int,
    label: String,

    pub mut func increment() {
        value = value + 1
    }

    pub mut func setValue(newValue: Int) {
        self.value = newValue
    }

    pub func getLabel() -> String {
        return label .. "=" .. value
    }
}

func sqrtOf(n: Int) -> Float {
    f: Float = n
    return math.sqrt(f)
}

func main() -> Int {
    // Create a mutable point
    mut p: Point = Point { x: 3, y: 4 }
    println("Initial: " .. p.describe())

    // Call mutating method
    p.move(10, 20)
    println("After move: " .. p.describe())

    // Call non-mutating method returning int
    s: Int = p.sum()
    println("Sum: " .. s)

    // Distance via helper function (int->float widening for native args)
    d: Float = sqrtOf(p.x * p.x + p.y * p.y)
    println("Distance: " .. d)

    // Counter with methods
    mut c: Counter = Counter { value: 0, label: "count" }
    c.increment()
    c.increment()
    c.increment()
    c.setValue(8)
    if c.value != 8 {
        return 1
    }
    println(c.getLabel())

    // Verify private method is not accessible (commented out — would be compile error)
    // p.validate()

    // Verify private field is not accessible (commented out — would be compile error)
    // c.label

    return 0
}
