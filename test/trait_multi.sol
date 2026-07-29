package trait_multi

trait Drawable {
    func draw() -> string
}

trait Resizable {
    func scale(factor: float) -> void
    func getSize() -> float
}

struct Circle {
    pub mut radius: float,

    pub func draw() -> string {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub func scale(factor: float) -> void {
        radius = radius * factor
    }

    pub func getSize() -> float {
        return radius
    }
}

func useDrawable(d: Drawable) -> void {
    println(d.draw())
}

func useResizable(r: Resizable) -> void {
    println("size=" .. string(r.getSize()))
}

func main() -> int {
    c: Circle = Circle(5.0)
    useDrawable(c)
    useResizable(c)

    mut s: Resizable = Circle(10.0)
    println("before scale: " .. string(s.getSize()))
    s.scale(2.0)
    println("after scale: " .. string(s.getSize()))

    return 0
}
