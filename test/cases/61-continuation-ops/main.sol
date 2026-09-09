module cont

class Main {

    public static run(args: String...): Long {
        // Trailing-operator continuation.
        a: Bool = true
        b: Bool = false
        r1: Bool = a &&
        !b
        stdout.println(r1)
        r2: Bool = a ||
        b
        stdout.println(r2)
        n: Long? = null
        r3: Long = n ??
        10
        stdout.println(r3)
        // Operator-first continuation.
        r4: Long = 1
        + 2
        stdout.println(r4)
        // Match arm bodies on the next line.
        x: Long = 1
        r5: Long = match x {
            1 =>
            100
            _ =>
            200
        }
        stdout.println(r5)
        return 0
    }
}
