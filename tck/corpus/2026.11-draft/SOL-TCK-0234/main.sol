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
var a: A = A(1)
var b: B = B(1)
var q: Any = a
print("esc" .. (q == b))
