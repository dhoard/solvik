package constfold

struct Main {

    pub func sum(): Long {
        return 2 + 3 * 4
    }

    pub func neg(): Long {
        return -(5 * 2)
    }

    pub func cmp(): Boolean {
        return 10 > 3 && "ab" < "ac"
    }

    pub func run(args: String...): Integer {
        System.getOut().println(Main.sum())
        System.getOut().println(Main.neg())
        System.getOut().println(Main.cmp())
        System.getOut().println(1.5 + 2.25)
        System.getOut().println("x" == "x")
        return 0
    }
}
