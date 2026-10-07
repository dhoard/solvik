class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var p: Point = Point(1, 2)
var q: Any = p
var r: Any = p
print(q === r)

print("EXECUTED-INVALID")
