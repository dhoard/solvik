package nameddefaults

class Point {
    pub x: Int
    pub y: Int

    pub static new(x: Int = 0, y: Int = 0): Self {
        return Self { x, y }
    }

    pub dist(): Int {
        return x * x + y * y
    }
}

class Main {
    pub static run(args: String...): Int {
        // positional
        a: Point = Point::new(3, 4)
        // named
        b: Point = Point::new(x: 6, y: 8)
        // mixed: positional then named
        c: Point = Point::new(1, y: 2)
        // skipped default
        d: Point = Point::new()
        // named out of order
        e: Point = Point::new(y: 5, x: 12)

        if a.dist() != 25 { return 1 }
        if b.dist() != 100 { return 2 }
        if c.dist() != 5 { return 3 }
        if d.dist() != 0 { return 4 }
        if e.dist() != 169 { return 5 }

        stdout.println("ok")
        return 0
    }
}
