class A {
    var v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
var x: Any = A(9)
var a: A = x as A
print("cast" .. a.get())
