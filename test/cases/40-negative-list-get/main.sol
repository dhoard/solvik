package regression

struct Main {

    public func run(args: String...): Long {
        let x: List<Long> = [7]
        System.getOut().println(x.get(-1))
        return 0
    }
}
