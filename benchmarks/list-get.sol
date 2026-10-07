// List.get over a populated integral list, counting elements above a threshold.
var mutable list: List<Integer> = List<Integer>()
var mutable i: Integer = 0
while (i < 30000000) {
    list.add(i)
    i = i + 1
}
var mutable hits: Integer = 0
var mutable j: Integer = 0
while (j < list.size) {
    if (list.get(j) > 29999998) {
        hits = hits + 1
    }
    j = j + 1
}
println(hits)
