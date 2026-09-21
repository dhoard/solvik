// Solvik semantic equality `==` with a user `equals` override.
//
// `==` and an explicit `equals` call share one semantic-equality definition: they dispatch the
// effective override on the left receiver, or fall back to reference identity when no class in the
// hierarchy overrides `equals`. `===` always compares allocations and never invokes the override.

class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }

    override func equals(other: Any?): Boolean {
        if (other is Point) {
            return this.x == other.x && this.y == other.y
        }
        return false
    }
}

val a = Point(1, 2)
val b = Point(1, 2)
val c = Point(3, 4)

// The override decides `==` and an explicit `equals` call; they agree.
println(a == b)
println(a == c)
println(a.equals(b))
println(a.equals(c))

// `===` ignores the override entirely: it is the same allocation or not.
println(a === b)
println(a === a)
println(a !== b)

// Collections route through the same shared equality service.
val points: Set<Point> = Set(a, b, c)
println(points.size)
