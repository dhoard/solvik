package scope
struct Main {
    public func run(args: String...): Integer {
        let x: Long = 1
        let l: List<Long> = [1, 2]
        for i in l {}
        System.getOut().println(x)
        return 0
    }
}
