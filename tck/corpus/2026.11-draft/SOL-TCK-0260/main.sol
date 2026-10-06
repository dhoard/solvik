class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var a = Point(1, 2)
var b = Point(1, 2)
var c = a
print("df" .. (a == b) .. (a == c) .. (a.equals(b)))
