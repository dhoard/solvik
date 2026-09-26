// Implicit numeric widening where no range or precision is lost (LANGUAGE_SPEC.md section 4).
println(1 + 1L)
println(1L + 1)
println(1 + 1.5)
println(1.5f + 2.0)
val widenedLong: Long = 1
val widenedDouble: Double = 1.5f
val widenedInteger: Integer = Byte(7)
println(widenedLong)
println(widenedDouble)
println(widenedInteger)
println(1 == 1L)
println(1.5f == 1.5)
println(1 < 2L)

func takes(a: Long, b: Double): Long {
    return a
}

println(takes(1, 1.5f))

// The least common widened type of Long and Integer is Long (not Double, which would lose precision).
val asLong: Long = 1L - 1
println(asLong)

// A widening never overflows: 2147483647 (Integer) widens to Long, then + 1 stays a Long.
val big: Long = 2147483647
println(big + 1)
