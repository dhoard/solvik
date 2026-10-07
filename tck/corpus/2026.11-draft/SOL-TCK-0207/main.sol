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
var b: B = B(8)
var a: A = b
print(1)

print("EXECUTED-INVALID")
