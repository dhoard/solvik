// Scaling probe for positional List access: builds a list of <N> elements and reads each one once.
// The element count is a placeholder so SolvikCollectionBenchmarkTest can run it at two sizes.
// Accesses are counted rather than summed, so the checked Integer accumulator cannot overflow.
var mutable list: List<Integer> = List<Integer>()
var mutable i: Integer = 0
while (i < <N>) {
    list.add(i)
    i = i + 1
}
var mutable reads: Integer = 0
var mutable j: Integer = 0
while (j < list.size) {
    if (list.get(j) >= 0) {
        reads = reads + 1
    }
    j = j + 1
}
println(reads)
println(list.size)
