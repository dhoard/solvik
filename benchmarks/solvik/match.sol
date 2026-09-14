package bench

enum Shape {
    circle(Double)
    square(Double)
}

struct Area {

    pub func area(s: Shape): Double {
        return match s {
            Shape.circle(r) => 3.141592653589793 * r * r
            Shape.square(a) => a * a
        }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        var total: Double = 0.0
        var i: Long = 0
        while i < 5000000 {
            let s: Shape = Shape.circle(i)
            total += Area.area(s)
            i += 1
        }
        System.getOut().println(total)
        return 0
    }
}
