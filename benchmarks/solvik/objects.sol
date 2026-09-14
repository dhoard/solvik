package bench

struct Point {

    x: Long
    y: Long

    pub func new(x: Long, y: Long): Self {
        return Self {
            x: x,
            y: y,
        }
    }

    pub func dist2(self, other: Point): Long {
        let dx: Long = self.x - other.x
        let dy: Long = self.y - other.y
        return dx * dx + dy * dy
    }
}

struct Main {

    pub func run(args: String...): Integer {
        var total: Long = 0
        var i: Long = 0
        while i < 5000000 {
            let p: Point = Point.new(i, i + 1)
            let q: Point = Point.new(i + 2, i + 3)
            total += p.dist2(q)
            i += 1
        }
        System.getOut().println(total)
        return 0
    }
}
