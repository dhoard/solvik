package bdnorm

struct Main {
    public func run(args: String...): Integer {
        // Equality is scale-insensitive (numeric value).
        let a: BigDecimal = 1.50bd
        let b: BigDecimal = 1.5bd
        System.getOut().println(a.equals(b))
        System.getOut().println(a.hashCode() == b.hashCode())
        // Large scale and negative values.
        let big: BigDecimal = 123456789.123456789bd
        let neg: BigDecimal = -0.000001bd
        System.getOut().println(big * neg)
        System.getOut().println(big > neg)
        // Deterministic division under the built-in context.
        System.getOut().println(1bd / 3bd)
        // Map lookup uses the same scale-insensitive equality.
        let m: Map<BigDecimal, Long> = { 1.50bd: 7 }
        System.getOut().println(m.get(1.5bd))
        return 0
    }
}
