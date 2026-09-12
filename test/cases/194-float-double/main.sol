package floatdouble

class Main {
    public static run(args: String...): Long {
        // Float precision path.
        let f: Float = 0.1f + 0.2f
        System.out().println(f)
        // Double precision path.
        let d: Double = 0.1 + 0.2
        System.out().println(d)
        // Float widens into Double.
        let w: Double = 1.5f
        System.out().println(w * 2.0)
        // Explicit narrowing Double -> Float.
        let n: Float = Float.from(3.75)
        System.out().println(n)
        // NaN and infinities.
        let nan: Double = Double.NaN
        System.out().println(nan == nan)
        let inf: Double = Double.POSITIVE_INFINITY
        System.out().println(inf > 1e300)
        let ninf: Double = Double.NEGATIVE_INFINITY
        System.out().println(ninf < -1e300)
        // Comparisons across widths promote numerically.
        System.out().println(1 < 1.5)
        System.out().println(2 == 2.0)
        return 0
    }
}
