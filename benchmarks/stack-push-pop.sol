// Stack push/pop, which should stay constant time per operation.
var mutable stack: Stack<Integer> = Stack<Integer>()
var mutable i: Integer = 0
while (i < 1000000) {
    stack.push(i)
    i = i + 1
}
var mutable popped: Integer = 0
while (!stack.isEmpty) {
    stack.pop()
    popped = popped + 1
}
println(popped)
