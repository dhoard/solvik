package namedvariadic

struct Main {

    func count(first: Long, rest: Long...): Long {
        return first
    }

    public func run(args: String...): Long {
        System.getOut().println(Main.count(first: 7))
        return 0
    }
}
