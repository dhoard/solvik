package dapos

class Main {
    public static run(args: String...): Long {
        // if/else: both branches assign.
        let a: Long
        if true {
            a = 1
        } else {
            a = 2
        }
        System.out().println(a)

        // Loop: assigned before the read on every iteration.
        let mutable b: Long
        for i in 1..3 {
            b = i
            System.out().println(b)
        }

        // try/catch/finally: every path assigns.
        let c: Long
        try {
            c = 10
        } catch (e: Exception) {
            c = 20
        } finally {
            System.out().println("done")
        }
        System.out().println(c)

        // finally runs on return too.
        let r: Long = Main.finish()
        System.out().println(r)
        return 0
    }

    public static finish(): Long {
        try {
            return 42
        } finally {
            System.out().println("bye")
        }
    }
}
