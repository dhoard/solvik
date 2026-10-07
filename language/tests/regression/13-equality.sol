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
var c: Point = a
println(a == b)
println(a != b)
println(a === c)
println(a !== b)
println(1 == 1)
println("x" == "x")
println('a' == 'a')
