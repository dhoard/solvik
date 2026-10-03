// Calls through a named function value. `advance` holds the canonical value of one declared function,
// so the site sees one value forever and its dispatch may keep the monomorphic form: this is the row
// that must stay close to call-direct, because a declaration reference is the commonest way a program
// writes an indirect call (docs/LANGUAGE_SPEC.md section 6, "Named functions as values").
func step(value: Integer, delta: Integer): Integer {
    return value + delta
}

val advance: func(Integer, Integer): Integer = step

mutable val total = 0
mutable val i = 0
while (i < 20000000) {
    total = advance(total, 1)
    i = i + 1
}
println(total)
println(i)
