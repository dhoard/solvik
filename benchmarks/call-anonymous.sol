// Calls through a non-capturing anonymous function value. The expression is evaluated once, outside the
// loop, so this row measures the call rather than the allocation: an anonymous function creates a new
// value every time evaluation reaches the expression, and here evaluation reaches it once
// (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions").
func step(value: Integer, delta: Integer): Integer {
    return value + delta
}

var advance: func(Integer, Integer): Integer = func(value: Integer, delta: Integer): Integer {
    return value + delta
}

var mutable total = 0
var mutable i = 0
while (i < 20000000) {
    total = advance(total, 1)
    i = i + 1
}
println(total)
println(i)
