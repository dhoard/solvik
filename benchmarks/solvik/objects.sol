package bench

struct Point {

    x: Long
    y: Long

    public func new(x: Long, y: Long): Self {
        return Self {
            x: x,
            y: y,
        }
    }

    public func dist2(self, other: Point): Long {
        let dx: Long = self.x - other.x
        let dy: Long = self.y - other.y
        return dx * dx + dy * dy
    }
}

struct Main {

    public func run(args: String...): Long {
        let mutable total: Long = 0
        let mutable i: Long = 0
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
