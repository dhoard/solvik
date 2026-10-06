// List.get over a populated erased list, the counterpart of list-get.
var mutable list = List<Any>()
var mutable i = 0
while (i < 30000000) {
    list.add(i)
    i = i + 1
}
var mutable hits = 0
var mutable j = 0
while (j < list.size) {
    if (list.get(j) == 29999999) {
        hits = hits + 1
    }
    j = j + 1
}
println(hits)
