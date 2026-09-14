package badstaticobj

struct Vault {

    static secret: Long = 42

    pub func new(): Self {
        return Self {}
    }
}

struct Main {

    pub func run(args: String...): Integer {
        // ERROR: C234 - static fields are never reachable through an object
        // receiver; use Self.secret inside Vault instead.
        let v: Vault = Vault.new()
        return v.secret
    }
}
