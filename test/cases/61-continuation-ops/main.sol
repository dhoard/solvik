package cont

class Main {

    public static run(args: String...): Long {
        // Trailing-operator continuation.
        let a: Bool = true
        let b: Bool = false
        let r1: Bool = a &&
        !b
        stdout.println(r1)
        let r2: Bool = a ||
        b
        stdout.println(r2)
        let n: Long? = null
        let r3: Long = n ??
        10
        stdout.println(r3)
        // Operator-first continuation.
        let r4: Long = 1
        + 2
        stdout.println(r4)
        // Match arm bodies on the next line.
        let x: Long = 1
        let r5: Long = match x {
            1 =>
            100
            _ =>
            200
        }
        stdout.println(r5)
        return 0
    }
}
