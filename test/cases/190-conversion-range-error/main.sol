package convrange

struct Main {
    public static func run(args: String...): Long {
        // Arbitrary-precision values overflow fixed-width targets.
        let huge: BigInteger = 99999999999999999999
        let c: Long = Long.from(huge)
        return 0
    }
}
