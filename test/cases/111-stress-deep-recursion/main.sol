package stressdeep

struct Main {

    public static func acc(n: Long): Long {
        if n == 0 {
            return 0
        }
        return n % 7 + Main.acc(n - 1)
    }

    public static func run(args: String...): Long {
        System.getOut().println(Main.acc(20000))
        return 0
    }
}
