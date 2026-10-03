// Solvik abstract classes and match over a class type, which needs a wildcard branch.
abstract class Shape {
}

class Circle extends Shape {
    val radius: Integer

    Circle(radius: Integer) {
        this.radius = radius
    }
}

class Square extends Shape {
    val side: Integer

    Square(side: Integer) {
        this.side = side
    }
}

func area(shape: Shape): Integer {
    return match shape {
        circle: Circle => 3 * circle.radius * circle.radius
        square: Square => square.side * square.side
        _ => 0
    }
}

println(area(Circle(2)))
println(area(Square(3)))
