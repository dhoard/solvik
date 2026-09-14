package regression

struct Main {

    public func run(args: String...): Integer {
        let x: List<Long> = [7]
        x.set(-1, 9)
        System.getOut().println(x.get(0))
        return 0
    }
}
