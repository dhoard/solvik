package bench

struct Fib {

    public func fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Fib.fib(n - 1) + Fib.fib(n - 2)
    }
}

struct Main {

    public func run(args: String...): Long {
        System.getOut().println(Fib.fib(32))
        return 0
    }
}
