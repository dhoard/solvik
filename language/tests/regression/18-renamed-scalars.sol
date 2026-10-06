var count: Integer = 40
var step: Integer = 2
println(count + step)
var ratio: Double = 7.9
println(Integer(ratio))
var wide: Long = Long(9)
println(wide)
var letter: Character = 'A'
println(letter)
var other: Character = 'Z'
println(other)
var missing: Integer? = null
println(missing)
println(missing is Integer)
println(count is Integer)
println(count is Number)
var numbers: List<Integer> = List<Integer>(1, 2, 3)
println(numbers.size)
println(numbers.get(0))
var codes: Map<String, Integer> = Map("a": 1)
println(codes.get("a"))
var letters: Set<Character> = Set('a', 'b')
println(letters.size)
func twice(value: Integer): Integer {
    return value * 2
}
println(twice(21))
{
    var mutable i: Integer = 0
    while (i < 3) {
        println(i)
        i = i + 1
    }
}
