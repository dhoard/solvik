class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var a: Point? = Point(3, 4)
if (a === null) {
    print("isnull")
}
else {
    print("el" .. a.y)
}
