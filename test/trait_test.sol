package trait_test

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

func printShape(shape: Drawable) -> void {
    println(shape.draw() .. " area=" .. string(shape.area()))
}

func main() -> int {
    c: Circle = Circle(5.0)
    r: Rectangle = Rectangle(3.0, 4.0)

    printShape(c)
    printShape(r)

    mut current: Drawable = Circle(10.0)
    println(current.draw())
    current = Rectangle(6.0, 7.0)
    println(current.draw())

    return 0
}
