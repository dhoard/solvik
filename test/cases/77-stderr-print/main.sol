package regression
struct Main {

    public func run(args: String...): Long {
        System.getErr().print("a")
        System.getErr().println("b")
        System.getErr().redirect(System.getOut())
        System.getErr().print("c")
        System.getErr().println("d")
        return 0
    }
}
