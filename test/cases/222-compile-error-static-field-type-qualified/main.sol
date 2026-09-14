package badstaticqual

struct Vault {

    static var secret: Long = 42
    static cache: Map<Long, Long> = {}

    pub func new(): Self {
        return Self {}
    }

    pub func read(): Long {
        // ERROR: C246 - struct-name qualification is rejected for static
        // fields; only Self.secret is accepted inside Vault.
        return Vault.secret
    }

    pub func write(v: Long) {
        // ERROR: C246 - the assignment form is rejected too.
        Vault.secret = v
    }

    pub func chained(k: Long): Long? {
        // ERROR: C246 - chains the parser leaves as member access get the
        // same diagnostic instead of an unknown-variable cascade.
        return Vault.cache.get(k)
    }
}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
