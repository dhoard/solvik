// Solvik semantic equality `==` with a user `equals` override.
//
// `==` and an explicit `equals` call share one semantic-equality definition: they dispatch the
// effective override on the left receiver, or fall back to reference identity when no class in the
// hierarchy overrides `equals`. `===` always compares allocations and never invokes the override.

class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }

    method override equals(other: Any?): Boolean {
        if (other is Point) {
            return this.x == other.x && this.y == other.y
        }
        return false
    }

    method override hashCode(): Integer {
        return 31 * this.x + this.y
    }
}

var a: Point = Point(1, 2)
var b: Point = Point(1, 2)
var c: Point = Point(3, 4)

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
var points: Set<Point> = Set(a, b, c)
println(points.size)
