class A {
    val v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
val x: Any = A(9)
val a: A = x as A
print("cast" .. a.get())
