// Map.put while growing: each insertion scans the keys. N = 80000.
var mutable map = Map<Integer, Integer>()
var mutable i = 0
while (i < 80000) {
    map.put(i, i * 2)
    i = i + 1
}
println(map.size)
