package trait_collection

trait Drawable {
    func draw() -> string
    func area() -> float
}

struct Circle {
    pub mut radius: float,

    pub func draw() -> string {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub func area() -> float {
        return 3.14159 * radius * radius
    }
}

struct Rectangle {
    pub mut width: float,
    pub mut height: float,

    pub func draw() -> string {
        return "Rectangle(" .. string(width) .. "x" .. string(height) .. ")"
    }

    pub func area() -> float {
        return width * height
    }
}

func main() -> int {
    shapes: list<Drawable> = [Circle(5.0), Rectangle(3.0, 4.0)]

    for s in shapes {
        println(s.draw() .. " area=" .. string(s.area()))
    }

    return 0
}
