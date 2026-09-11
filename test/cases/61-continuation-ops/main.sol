package cont

class Main {

    public static run(args: String...): Long {
        // Trailing-operator continuation.
        let a: Bool = true
        let b: Bool = false
        let r1: Bool = a &&
        !b
        System.out().println(r1)
        let r2: Bool = a ||
        b
        System.out().println(r2)
        let n: Long? = null
        let r3: Long = n ??
        10
        System.out().println(r3)
        // Operator-first continuation.
        let r4: Long = 1
        + 2
        System.out().println(r4)
        // Match arm bodies on the next line.
        let x: Long = 1
        let r5: Long = match x {
            1 =>
            100
            _ =>
            200
        }
        System.out().println(r5)
        return 0
    }
}
