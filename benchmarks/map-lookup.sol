// Map.get over a populated map.
var mutable map: Map<Integer, Integer> = Map<Integer, Integer>()
var mutable i: Integer = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
var mutable sum: Integer = 0
var mutable k: Integer = 0
while (k < 20000) {
    sum = sum + map.get(k)
    k = k + 1
}
println(sum)
