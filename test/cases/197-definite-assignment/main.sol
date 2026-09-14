package dapos

struct Main {
    pub func run(args: String...): Integer {
        // if/else: both branches assign.
        let a: Long
        if true {
            a = 1
        } else {
            a = 2
        }
        System.getOut().println(a)

        // Loop: assigned before the read on every iteration.
        var b: Long
        for i in 1..3 {
            b = i
            System.getOut().println(b)
        }

        // try/catch/finally: every path assigns.
        let c: Long
        try {
            c = 10
        } catch (e: Exception) {
            c = 20
        } finally {
            System.getOut().println("done")
        }
        System.getOut().println(c)

        // finally runs on return too.
        let r: Long = Main.finish()
        System.getOut().println(r)
        return 0
    }

    pub func finish(): Long {
        try {
            return 42
        } finally {
            System.getOut().println("bye")
        }
    }
}
