class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
val a: Point? = Point(1, 2)
print("ok" .. (a === null))
