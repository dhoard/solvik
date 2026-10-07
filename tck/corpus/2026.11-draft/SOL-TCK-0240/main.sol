class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var a: Point = Point(1, 2)
var b: Point = Point(1, 2)
print("cls" .. (a === b))
