class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
val a = Point(1, 2)
val b = Point(1, 2)
val c = a
print("df" .. (a == b) .. (a == c) .. (a.equals(b)))
