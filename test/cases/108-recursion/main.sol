package recursion

class Rec {

    public static fib(n: Long): Long {
        if n < 2 {
            return n
        }
        return Rec.fib(n - 1) + Rec.fib(n - 2)
    }

    public static isEven(n: Long): Boolean {
        if n == 0 {
            return true
        }
        if n == 1 {
            return false
        }
        return Rec.isEven(n - 2)
    }
}

class Mut {

    public static even(n: Long): Boolean {
        if n == 0 {
            return true
        }
        return Mut.odd(n - 1)
    }

    public static odd(n: Long): Boolean {
        if n == 0 {
            return false
        }
        return Mut.even(n - 1)
    }
}

class Main {

    public static run(args: String...): Long {
        System.out().println(Rec.fib(20))
        System.out().println(Rec.isEven(10))
        System.out().println(Rec.isEven(7))
        System.out().println(Mut.even(4))
        System.out().println(Mut.odd(5))
        return 0
    }
}
