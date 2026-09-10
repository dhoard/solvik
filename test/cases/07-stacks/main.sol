module stacks

class Main {

    public static run(args: String...): Long {
        let s: Stack<Long> = Stack<Long>.new()
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
