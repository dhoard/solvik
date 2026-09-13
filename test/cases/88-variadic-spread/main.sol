package variadicspread

struct Main {

    public func count(values: Long...): Long {
        return values.size()
    }

    public func run(args: String...): Long {
        let values: List<Long> = [1, 2]
        let n: Long = Main.count(...values)
        System.getOut().println(values.size())
        System.getOut().println(n)
        return 0
    }
}
