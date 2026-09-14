package narrowrange

struct Main {
    public func run(args: String...): Integer {
        // Explicit narrowing conversions are range-checked at runtime.
        let b: Byte = Byte.from(300)
        return 0
    }
}
