package regression
struct Main {

    pub func run(args: String...): Integer {
        System.getErr().print("a")
        System.getErr().println("b")
        System.getErr().redirect(System.getOut())
        System.getErr().print("c")
        System.getErr().println("d")
        return 0
    }
}
