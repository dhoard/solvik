package invalid
struct Main {

    public func run(args: String...): Integer {
        System.getOut().println(true .. false)
        return 0
    }
}
