package stacks

struct Main {

    public static func run(args: String...): Long {
        let s: Stack<Long> = Stack<Long>.new()
        System.getOut().println(s.isEmpty())
        s.push(1)
        s.push(2)
        s.push(3)
        System.getOut().println(s.size())
        System.getOut().println(s.peek())
        System.getOut().println(s.pop())
        System.getOut().println(s.pop())
        System.getOut().println(s.isEmpty())
        return 0
    }
}
