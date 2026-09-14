package scope
struct Main {
    public func run(args: String...): Integer {
        while false {
            let inner: Long = 5
        }
        System.getOut().println(inner)
        return 0
    }
}
