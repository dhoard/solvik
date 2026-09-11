package tailreturn

// Regression: `if/else` chains where every branch returns used to leave a
// dead merge jump after the then-branch, which the bytecode verifier
// rejected as unreachable code even though the program was valid.

class Foo {

    public static branch(x: Bool): Long {
        if (x) { return 1 } else { return 2 }
    }

    public static chain(x: Long): Long {
        if (x == 0) {
            return 10
        } else if (x == 1) {
            return 11
        } else {
            return 12
        }
    }

    public static condReturn(x: Bool): Long {
        if (x) {
            return 20
        }
        return 21
    }
}

class Main {

    public static run(args: String...): Long {
        stdout.println(Foo.branch(false))
        stdout.println(Foo.branch(true))
        stdout.println(Foo.chain(0))
        stdout.println(Foo.chain(1))
        stdout.println(Foo.chain(9))
        stdout.println(Foo.condReturn(false))
        stdout.println(Foo.condReturn(true))
        return 0
    }
}
