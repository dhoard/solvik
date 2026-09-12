package numlattice

class Main {
    public static run(args: String...): Long {
        let b: Byte = 127
        let s: Short = 300
        let i: Integer = 100000
        let l: Long = 5000000000
        let f: Float = 1.5f
        let d: Double = 2.25

        // Widening assignment (Integer literal into Long).
        let l2: Long = i
        System.out().println(l2)

        // Binary promotion: Integer + Long -> Long.
        System.out().println(i + l)
        // Byte + Short -> Integer.
        System.out().println(b + s)
        // Integer + Float -> Float.
        System.out().println(1 + f)
        // Long + Double -> Double.
        System.out().println(l + d)
        // Cross-kind numeric comparison promotes.
        if 2 == 2.0 { System.out().println("eq") }
        if 1 < 1.5 { System.out().println("lt") }
        // Checked integer arithmetic stays in the promoted width, then
        // widens into Long.
        let small: Integer = Integer.MAX_VALUE - 1
        let wide: Long = small + 1
        System.out().println(wide)
        return 0
    }
}
