package badstatic

struct Vault {

    static var secret: Long = 42

    pub func new(): Self {
        return Self {}
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // ERROR: C246 - static fields are only accessible as Self.field
        // inside their declaring struct.
        return Vault.secret
    }
}
