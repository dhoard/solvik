package trait_multi

trait Drawable {
    func draw() -> String
}

trait Resizable {
    mut func scale(factor: Float)
    func getSize() -> Float
}

struct Circle {
    pub mut radius: Float,

    pub func draw() -> String {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub mut func scale(factor: Float) {
        radius = radius * factor
    }

    pub func getSize() -> Float {
        return radius
    }
}

func useDrawable(d: Drawable) {
    println(d.draw())
}

func useResizable(r: Resizable) {
    println("size=" .. string(r.getSize()))
}

func main() -> Int {
    c: Circle = Circle { radius: 5.0 }
    useDrawable(c)
    useResizable(c)

    mut s: Resizable = Circle { radius: 10.0 }
    println("before scale: " .. string(s.getSize()))
    s.scale(2.0)
    println("after scale: " .. string(s.getSize()))

    return 0
}
