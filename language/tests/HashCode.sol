// The universal `Any.hashCode()` member and its pairing with `equals` (docs/LANGUAGE_SPEC.md section 3).
//
// Equal values must hash alike. A user class that overrides `equals` is required to override
// `hashCode` in the same class, so a hash can never ignore a field that equality compares.

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
        // Reads exactly the fields `equals` reads.
        return 31 * this.x + this.y
    }
}

var a: Point = Point(1, 2)
var b: Point = Point(1, 2)
var c: Point = Point(9, 9)

// Equal values agree on both members.
println(a == b)
println(a.hashCode() == b.hashCode())
println(a == c)

// The override is reached through a widened receiver too.
var erased: Any = a
var other: Any = b
println(erased.hashCode() == other.hashCode())

// `Set` and `Map` stay consistent with the shared equality and hash definitions.
var unique: Set<Point> = Set(a, b, c)
println(unique.size)
println(unique.contains(Point(1, 2)))

// Built-in scalars hash by value, and equal values agree.
println("abc".hashCode() == "abc".hashCode())
println(7.hashCode() == (3 + 4).hashCode())

// Solvik floating equality is IEEE: 0.0 equals -0.0, so their hashes agree and a Set keeps one entry.
var zeros: Set<Double> = Set(0.0, -0.0)
println(zeros.size)

// A class overriding neither member falls back to reference identity for both.
class Plain {
}
var p: Plain = Plain()
var q: Plain = Plain()
println(p == q)
println(p.hashCode() == q.hashCode())
println(p.hashCode() == p.hashCode())
