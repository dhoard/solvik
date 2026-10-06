class A {
    var v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
var a = A(1)
var q: Any = a
print("pcp" .. (a == q))
