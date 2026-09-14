package badstatic

struct Vault {

    static mutable secret: Long = 42

    public func new(): Self {
        return Self {}
    }
}

struct Main {

    public func run(args: String...): Integer {
        // ERROR: C246 - static fields are only accessible as Self.field
        // inside their declaring struct.
        return Vault.secret
    }
}
