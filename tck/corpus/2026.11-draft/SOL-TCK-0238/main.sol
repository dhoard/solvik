class Point {
    var x: Integer
    var y: Integer

    Point(x: Integer, y: Integer) {
        this.x = x
        this.y = y
    }
}
var a: Point? = Point(1, 2)
var b: Point? = null
print("n" .. (a !== b) .. (a === b) .. (b !== b) .. (b === b))
