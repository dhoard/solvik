package badruntime

struct Main {

    public func run(args: String...): Integer {
        let x: List<Long> = [1]
        System.getOut().println(x.get(5))
        return 0
    }
}
