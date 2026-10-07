// The computed size property evaluated in a loop condition, once per iteration.
var mutable list: List<Integer> = List<Integer>(1, 2, 3)
var mutable steps: Integer = 0
while (steps < 20000000) {
    if (list.size < 0) {
        steps = steps + 1000000
    }
    steps = steps + 1
}
println(steps)
