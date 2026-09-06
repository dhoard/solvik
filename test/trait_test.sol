package trait_test

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

func printShape(shape: Drawable) {
    println(shape.draw() .. " area=" .. string(shape.area()))
}

func main() -> Int {
    c: Circle = Circle { radius: 5.0 }
    r: Rectangle = Rectangle { width: 3.0, height: 4.0 }

    printShape(c)
    printShape(r)

    mut current: Drawable = Circle { radius: 10.0 }
    println(current.draw())
    current = Rectangle { width: 6.0, height: 7.0 }
    println(current.draw())

    return 0
}
