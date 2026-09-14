package constfold

struct Main {

    public func sum(): Long {
        return 2 + 3 * 4
    }

    public func neg(): Long {
        return -(5 * 2)
    }

    public func cmp(): Boolean {
        return 10 > 3 && "ab" < "ac"
    }

    public func run(args: String...): Integer {
        System.getOut().println(Main.sum())
        System.getOut().println(Main.neg())
        System.getOut().println(Main.cmp())
        System.getOut().println(1.5 + 2.25)
        System.getOut().println("x" == "x")
        return 0
    }
}
