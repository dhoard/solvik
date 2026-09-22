// List.add on a growing integral list. Counts insertions rather than summing elements so the
// checked Integer accumulator never overflows. Sized so collection work dominates process startup.
var list = List<Integer>()
var count = 0
var i = 0
while (i < 30000000) {
    list.add(i)
    count = count + 1
    i = i + 1
}
println(count)
println(list.size)
