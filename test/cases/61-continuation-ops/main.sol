package cont

class Main {
    pub static run(args: String...): Int {
        // Trailing-operator continuation.
        a: Bool = true
        b: Bool = false
        r1: Bool = a &&
            !b
        stdout.println(r1)
        r2: Bool = a ||
            b
        stdout.println(r2)
        n: Int? = null
        r3: Int = n ??
            10
        stdout.println(r3)
        // Operator-first continuation.
        r4: Int = 1
            + 2
        stdout.println(r4)
        // Match arm bodies on the next line.
        x: Int = 1
        r5: Int = match x {
            1 =>
                100
            _ =>
                200
        }
        stdout.println(r5)
        return 0
    }
}
