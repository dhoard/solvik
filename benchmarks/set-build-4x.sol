// Set.add while growing: each insertion scans for an equal element. N = 80000.
var mutable set: Set<Integer> = Set<Integer>()
var mutable added: Integer = 0
var mutable i: Integer = 0
while (i < 80000) {
    if (set.add(i)) {
        added = added + 1
    }
    i = i + 1
}
println(added)
println(set.size)
