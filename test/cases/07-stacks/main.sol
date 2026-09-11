package stacks

class Main {

    public static run(args: String...): Long {
        let s: Stack<Long> = Stack<Long>.new()
        System.out().println(s.isEmpty())
        s.push(1)
        s.push(2)
        s.push(3)
        System.out().println(s.size())
        System.out().println(s.peek())
        System.out().println(s.pop())
        System.out().println(s.pop())
        System.out().println(s.isEmpty())
        return 0
    }
}
