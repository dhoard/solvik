// Calls through a capturing closure, with the value created once so the row reads as call cost against
// call-anonymous. `delta` is a captured primitive, which is the case that matters: a capture is stored as
// a hidden leading frame argument, so the difference between this row and call-anonymous is the capture
// read at entry and the array the call carries, not a boxed primitive slot
// (docs/LANGUAGE_SPEC.md section 6, "Explicit immutable closure capture").
func step(value: Integer, delta: Integer): Integer {
    return value + delta
}

val delta = 1

val advance: func(Integer): Integer = func [delta](value: Integer): Integer {
    return value + delta
}

mutable val total = 0
mutable val i = 0
while (i < 20000000) {
    total = advance(total)
    i = i + 1
}
println(total)
println(i)
