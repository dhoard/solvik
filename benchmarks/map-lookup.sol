// Map.get over a populated map.
var map = Map<Integer, Integer>()
var i = 0
while (i < 20000) {
    map.put(i, i * 2)
    i = i + 1
}
var sum = 0
var k = 0
while (k < 20000) {
    sum = sum + map.get(k)
    k = k + 1
}
println(sum)
