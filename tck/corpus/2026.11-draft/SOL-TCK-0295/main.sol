class S {
    S() {
    }

    func load(): S {
        return this
    }

    func value(): Integer {
        return 42
    }
}
val s = S()
val r = s
    .load()
    .value()
print("r" .. r)
