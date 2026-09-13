package recursion

struct Rec {

    public func fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Rec.fib(n - 1) + Rec.fib(n - 2)
    }

    public func isEven(n: Long): Boolean {
        if n == 0 {
            return true
        }
        if n == 1 {
            return false
        }
        return Rec.isEven(n - 2)
    }
}

struct Mut {

    public func even(n: Long): Boolean {
        if n == 0 {
            return true
        }
        return Mut.odd(n - 1)
    }

    public func odd(n: Long): Boolean {
        if n == 0 {
            return false
        }
        return Mut.even(n - 1)
    }
}

struct Main {

    public func run(args: String...): Long {
        System.getOut().println(Rec.fib(20))
        System.getOut().println(Rec.isEven(10))
        System.getOut().println(Rec.isEven(7))
        System.getOut().println(Mut.even(4))
        System.getOut().println(Mut.odd(5))
        return 0
    }
}
