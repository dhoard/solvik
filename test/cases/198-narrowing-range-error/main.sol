package narrowrange

struct Main {
    public static func run(args: String...): Long {
        // Explicit narrowing conversions are range-checked at runtime.
        let b: Byte = Byte.from(300)
        return 0
    }
}
