package bigdec

class Main {
    public static run(args: String...): Long {
        // Integer literals beyond i64 become BigInteger.
        let huge: BigInteger = 123456789012345678901234567890
        let other: BigInteger = 99999999999999999999
        System.out().println(huge * other)
        // BigDecimal literals are exact decimal text.
        let a: BigDecimal = 0.1bd
        let b: BigDecimal = 0.2bd
        System.out().println(a + b)
        System.out().println(a / b)
        // Explicit factories.
        System.out().println(BigInteger.from("42"))
        System.out().println(BigDecimal.from("1.5"))
        return 0
    }
}
