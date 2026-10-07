// Set.contains over a populated set.
var mutable set: Set<Integer> = Set<Integer>()
var mutable i: Integer = 0
while (i < 20000) {
    set.add(i)
    i = i + 1
}
var mutable hits: Integer = 0
var mutable k: Integer = 0
while (k < 20000) {
    if (set.contains(k)) {
        hits = hits + 1
    }
    k = k + 1
}
println(hits)
