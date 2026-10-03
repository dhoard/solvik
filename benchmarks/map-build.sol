// Map.put while growing: each insertion scans the keys. N = 20000.
mutable val map = Map<Integer, Integer>()
mutable val i = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
println(map.size)
