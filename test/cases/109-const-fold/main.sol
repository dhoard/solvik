module constfold

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
        stdout.println(Main.sum())
        stdout.println(Main.neg())
        stdout.println(Main.cmp())
        stdout.println(1.5 + 2.25)
        stdout.println("x" == "x")
        return 0
    }
}
