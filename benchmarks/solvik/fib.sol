package bench

struct Fib {

    pub func fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Fib.fib(n - 1) + Fib.fib(n - 2)
    }
}

struct Main {

    pub func run(args: String...): Integer {
        System.getOut().println(Fib.fib(32))
        return 0
    }
}
