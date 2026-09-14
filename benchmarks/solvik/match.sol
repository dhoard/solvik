package bench

enum Shape {
    circle(Double)
    square(Double)
}

struct Area {

    public func area(s: Shape): Double {
        return match s {
            Shape.circle(r) => 3.141592653589793 * r * r
            Shape.square(a) => a * a
        }
    }
}

struct Main {

    public func run(args: String...): Integer {
        let mutable total: Double = 0.0
        let mutable i: Long = 0
        while i < 5000000 {
            let s: Shape = Shape.circle(i)
            total += Area.area(s)
            i += 1
        }
        System.getOut().println(total)
        return 0
    }
}
