// List.get over a populated erased list, the counterpart of list-get.
mutable val list = List<Any>()
mutable val i = 0
while (i < 30000000) {
    list.add(i)
    i = i + 1
}
mutable val hits = 0
mutable val j = 0
while (j < list.size) {
    if (list.get(j) == 29999999) {
        hits = hits + 1
    }
    j = j + 1
}
println(hits)
