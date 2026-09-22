// Scaling probe for positional List access: builds a list of <N> elements and reads each one once.
// The element count is a placeholder so SolvikCollectionBenchmarkTest can run it at two sizes.
// Accesses are counted rather than summed, so the checked Integer accumulator cannot overflow.
var list = List<Integer>()
var i = 0
while (i < <N>) {
    list.add(i)
    i = i + 1
}
var reads = 0
var j = 0
while (j < list.size) {
    if (list.get(j) >= 0) {
        reads = reads + 1
    }
    j = j + 1
}
println(reads)
println(list.size)
