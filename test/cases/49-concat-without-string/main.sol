package invalid
struct Main {

    public func run(args: String...): Long {
        System.getOut().println(true .. false)
        return 0
    }
}
