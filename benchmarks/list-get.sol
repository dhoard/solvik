// List.get over a populated integral list, counting elements above a threshold.
mutable val list = List<Integer>()
mutable val i = 0
while (i < 30000000) {
    list.add(i)
    i = i + 1
}
mutable val hits = 0
mutable val j = 0
while (j < list.size) {
    if (list.get(j) > 29999998) {
        hits = hits + 1
    }
    j = j + 1
}
println(hits)
