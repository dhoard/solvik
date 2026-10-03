// Map.get over a populated map.
mutable val map = Map<Integer, Integer>()
mutable val i = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
mutable val sum = 0
mutable val k = 0
while (k < 20000) {
    sum = sum + map.get(k)
    k = k + 1
}
println(sum)
