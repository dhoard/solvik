package narrowrange

struct Main {
    pub func run(args: String...): Integer {
        // Explicit narrowing conversions are range-checked at runtime.
        let b: Byte = Byte.from(300)
        return 0
    }
}
