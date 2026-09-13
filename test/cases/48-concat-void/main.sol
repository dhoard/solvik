package invalid
struct Main {

    public func nothing(): Void {}
    public func run(args: String...): Long {
        System.getOut().println("void=" .. Main.nothing())
        return 0
    }
}
