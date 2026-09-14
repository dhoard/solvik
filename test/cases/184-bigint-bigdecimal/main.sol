package bigdec

struct Main {
    pub func run(args: String...): Integer {
        // Integer literals beyond i64 become BigInteger.
        let huge: BigInteger = 123456789012345678901234567890
        let other: BigInteger = 99999999999999999999
        System.getOut().println(huge * other)
        // BigDecimal literals are exact decimal text.
        let a: BigDecimal = 0.1bd
        let b: BigDecimal = 0.2bd
        System.getOut().println(a + b)
        System.getOut().println(a / b)
        // Explicit factories.
        System.getOut().println(BigInteger.from("42"))
        System.getOut().println(BigDecimal.from("1.5"))
        return 0
    }
}
