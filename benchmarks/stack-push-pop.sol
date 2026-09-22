// Stack push/pop, which should stay constant time per operation.
var stack = Stack<Integer>()
var i = 0
while (i < 1000000) {
    stack.push(i)
    i = i + 1
}
var popped = 0
while (!stack.isEmpty) {
    stack.pop()
    popped = popped + 1
}
println(popped)
