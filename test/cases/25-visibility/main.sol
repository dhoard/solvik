module vis

// Fields are always private; behavior is exposed through methods.

class Account {

    secret: Long
    shield: Long
    balanceValue: Long

    public static new(): Self {
        return Self { secret: 1, shield: 2, balanceValue: 100, }
    }

    // private method (omitted visibility)
    internal(): Long {
        return self.secret
    }

    public getBalance(): Long {
        return self.balanceValue
    }
}

class Main {

    public static run(args: String...): Long {
        let a: Account = Account.new()
        // public method accessible
        if a.getBalance() != 100 { return 2 }
        stdout.println("ok")
        return 0
    }
}
