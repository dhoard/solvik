class Point {
    val x: Integer
    val y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
val p = Point(1, 2)
val q: Any = p
val r: Any = p
print(q === r)

print("EXECUTED-INVALID")
