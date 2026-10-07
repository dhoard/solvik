// List.get over a populated erased list, the counterpart of list-get.
var mutable list: List<Any> = List<Any>()
var mutable i: Integer = 0
while (i < 30000000) {
    list.add(i)
    i = i + 1
}
var mutable hits: Integer = 0
var mutable j: Integer = 0
while (j < list.size) {
    if (list.get(j) == 29999999) {
        hits = hits + 1
    }
    j = j + 1
}
println(hits)
