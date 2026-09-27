class A {
    val v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}

class B {
    val v: Integer

    B(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
val a = A(1)
val b = B(1)
print(a == b)

print("EXECUTED-INVALID")
