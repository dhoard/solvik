package streams

struct Main {

    public func run(args: String...): Integer {
        System.getOut().println("out")
        System.getErr().println("err")
        System.getErr().redirect(System.getOut())
        System.getErr().println("redirected")
        return 0
    }
}
