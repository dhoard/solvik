// Baseline for function-value dispatch: statically resolved direct calls only. Every call-* row below
// performs this same body of work through a different callable form, so a difference between two rows
// is the cost of that form and not a difference in the arithmetic.
//
// The callee adds its arguments and returns the sum, which is small enough that the call itself
// dominates the loop and large enough that the compiler cannot be assumed to fold it away.
func step(value: Integer, delta: Integer): Integer {
    return value + delta
}

var mutable total: Integer = 0
var mutable i: Integer = 0
while (i < 20000000) {
    total = step(total, 1)
    i = i + 1
}
println(total)
println(i)
