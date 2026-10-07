class A {
    var v: Integer

    A(n: Integer) {
        this.v = n
    }

    method get(): Integer {
        return this.v
    }
}
var x: Any = A(1)
print(x.get())

print("EXECUTED-INVALID")
