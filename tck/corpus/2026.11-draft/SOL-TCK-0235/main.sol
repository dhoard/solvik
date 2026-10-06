class A {
    var v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}

class B {
    var v: Integer

    B(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
var a = A(1)
var b = B(1)
print("exp" .. a.equals(b))
