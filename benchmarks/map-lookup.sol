// Map.get over a populated map.
var mutable map = Map<Integer, Integer>()
var mutable i = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
var mutable sum = 0
var mutable k = 0
while (k < 20000) {
    sum = sum + map.get(k)
    k = k + 1
}
println(sum)
