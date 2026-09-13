package badstatic

struct Vault {

    static mutable secret: Long = 42

    public static func new(): Self {
        return Self {}
    }
}

struct Main {

    public static func run(args: String...): Long {
        // ERROR: C162 - static fields are private to their declaring struct.
        return Vault.secret
    }
}
