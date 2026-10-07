class A {
    var v: Integer

    A(n: Integer) {
        this.v = n
    }

    method get(): Integer {
        return this.v
    }
}

class B {
    var v: Integer

    B(n: Integer) {
        this.v = n
    }

    method get(): Integer {
        return this.v
    }
}
var a: A = A(7)
var b: B = B(8)
print("nom" .. a.get() .. b.get())
