// Solvik sealed types and exhaustive match over a closed subtype set.
sealed class Shape {
}

class Circle extends Shape {
    val radius: Int

    init(radius: Int) {
        this.radius = radius
    }
}

class Square extends Shape {
    val side: Int

    init(side: Int) {
        this.side = side
    }
}

fun area(shape: Shape): Int {
    return match shape {
        circle: Circle => 3 * circle.radius * circle.radius
        square: Square => square.side * square.side
    }
}

fun main(): Unit {
    println(area(Circle(2)))
    println(area(Square(3)))
}
