package tailreturn

// Regression: `if/else` chains where every branch returns used to leave a
// dead merge jump after the then-branch, which the bytecode verifier
// rejected as unreachable code even though the program was valid.

struct Foo {

    public static func branch(x: Boolean): Long {
        if (x) { return 1 } else { return 2 }
    }

    public static func chain(x: Long): Long {
        if (x == 0) {
            return 10
        } else if (x == 1) {
            return 11
        } else {
            return 12
        }
    }

    public static func condReturn(x: Boolean): Long {
        if (x) {
            return 20
        }
        return 21
    }
}

struct Main {

    public static func run(args: String...): Long {
        System.getOut().println(Foo.branch(false))
        System.getOut().println(Foo.branch(true))
        System.getOut().println(Foo.chain(0))
        System.getOut().println(Foo.chain(1))
        System.getOut().println(Foo.chain(9))
        System.getOut().println(Foo.condReturn(false))
        System.getOut().println(Foo.condReturn(true))
        return 0
    }
}
