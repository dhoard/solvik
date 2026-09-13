package badstatic

struct Vault {

    static mutable secret: Long = 42

    public func new(): Self {
        return Self {}
    }
}

struct Main {

    public func run(args: String...): Long {
        // ERROR: C162 - static fields are private to their declaring struct.
        return Vault.secret
    }
}
