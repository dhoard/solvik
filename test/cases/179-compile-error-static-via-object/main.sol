package badstaticobj

class Vault {

    static secret: Long = 42

    public static new(): Self {
        return Self {}
    }
}

class Main {

    public static run(args: String...): Long {
        // ERROR: C234 - static fields are never reachable through an object
        // receiver; use Vault.secret instead.
        let v: Vault = Vault.new()
        return v.secret
    }
}
