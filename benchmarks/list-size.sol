// The computed size property evaluated in a loop condition, once per iteration.
mutable val list = List<Integer>(1, 2, 3)
mutable val steps = 0
while (steps < 20000000) {
    if (list.size < 0) {
        steps = steps + 1000000
    }
    steps = steps + 1
}
println(steps)
