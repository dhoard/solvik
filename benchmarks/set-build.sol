// Set.add while growing: each insertion scans for an equal element. N = 20000.
var set = Set<Integer>()
var added = 0
var i = 0
while (i < 20000) {
    if (set.add(i)) {
        added = added + 1
    }
    i = i + 1
}
println(added)
println(set.size)
