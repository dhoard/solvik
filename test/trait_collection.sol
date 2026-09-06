package trait_collection

trait Drawable {
    func draw() -> String
    func area() -> Float
}

struct Circle {
    pub mut radius: Float,

    pub func draw() -> String {
        return "Circle(r=" .. string(radius) .. ")"
    }

    pub func area() -> Float {
        return 3.14159 * radius * radius
    }
}

struct Rectangle {
    pub mut width: Float,
    pub mut height: Float,

    pub func draw() -> String {
        return "Rectangle(" .. string(width) .. "x" .. string(height) .. ")"
    }

    pub func area() -> Float {
        return width * height
    }
}

func main() -> Int {
    shapes: List<Drawable> = [Circle { radius: 5.0 }, Rectangle { width: 3.0, height: 4.0 }]

    for s in shapes {
        println(s.draw() .. " area=" .. string(s.area()))
    }

    return 0
}
