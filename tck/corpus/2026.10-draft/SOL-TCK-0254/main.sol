class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
val q: Any = Point(1, 2)
val a: Point = q as Point
val b: Point = a
print("nw" .. (a === b))
