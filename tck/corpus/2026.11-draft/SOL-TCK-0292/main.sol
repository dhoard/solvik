class C {
    C(x: Integer, y: Integer) {
        this.sum = x + y
    }

    var sum: Integer
}
var c = C(1,
    2)
print("r" .. c.sum)
