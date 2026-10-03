// The same add workload on an erased element type (List<Any>), which must keep Object storage.
// Comparing this against list-add is how the integral element-storage gain is read.
mutable val list = List<Any>()
mutable val count = 0
mutable val i = 0
while (i < 30000000) {
    list.add(i)
    count = count + 1
    i = i + 1
}
println(count)
println(list.size)
