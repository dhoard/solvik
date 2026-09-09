package stacks

class Main {
    pub static run(args: String...): Int {
        s: Stack<Int> = Stack<Int>::new()
        stdout.println(s.isEmpty())
        s.push(1)
        s.push(2)
        s.push(3)
        stdout.println(s.size())
        stdout.println(s.peek())
        stdout.println(s.pop())
        stdout.println(s.pop())
        stdout.println(s.isEmpty())
        return 0
    }
}
