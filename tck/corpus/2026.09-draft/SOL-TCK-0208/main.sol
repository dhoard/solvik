class A {
    val v: Integer

    A(n: Integer) {
        this.v = n
    }

    func get(): Integer {
        return this.v
    }
}
val x: Any = A(1)
print(x.get())

print("EXECUTED-INVALID")
