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
var s = S()
var r = s
    .load()
    .value()
print("r" .. r)
