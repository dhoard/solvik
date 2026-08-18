package trait_multi

trait Drawable {
    func draw() -> string
}

trait Resizable {
    mut func scale(factor: float)
    func getSize() -> float
}

struct Circle {
    pub mut radius: float,

    pub func draw() -> string {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub mut func scale(factor: float) {
        radius = radius * factor
    }

    pub func getSize() -> float {
        return radius
    }
}

func useDrawable(d: Drawable) {
    println(d.draw())
}

func useResizable(r: Resizable) {
    println("size=" .. string(r.getSize()))
}

func main() -> int {
    c: Circle = Circle { radius: 5.0 }
    useDrawable(c)
    useResizable(c)

    mut s: Resizable = Circle { radius: 10.0 }
    println("before scale: " .. string(s.getSize()))
    s.scale(2.0)
    println("after scale: " .. string(s.getSize()))

    return 0
}
