// Map.put while growing: each insertion scans the keys. N = 20000.
var mutable map: Map<Integer, Integer> = Map<Integer, Integer>()
var mutable i: Integer = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
println(map.size)
