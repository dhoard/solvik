class Point {
    val x: Int
    val y: Int
    Point(x: Int, y: Int) {
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
val c = a
println(a == b)
println(a != b)
println(a === c)
println(a !== b)
println(1 == 1)
println("x" == "x")
println('a' == 'a')
