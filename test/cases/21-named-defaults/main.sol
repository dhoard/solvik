module nameddefaults

class Point {

    public x: Long
    public y: Long

    public static new(x: Long = 0, y: Long = 0): Self {
        return Self { x: x, y: y, }
    }

    public dist(): Long {
        return self.x * self.x + self.y * self.y
    }
}

class Main {

    public static run(args: String...): Long {
        // positional
        a: Point = Point.new(3, 4)
        // named
        b: Point = Point.new(x: 6, y: 8)
        // mixed: positional then named
        c: Point = Point.new(1, y: 2)
        // skipped default
        d: Point = Point.new()
        // named out of order
        e: Point = Point.new(y: 5, x: 12)

        if a.dist() != 25 { return 1 }
        if b.dist() != 100 { return 2 }
        if c.dist() != 5 { return 3 }
        if d.dist() != 0 { return 4 }
        if e.dist() != 169 { return 5 }

        stdout.println("ok")
        return 0
    }
}
