// List.add on a growing integral list. Counts insertions rather than summing elements so the
// checked Integer accumulator never overflows. Sized so collection work dominates process startup.
var mutable list: List<Integer> = List<Integer>()
var mutable count: Integer = 0
var mutable i: Integer = 0
while (i < 30000000) {
    list.add(i)
    count = count + 1
    i = i + 1
}
println(count)
println(list.size)
