package floatdouble

struct Main {
    pub func run(args: String...): Integer {
        // Float precision path.
        let f: Float = 0.1f + 0.2f
        System.getOut().println(f)
        // Double precision path.
        let d: Double = 0.1 + 0.2
        System.getOut().println(d)
        // Float widens into Double.
        let w: Double = 1.5f
        System.getOut().println(w * 2.0)
        // Explicit narrowing Double -> Float.
        let n: Float = Float.from(3.75)
        System.getOut().println(n)
        // NaN and infinities.
        let nan: Double = Double.NaN
        System.getOut().println(nan == nan)
        let inf: Double = Double.POSITIVE_INFINITY
        System.getOut().println(inf > 1e300)
        let ninf: Double = Double.NEGATIVE_INFINITY
        System.getOut().println(ninf < -1e300)
        // Comparisons across widths promote numerically.
        System.getOut().println(1 < 1.5)
        System.getOut().println(2 == 2.0)
        return 0
    }
}
