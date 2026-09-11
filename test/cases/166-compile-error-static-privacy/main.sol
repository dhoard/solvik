package badstatic

class Vault {

    static mutable secret: Long = 42

    public static new(): Self {
        return Self {}
    }
}

class Main {

    public static run(args: String...): Long {
        // ERROR: C162 - static fields are private to their declaring class.
        return Vault.secret
    }
}
