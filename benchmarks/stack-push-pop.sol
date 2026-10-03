// Stack push/pop, which should stay constant time per operation.
mutable val stack = Stack<Integer>()
mutable val i = 0
while (i < 1000000) {
    stack.push(i)
    i = i + 1
}
mutable val popped = 0
while (!stack.isEmpty) {
    stack.pop()
    popped = popped + 1
}
println(popped)
