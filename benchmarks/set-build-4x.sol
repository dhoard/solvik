// Set.add while growing: each insertion scans for an equal element. N = 80000.
mutable val set = Set<Integer>()
mutable val added = 0
mutable val i = 0
while (i < 80000) {
    if (set.add(i)) {
        added = added + 1
    }
    i = i + 1
}
println(added)
println(set.size)
