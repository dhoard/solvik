// A deliberately polymorphic function-value call site. One call, written once inside `apply`, sees four
// distinct values: the canonical value of a declared function, a value created by one anonymous
// expression, a capturing closure, and a bound method value. A site that keeps meeting new values cannot
// keep a cached target, so this is the row on which the indirect fallback settles, and it exists to show
// that the fallback is correct and bounded rather than only the monomorphic fast path
// (docs/LANGUAGE_SPEC.md section 6, "Function values and invocation").
//
// Each of the four adds a different amount, so the checksum is a property of the rotation as well as of
// the arithmetic: a site that kept serving one target after the second value arrived prints a different
// total rather than passing silently. 20,000,000 iterations divide by four, so the expected sum is
// 5,000,000 cycles of (1 + 2 + 3 + 4) = 50,000,000.
//
// The values are built once, before the loop. Reading them by position from a list is the cheapest way to
// reach one call site from four values, and its cost is the same in every iteration, so the difference
// between this row and call-named is dispatch plus that read and not something subtler.
func named(value: Integer): Integer {
    return value + 1
}

class Stepper {
    val delta: Integer = 4

    func step(value: Integer): Integer {
        return value + this.delta
    }
}

func apply(operation: func(Integer): Integer, value: Integer): Integer {
    return operation(value)
}

val delta = 3

val operations: List<func(Integer): Integer> = List()

val anonymous = func(value: Integer): Integer {
    return value + 2
}

val closure = func [delta](value: Integer): Integer {
    return value + delta
}

val stepper = Stepper()

operations.add(named)
operations.add(anonymous)
operations.add(closure)
operations.add(stepper.step)

mutable val total = 0
mutable val slot = 0
mutable val i = 0
while (i < 20000000) {
    total = apply(operations.get(slot), total)
    slot = slot + 1
    if (slot == 4) {
        slot = 0
    }
    i = i + 1
}
println(total)
println(i)
