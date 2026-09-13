package badstaticobj

struct Vault {

    static secret: Long = 42

    public static func new(): Self {
        return Self {}
    }
}

struct Main {

    public static func run(args: String...): Long {
        // ERROR: C234 - static fields are never reachable through an object
        // receiver; use Vault.secret instead.
        let v: Vault = Vault.new()
        return v.secret
    }
}
