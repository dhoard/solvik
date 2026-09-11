package constfold

class Main {

    public static sum(): Long {
        return 2 + 3 * 4
    }

    public static neg(): Long {
        return -(5 * 2)
    }

    public static cmp(): Bool {
        return 10 > 3 && "ab" < "ac"
    }

    public static run(args: String...): Long {
        System.out().println(Main.sum())
        System.out().println(Main.neg())
        System.out().println(Main.cmp())
        System.out().println(1.5 + 2.25)
        System.out().println("x" == "x")
        return 0
    }
}
