// Set.add while growing: each insertion scans for an equal element. N = 80000.
var set = Set<Integer>()
var added = 0
var i = 0
while (i < 80000) {
    if (set.add(i)) {
        added = added + 1
    }
    i = i + 1
}
println(added)
println(set.size)
