package badstaticqual

struct Vault {

    static mutable secret: Long = 42
    static cache: Map<Long, Long> = {}

    public func new(): Self {
        return Self {}
    }

    public func read(): Long {
        // ERROR: C246 - struct-name qualification is rejected for static
        // fields; only Self.secret is accepted inside Vault.
        return Vault.secret
    }

    public func write(v: Long): Void {
        // ERROR: C246 - the assignment form is rejected too.
        Vault.secret = v
    }

    public func chained(k: Long): Long? {
        // ERROR: C246 - chains the parser leaves as member access get the
        // same diagnostic instead of an unknown-variable cascade.
        return Vault.cache.get(k)
    }
}

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
