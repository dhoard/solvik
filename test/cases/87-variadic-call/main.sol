package variadiccall

struct Main {

    public func count(values: Long...): Long {
        return values.size()
    }

    public func run(args: String...): Long {
        System.getOut().println(Main.count(1, 2, 3))
        return 0
    }
}
