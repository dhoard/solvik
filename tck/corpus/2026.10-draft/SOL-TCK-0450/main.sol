// Solvik TCK SOL-TCK-0450
// Two evaluations of one non-capturing anonymous function are distinct values, while one local read twice is the same value under both === and equals
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An anonymous function creates a new function value every time evaluation reaches the expression, and two evaluations are distinct even when the expression captures no values.
//   - Re-reading a local that holds an anonymous function value preserves its identity.
//
func makeHalf(): func(Integer): Integer {
    return func(value: Integer): Integer {
        return value / 2
    }
}

val first = makeHalf()
val second = makeHalf()

print((makeHalf() === makeHalf()).toString() .. "\n")
print((first === second).toString() .. "\n")
print(first.equals(second).toString() .. "\n")
print((first === first).toString() .. "\n")
print(first.equals(first).toString() .. "\n")
print(first(81).toString())
