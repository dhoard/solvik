package stressdeep

struct Main {

    public func acc(n: Long): Long {
        if n == 0 {
            return 0
        }
        return n % 7 + Main.acc(n - 1)
    }

    public func run(args: String...): Integer {
        System.getOut().println(Main.acc(20000))
        return 0
    }
}
