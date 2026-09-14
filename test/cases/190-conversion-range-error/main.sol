package convrange

struct Main {
    public func run(args: String...): Integer {
        // Arbitrary-precision values overflow fixed-width targets.
        let huge: BigInteger = 99999999999999999999
        let c: Long = Long.from(huge)
        return 0
    }
}
