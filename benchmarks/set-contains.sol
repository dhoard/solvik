// Set.contains over a populated set.
var set = Set<Integer>()
var i = 0
while (i < 20000) {
    set.add(i)
    i = i + 1
}
var hits = 0
var k = 0
while (k < 20000) {
    if (set.contains(k)) {
        hits = hits + 1
    }
    k = k + 1
}
println(hits)
