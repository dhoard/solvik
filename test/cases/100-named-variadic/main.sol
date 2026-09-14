package namedvariadic

struct Main {

    func count(first: Long, rest: Long...): Long {
        return first
    }

    pub func run(args: String...): Integer {
        System.getOut().println(Main.count(first: 7))
        return 0
    }
}
