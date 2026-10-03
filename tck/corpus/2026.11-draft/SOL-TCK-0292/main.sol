class C {
    C(x: Integer, y: Integer) {
        this.sum = x + y
    }

    val sum: Integer
}
val c = C(1,
    2)
print("r" .. c.sum)
