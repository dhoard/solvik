class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var q: Any = Point(1, 2)
var a: Point = q as Point
var b: Point = a
print("nw" .. (a === b))
