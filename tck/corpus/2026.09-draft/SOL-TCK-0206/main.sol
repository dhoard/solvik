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
val a = A(7)
val b = B(8)
print("nom" .. a.get() .. b.get())
