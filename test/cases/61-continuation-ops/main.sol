package cont

struct Main {

    pub func run(args: String...): Integer {
        // Trailing-operator continuation.
        let a: Boolean = true
        let b: Boolean = false
        let r1: Boolean = a &&
        !b
        System.getOut().println(r1)
        let r2: Boolean = a ||
        b
        System.getOut().println(r2)
        let n: Long? = null
        let r3: Long = n ??
        10
        System.getOut().println(r3)
        // Operator-first continuation.
        let r4: Long = 1
        + 2
        System.getOut().println(r4)
        // Match arm bodies on the next line.
        let x: Long = 1
        let r5: Long = match x {
            1 =>
            100
            _ =>
            200
        }
        System.getOut().println(r5)
        return 0
    }
}
