class S {
    S() {
    }

    method load(): S {
        return this
    }

    method value(): Integer {
        return 42
    }
}
var s: S = S()
var r: Integer = s
    .load()
    .value()
print("r" .. r)
