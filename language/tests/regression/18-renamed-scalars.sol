val count: Integer = 40
val step: Integer = 2
println(count + step)
val ratio: Double = 7.9
println(Integer(ratio))
val wide: Long = Long(9)
println(wide)
val letter: Character = 'A'
println(letter)
val other: Character = 'Z'
println(other)
val missing: Integer? = null
println(missing)
println(missing is Integer)
println(count is Integer)
println(count is Number)
val numbers: List<Integer> = List<Integer>(1, 2, 3)
println(numbers.size)
println(numbers.get(0))
val codes: Map<String, Integer> = Map("a": 1)
println(codes.get("a"))
val letters: Set<Character> = Set('a', 'b')
println(letters.size)
func twice(value: Integer): Integer {
    return value * 2
}
println(twice(21))
for (var i: Integer = 0; i < 3; i = i + 1) {
    println(i)
}
