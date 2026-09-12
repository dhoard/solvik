package bdnorm

class Main {
    public static run(args: String...): Long {
        // Equality is scale-insensitive (numeric value).
        let a: BigDecimal = 1.50bd
        let b: BigDecimal = 1.5bd
        System.out().println(a.equals(b))
        System.out().println(a.hashCode() == b.hashCode())
        // Large scale and negative values.
        let big: BigDecimal = 123456789.123456789bd
        let neg: BigDecimal = -0.000001bd
        System.out().println(big * neg)
        System.out().println(big > neg)
        // Deterministic division under the built-in context.
        System.out().println(1bd / 3bd)
        // Map lookup uses the same scale-insensitive equality.
        let m: Map<BigDecimal, Long> = { 1.50bd: 7 }
        System.out().println(m.get(1.5bd))
        return 0
    }
}
