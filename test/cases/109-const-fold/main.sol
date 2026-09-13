package constfold

struct Main {

    public static func sum(): Long {
        return 2 + 3 * 4
    }

    public static func neg(): Long {
        return -(5 * 2)
    }

    public static func cmp(): Boolean {
        return 10 > 3 && "ab" < "ac"
    }

    public static func run(args: String...): Long {
        System.getOut().println(Main.sum())
        System.getOut().println(Main.neg())
        System.getOut().println(Main.cmp())
        System.getOut().println(1.5 + 2.25)
        System.getOut().println("x" == "x")
        return 0
    }
}
