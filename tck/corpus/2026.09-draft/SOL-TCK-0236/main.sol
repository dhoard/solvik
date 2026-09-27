class A {
    val v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
val a = A(1)
val q: Any = a
print("pcp" .. (a == q))
