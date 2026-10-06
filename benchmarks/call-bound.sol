// Calls through a bound method value, created once so the row is monomorphic. The receiver is supplied
// as a hidden leading frame argument rather than as a guest argument, so the difference between this row
// and call-direct is that argument plus the dispatch the value performs, and the difference between it and
// call-named is which hidden argument the value carries
// (docs/LANGUAGE_SPEC.md section 6, "Bound method references").
class Stepper {
    var delta: Integer = 1

    func step(value: Integer): Integer {
        return value + this.delta
    }
}

var stepper = Stepper()

var advance: func(Integer): Integer = stepper.step

var mutable total = 0
var mutable i = 0
while (i < 20000000) {
    total = advance(total)
    i = i + 1
}
println(total)
println(i)
