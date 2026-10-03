// Set.contains over a populated set.
mutable val set = Set<Integer>()
mutable val i = 0
while (i < 20000) {
    set.add(i)
    i = i + 1
}
mutable val hits = 0
mutable val k = 0
while (k < 20000) {
    if (set.contains(k)) {
        hits = hits + 1
    }
    k = k + 1
}
println(hits)
